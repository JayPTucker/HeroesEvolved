package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.hazard.BodyBurn;
import com.jayptucker.heroesevolved.hazard.Suits;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.network.SpeedCyclonePayload;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A speedster runs rings round a radioactive player, faster than any Cyclone, and drags the heat out of them on the
 * wind. Pressing the Cyclone key with a hot radioactive player within {@link #REACH} blocks starts it instead of a
 * Cyclone.
 * <ul>
 *   <li><b>Cooling.</b> While they are only hot, every tick of running carries Core out of them, until they are cool.
 *   Let go of the key and you stop.</li>
 *   <li><b>Containment.</b> If they are about to blow (containment failing, or the blast already charging), the run
 *   holds it back: neither the warning nor the charge runs down while you run, and after {@link #DEFUSE_TICKS} ticks
 *   (five seconds, building all the while) it is defused. Once you are containing a blast you cannot stop until it is done.</li>
 *   <li><b>The cost.</b> The heat you carry is paid for in stamina, more of it below tier 5. When the stamina runs out
 *   you overexert, and the heat burns you instead. If it would kill you, you burn away to nothing on the ring, but you
 *   take the rest of the heat with you: the blast is stopped.</li>
 * </ul>
 */
public final class SpeedContainment {
    /** How far away a hot player can be to be run round, and how wide the ring is. */
    /** How far away a hot player can be to be run round; how wide the ring is while cooling, and while holding a blast in. */
    public static final double REACH = 10, RING = 4.5, CONTAIN_RING = 6.5;
    /**
     * Ticks a blast must be held to be defused (ten seconds, building all the way to the moment it breaks), and the
     * longest a cooling run lasts.
     */
    public static final int DEFUSE_TICKS = 200, COOL_MAX_TICKS = 200;
    /** A beaten runner keeps running while they burn away; from the beaten moment until they are gone. */
    public static final int DEATH_TICKS = 100;
    /**
     * Holding a blast in without the whole velocity suit: the friction and the radiation go straight through the runner,
     * who burns up on the ring for this many ticks (three and a half seconds, harder and harder) and then burns away,
     * long before the blast is held long enough to stop it, and so it goes on.
     */
    public static final int UNSUITED_TICKS = 70;
    /** The share of the heat a suited runner still carries. */
    static final float SUITED_HEAT = 0.6F;
    /** Core a cooling run drains a tick; the Core a defused blast settles at. */
    static final int COOL_PER_TICK = 1, DEFUSED_CORE = 30, COOL_FLOOR = 10;
    /**
     * Heat carried a tick while containing (on average) and while cooling. A held blast is 120 units of heat in all:
     * 0.6 a tick on average over ten seconds, but back-loaded, from 0.24 at the start to 0.96 at the end
     * ({@link #containHeat}), so the strain climbs to the moment it breaks.
     */
    static final float CONTAIN_HEAT = 0.6F, COOL_HEAT = 0.5F;

    /** Heat carried on a tick {@code k} of the way through a hold (0 to 1); it averages {@link #CONTAIN_HEAT}. */
    static float containHeat(float k) { return CONTAIN_HEAT * (0.4F + 1.2F * Math.min(1, k)); }
    public static final ResourceKey<DamageType> DISINTEGRATED = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "disintegrated"));

    private static final class Run {
        final ServerPlayer runner;
        /** A radioactive player, or a villager the Eclipse woke a power in (always about to blow). */
        final LivingEntity target;
        final int tier;
        final long start;
        long containedFrom = -1;
        /** When the heat beat them: they stop, stand, burn away, and die at {@link #DEATH_TICKS} after it. */
        long failedAt = -1;
        /**
         * A suited runner whose body has given out mid-hold: nothing can hurt them now, they pay no more, and they run
         * on, burning, until the hold is done; then they burn away and take the blast with them.
         */
        boolean doomed;
        /**
         * Where the ring was when the heat beat them. They burn away there: if whoever they were holding is carried off
         * (or blows up) meanwhile, the burning body stays where it broke and doesn't follow.
         */
        Vec3 pinned;
        float debt;
        Run(ServerPlayer runner, LivingEntity target, int tier, long start) {
            this.runner = runner; this.target = target; this.tier = tier; this.start = start;
        }
    }
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    private SpeedContainment() {}

    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    public static boolean running(ServerPlayer runner) { return RUNS.containsKey(runner.getUUID()); }

    public static boolean containing(ServerPlayer runner) {
        Run r = RUNS.get(runner.getUUID());
        return r != null && r.containedFrom >= 0;
    }

    /** Stamina each unit of heat costs, and the health it burns once the stamina is gone: far less at tier 5. */
    public static float staminaPerHeat(int tier) { return tier >= 5 ? 1.0F : 1.8F; }
    public static float healthPerHeat(int tier) { return tier >= 5 ? 0.15F : 0.4F; }

    /**
     * The nearest radioactive player in reach running hot (Core 40+) or about to blow, or a villager in meltdown, if
     * any.
     */
    static LivingEntity hotNeighbour(ServerPlayer runner) {
        return runner.serverLevel().getEntitiesOfClass(LivingEntity.class, runner.getBoundingBox().inflate(REACH),
                        e -> e != runner && e.isAlive() && !e.isSpectator()
                                && (e instanceof ServerPlayer p ? radioactive(p)
                                        && (RadiationService.critical(p) || RadiationService.mass(p) >= RadiationService.HOT)
                                        : VillagerMeltdown.critical(e))
                                && e.distanceToSqr(runner) <= REACH * REACH && runner.hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(runner::distanceToSqr)).orElse(null);
    }

    // What the run asks of the one it runs round: a player's radiation, or a villager's meltdown.
    private static boolean critical(LivingEntity t) {
        return t instanceof ServerPlayer p ? RadiationService.critical(p) : VillagerMeltdown.critical(t);
    }
    private static void hold(LivingEntity t) {
        if (t instanceof ServerPlayer p) RadiationService.hold(p); else VillagerMeltdown.hold(t);
    }
    private static void defuse(LivingEntity t, int core, ServerPlayer runner) {
        if (t instanceof ServerPlayer p) RadiationService.defuse(p, core); else VillagerMeltdown.defuse(t, runner);
    }

    private static boolean radioactive(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.RADIATION_ID) && e.getValue().isUnlocked()).isPresent();
    }

    /** The Cyclone key: run round a hot radioactive player if one is in reach. False if there is none. */
    public static boolean start(ServerPlayer runner, int tier) {
        if (running(runner) || SpeedKit.cycloning(runner)) return false;
        LivingEntity target = hotNeighbour(runner);
        if (target == null || RUNS.values().stream().anyMatch(r -> r.target == target)) return false;
        Run r = new Run(runner, target, tier, now(runner));
        RUNS.put(runner.getUUID(), r);
        ServerLevel level = runner.serverLevel();
        level.playSound(null, target.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2F, 0.9F);
        runner.displayClientMessage(Component.literal(critical(target)
                ? Suits.frictionProof(runner) ? "You run rings round " + target.getName().getString() + " to hold it in."
                        : "No suit! Running rings round " + target.getName().getString() + " now will burn you away."
                : "You run rings round " + target.getName().getString() + " to draw the heat off.")
                .withStyle(critical(target) && !Suits.frictionProof(runner) ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        if (target instanceof ServerPlayer held) held.displayClientMessage(Component.literal(runner.getName().getString()
                + " is running the heat out of you.").withStyle(ChatFormatting.AQUA), true);
        sync(r, false);
        return true;
    }

    /** The key came up: a cooling run stops; a blast being contained must be seen through. */
    public static void release(ServerPlayer runner) {
        Run r = RUNS.get(runner.getUUID());
        if (r != null && r.containedFrom < 0 && r.failedAt < 0) end(r, false);
    }

    static void tick(long now) {
        for (Run r : List.copyOf(RUNS.values())) {
            ServerPlayer p = r.runner;
            LivingEntity t = r.target;
            if (r.failedAt >= 0) { burning(r); continue; }
            if (p.isRemoved() || !p.isAlive() || !PowerRules.canAct(p) || t.isRemoved() || !t.isAlive()
                    || t.level() != p.level() || t.distanceToSqr(p) > REACH * REACH * 4) {
                end(r, false);
                continue;
            }
            long age = now(p) - r.start;
            // You are out on the ring: your body stays where it is, and nobody sees it.
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 6, false, false, false));
            p.fallDistance = 0;
            boolean suited = Suits.frictionProof(p);
            if (critical(t) && r.containedFrom < 0) {
                r.containedFrom = now(p);
                p.displayClientMessage(Component.literal(suited ? "It's going to blow. Hold it in!"
                        : "It's going to blow, and you have no suit. The heat goes straight through you!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                sync(r, false);
            }
            if (r.containedFrom >= 0) {
                hold(t);
                long held = now(p) - r.containedFrom;
                if (!suited) {
                    // Friction and radiation with nothing between them and the runner: they burn up on the ring, harder
                    // and harder, and then they're gone.
                    float burnt = held / (float) UNSUITED_TICKS;
                    BodyBurn.atLeast(p, burnt);
                    if (held == UNSUITED_TICKS / 2)
                        p.displayClientMessage(Component.literal("You're burning up!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                    if (age % 3 == 0) CameraShakePayload.sendAround(p.serverLevel(), t.position(), 18, 0.4F + 1.6F * burnt, 5);
                    if (age % 6 == 0) p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1 + burnt, 0.6F + 0.8F * burnt);
                    if (held >= UNSUITED_TICKS) { disintegrate(r, false); continue; }
                }
                float k = Math.min(1, held / (float) DEFUSE_TICKS);
                if (r.doomed) BodyBurn.atLeast(p, 0.35F + 0.65F * k);
                else if (!strain(r, containHeat(k) * (suited ? SUITED_HEAT : 1))) continue;
                if (held >= DEFUSE_TICKS) {
                    // The heat goes out of them all at once: a cold shock rolls out over the ground.
                    SetPiecePayload.send(p.serverLevel(), Piece.RAD_EMP, t.position(), t.position().add(0, 1, 0), (float) (CONTAIN_RING + 3), null);
                    p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
                    p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.8F, 0.6F);
                    CameraShakePayload.sendAround(p.serverLevel(), t.position(), 32, 2.0F, 14);
                    if (r.doomed) { disintegrate(r, true); continue; }
                    defuse(t, DEFUSED_CORE, p);
                    p.displayClientMessage(Component.literal("You held it. The blast is gone.").withStyle(ChatFormatting.GREEN), true);
                    end(r, true);
                    continue;
                }
                // It builds all the way: the ground shakes harder, the wind and the hum climb, and in the last quarter
                // everything comes faster.
                boolean climax = k > 0.75F;
                if (age % (climax ? 2 : 4) == 0) CameraShakePayload.sendAround(p.serverLevel(), t.position(), 18 + 14 * k, 0.3F + 1.5F * k * k, 6);
                if (age % (climax ? 3 : 5) == 0) p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2F + k, 0.6F + 1.2F * k);
                if (climax && age % 8 == 0) p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F + k, 1.0F + k);
                if (held == DEFUSE_TICKS / 2 && !r.doomed)
                    p.displayClientMessage(Component.literal("Halfway. It's fighting you. Hold on!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            } else {
                // Only a player is ever merely hot; a villager whose meltdown was stopped has nothing left to cool.
                if (!(t instanceof ServerPlayer hot)) { end(r, true); continue; }
                RadiationService.vent(hot, COOL_PER_TICK);
                if (!strain(r, COOL_HEAT)) continue;
                if (RadiationService.mass(hot) <= COOL_FLOOR || age >= COOL_MAX_TICKS) {
                    p.displayClientMessage(Component.literal("They run cool again.").withStyle(ChatFormatting.GREEN), true);
                    end(r, true);
                    continue;
                }
            }
            if (age % 10 == 0) sync(r, false);
            if (age % 6 == 0) p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1.1F, 1.8F);
        }
    }

    /**
     * Carry {@code heat} for a tick: stamina first; once it is gone the heat burns. Returns false if the run ended
     * because it killed the runner.
     */
    private static boolean strain(Run r, float heat) {
        ServerPlayer p = r.runner;
        r.debt += heat * staminaPerHeat(r.tier);
        int owed = (int) r.debt;
        if (owed > 0) {
            r.debt -= owed;
            int have = PlayerEnergyService.getEnergy(p);
            int paid = Math.min(have, owed);
            if (paid > 0) PlayerEnergyService.tryConsume(p, paid);
            int unpaid = owed - paid;
            if (unpaid > 0) {
                float burn = unpaid / staminaPerHeat(r.tier) * healthPerHeat(r.tier);
                if (p.getHealth() + p.getAbsorptionAmount() - burn <= 0) {
                    if (r.containedFrom < 0) { disintegrate(r, true); return false; }
                    // Mid-hold: their body has given out, but they run on until it's done.
                    r.doomed = true;
                    p.setInvulnerable(true);
                    p.setHealth(Math.max(1, Math.min(p.getHealth(), 1)));
                    p.displayClientMessage(Component.literal("Your body is giving out. Don't let go. Finish it!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                    return true;
                }
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().source(DISINTEGRATED), burn);
            }
        }
        return true;
    }

    /**
     * The heat has beaten them. A suited runner whose body gave out takes it with them: the blast is stopped now. A
     * runner with no suit never had a chance: they burn away and the blast goes on. On the ring the runner keeps running
     * while they burn away, slowing as they go, and only when the last of them is gone do they die. Nothing else can
     * hurt them meanwhile.
     */
    private static void disintegrate(Run r, boolean saves) {
        ServerPlayer p = r.runner;
        LivingEntity t = r.target;
        ServerLevel level = p.serverLevel();
        Vec3 ringCenter = center(r);
        r.failedAt = now(p);
        r.pinned = ringCenter;
        p.setInvulnerable(true);
        p.setHealth(Math.max(1, Math.min(p.getHealth(), 1)));
        BodyBurn.atLeast(p, 1);
        if (saves) {
            defuse(t, 0, p);
            if (t instanceof ServerPlayer saved) saved.sendSystemMessage(Component.literal(p.getName().getString() + " burned away holding your blast in.")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            for (ServerPlayer near : level.players())
                if (near.distanceToSqr(t) <= 64 * 64) near.sendSystemMessage(Component.literal(p.getName().getString()
                        + " burned away on the ring. Without a suit, the heat went straight through them.").withStyle(ChatFormatting.RED));
        }
        sync(r, false);
        // The burning starts once they have stopped and stood a moment; the client finds where they stopped on the
        // ring. If it can't, the burning falls back to the ring between the two of them, never the one they saved.
        Vec3 toward = p.position().subtract(ringCenter).multiply(1, 0, 1);
        toward = toward.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : toward.normalize();
        Vec3 center = ringCenter.add(toward.scale(ring(r)));
        SetPiecePayload.send(level, Piece.SPEED_DISINTEGRATE, center, center.add(new Vec3(-toward.z, 0, toward.x)), 1, p);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4F, 0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.6F, 0.6F);
    }

    /** A beaten runner still running the ring and burning away; they die when it is done. */
    private static void burning(Run r) {
        ServerPlayer p = r.runner;
        if (p.isRemoved()) { RUNS.remove(p.getUUID()); return; }
        long since = now(p) - r.failedAt;
        // Keep the ring alive on clients (it is where they stand), or they would drop it as over.
        if (since % 10 == 0) sync(r, false);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 6, false, false, false));
        p.fallDistance = 0;
        Vec3 at = r.pinned;
        if (since == 20) p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
        if (since % 12 == 0)
            p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.7F);
        if (since < DEATH_TICKS) return;
        end(r, false);
        p.setInvulnerable(false);
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().source(DISINTEGRATED), p.getHealth() + p.getAbsorptionAmount() + 1000);
    }

    /** Has this suited runner's body given out mid-hold, so they run on, burning, until it's done? */
    public static boolean doomed(ServerPlayer runner) {
        Run r = RUNS.get(runner.getUUID());
        return r != null && r.doomed && r.failedAt < 0;
    }

    /** Has the heat beaten this runner (they are standing on the ring, burning away)? */
    public static boolean burningAway(ServerPlayer runner) {
        Run r = RUNS.get(runner.getUUID());
        return r != null && r.failedAt >= 0;
    }

    private static void end(Run r, boolean done) {
        RUNS.remove(r.runner.getUUID());
        r.runner.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sync(r, true);
    }

    /**
     * The ring's middle: round the target's feet (on the ground under them while they hang in the air charging), or,
     * once the heat has beaten the runner, where it was at that moment.
     */
    private static Vec3 center(Run r) {
        if (r.pinned != null) return r.pinned;
        LivingEntity t = r.target;
        Vec3 from = t.position();
        var hit = t.level().clip(new ClipContext(from, from.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, t));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : from;
    }

    /** Where this runner's ring is centred now (for tests), or null if they aren't running one. */
    public static Vec3 ringCenter(ServerPlayer runner) {
        Run r = RUNS.get(runner.getUUID());
        return r == null ? null : center(r);
    }

    private static void sync(Run r, boolean ended) {
        Vec3 center = center(r);
        int held = r.containedFrom < 0 || r.failedAt >= 0 ? -1 : (int) (now(r.runner) - r.containedFrom);
        SpeedCyclonePayload.send(r.runner.serverLevel(), r.runner, center, (float) ring(r), (int) (now(r.runner) - r.start), held, ended,
                false, r.failedAt >= 0 ? SpeedCyclonePayload.CONTAIN_FAIL : SpeedCyclonePayload.CONTAIN);
    }

    /** The ring widens to keep clear of a blast being held in. */
    private static double ring(Run r) { return r.containedFrom >= 0 ? CONTAIN_RING : RING; }

    static void forget(ServerPlayer p) {
        Run r = RUNS.get(p.getUUID());
        if (r != null) { end(r, false); if (r.failedAt >= 0) p.setInvulnerable(false); }
    }

    static void clear() { RUNS.clear(); }
}
