package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
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
    /** Ticks a blast must be held to be defused, and the longest a cooling run lasts. */
    public static final int DEFUSE_TICKS = 100, COOL_MAX_TICKS = 200;
    /** A beaten runner keeps running while they burn away; from the beaten moment until they are gone. */
    public static final int DEATH_TICKS = 100;
    /** Core a cooling run drains a tick; the Core a defused blast settles at. */
    static final int COOL_PER_TICK = 1, DEFUSED_CORE = 30, COOL_FLOOR = 10;
    /** Heat carried a tick while containing and while cooling. */
    // A held blast is 120 units of heat in all, however long it takes: 1.2 a tick over five seconds.
    static final float CONTAIN_HEAT = 1.2F, COOL_HEAT = 0.5F;
    public static final ResourceKey<DamageType> DISINTEGRATED = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "disintegrated"));

    private static final class Run {
        final ServerPlayer runner, target;
        final int tier;
        final long start;
        long containedFrom = -1;
        /** When the heat beat them: they stop, stand, burn away, and die at {@link #DEATH_TICKS} after it. */
        long failedAt = -1;
        float debt;
        Run(ServerPlayer runner, ServerPlayer target, int tier, long start) {
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

    /** The nearest radioactive player in reach running hot (Core 40+) or about to blow, if any. */
    static ServerPlayer hotNeighbour(ServerPlayer runner) {
        return runner.serverLevel().getEntitiesOfClass(ServerPlayer.class, runner.getBoundingBox().inflate(REACH),
                        p -> p != runner && p.isAlive() && !p.isSpectator() && radioactive(p)
                                && (RadiationService.critical(p) || RadiationService.mass(p) >= RadiationService.HOT)
                                && p.distanceToSqr(runner) <= REACH * REACH && runner.hasLineOfSight(p))
                .stream().min(Comparator.comparingDouble(runner::distanceToSqr)).orElse(null);
    }

    private static boolean radioactive(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.RADIATION_ID) && e.getValue().isUnlocked()).isPresent();
    }

    /** The Cyclone key: run round a hot radioactive player if one is in reach. False if there is none. */
    public static boolean start(ServerPlayer runner, int tier) {
        if (running(runner) || SpeedKit.cycloning(runner)) return false;
        ServerPlayer target = hotNeighbour(runner);
        if (target == null || RUNS.values().stream().anyMatch(r -> r.target == target)) return false;
        Run r = new Run(runner, target, tier, now(runner));
        RUNS.put(runner.getUUID(), r);
        ServerLevel level = runner.serverLevel();
        level.playSound(null, target.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2F, 0.9F);
        runner.displayClientMessage(Component.literal(RadiationService.critical(target)
                ? "You run rings round " + target.getName().getString() + " to hold it in."
                : "You run rings round " + target.getName().getString() + " to draw the heat off.").withStyle(ChatFormatting.GOLD), true);
        target.displayClientMessage(Component.literal(runner.getName().getString() + " is running the heat out of you.")
                .withStyle(ChatFormatting.AQUA), true);
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
            ServerPlayer p = r.runner, t = r.target;
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
            if (RadiationService.critical(t) && r.containedFrom < 0) {
                r.containedFrom = now(p);
                p.displayClientMessage(Component.literal("It's going to blow. Hold it in!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                sync(r, false);
            }
            if (r.containedFrom >= 0) {
                RadiationService.hold(t);
                if (!strain(r, CONTAIN_HEAT)) continue;
                long held = now(p) - r.containedFrom;
                if (held >= DEFUSE_TICKS) {
                    RadiationService.defuse(t, DEFUSED_CORE);
                    // The heat goes out of them all at once: a cold shock rolls out over the ground.
                    SetPiecePayload.send(p.serverLevel(), Piece.RAD_EMP, t.position(), t.position().add(0, 1, 0), (float) (CONTAIN_RING + 3), null);
                    p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
                    p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.8F, 0.6F);
                    CameraShakePayload.sendAround(p.serverLevel(), t.position(), 24, 1.4F, 10);
                    p.displayClientMessage(Component.literal("You held it. The blast is gone.").withStyle(ChatFormatting.GREEN), true);
                    end(r, true);
                    continue;
                }
                // It builds: the ground shakes harder and the wind climbs as the moment comes.
                float k = Math.min(1, (now(p) - r.containedFrom) / (float) DEFUSE_TICKS);
                if (age % 4 == 0) CameraShakePayload.sendAround(p.serverLevel(), t.position(), 18, 0.3F + 1.1F * k, 6);
                if (age % 5 == 0) p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2F + k, 0.6F + 1.2F * k);
            } else {
                RadiationService.vent(t, COOL_PER_TICK);
                if (!strain(r, COOL_HEAT)) continue;
                if (RadiationService.mass(t) <= COOL_FLOOR || age >= COOL_MAX_TICKS) {
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
                if (p.getHealth() + p.getAbsorptionAmount() - burn <= 0) { disintegrate(r); return false; }
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().source(DISINTEGRATED), burn);
            }
        }
        return true;
    }

    /** The heat was too much: the runner burns away on the ring, and takes the rest of the heat with them. */
    /**
     * The heat has beaten them, but they take it with them: the blast is stopped now. On the ring the runner keeps
     * running while they burn away, slowing as they go, and only when the last of them is gone do they die. Nothing
     * else can hurt them meanwhile.
     */
    private static void disintegrate(Run r) {
        ServerPlayer p = r.runner, t = r.target;
        ServerLevel level = p.serverLevel();
        r.failedAt = now(p);
        p.setInvulnerable(true);
        p.setHealth(Math.max(1, Math.min(p.getHealth(), 1)));
        RadiationService.defuse(t, 0);
        t.sendSystemMessage(Component.literal(p.getName().getString() + " burned away holding your blast in.")
                .withStyle(ChatFormatting.GOLD));
        sync(r, false);
        // The burning starts once they have stopped and stood a moment; the client finds where they stopped on the
        // ring. If it can't, the burning falls back to the ring between the two of them, never the one they saved.
        Vec3 toward = p.position().subtract(t.position()).multiply(1, 0, 1);
        toward = toward.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : toward.normalize();
        Vec3 center = t.position().add(toward.scale(ring(r)));
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
        Vec3 at = r.target.position();
        if (since == 20) p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
        if (since % 12 == 0)
            p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.7F);
        if (since < DEATH_TICKS) return;
        end(r, false);
        p.setInvulnerable(false);
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().source(DISINTEGRATED), p.getHealth() + p.getAbsorptionAmount() + 1000);
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

    /** The ring is drawn round the target's feet (on the ground under them while they hang in the air charging). */
    private static void sync(Run r, boolean ended) {
        ServerPlayer t = r.target;
        Vec3 from = t.position();
        var hit = t.level().clip(new ClipContext(from, from.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, t));
        Vec3 center = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : from;
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
