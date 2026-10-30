package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Superspeed's running, after Daphne Millbrook ("when I run, the world stops") with the Flash's
 * golden lightning. Native, synced movement attributes for running; bounded swept paths for bursts.
 *
 * <p>Running builds <b>Momentum</b> (0 to 100). At 50 the body crackles with lightning, at 60 you
 * buffet whatever you run past, and at 100 you break the sound barrier. Momentum powers Blitz and
 * Lightspeed (see {@link SpeedKit}) and the charged strike.</p>
 */
public final class SpeedService {
    /** Momentum at which the body crackles, enemies you pass are buffeted, and the sound barrier breaks. */
    public static final int AURA_MOMENTUM = 50, BUFFET_MOMENTUM = 60, BARRIER_MOMENTUM = 100;
    private static final Map<UUID, Long> BUFFETED = new HashMap<>();
    public static final ResourceLocation STRIDE = ResourceLocation.fromNamespaceAndPath("heroesevolved", "speed_stride");
    public static final ResourceLocation ACCELERATING = ResourceLocation.fromNamespaceAndPath("heroesevolved", "speed_accelerating");
    public static final ResourceLocation WATER_RUN = ResourceLocation.fromNamespaceAndPath("heroesevolved", "speed_water_run");
    private static final ResourceLocation STEP = ResourceLocation.fromNamespaceAndPath("heroesevolved", "speed_step");
    private static final Map<UUID, Runner> RUNNERS = new HashMap<>();
    private static final Map<UUID, Flurry> FLURRIES = new HashMap<>();
    private static final Map<UUID, Long> REFLEX = new HashMap<>(), DODGED = new HashMap<>();
    private static final class Runner {
        final ServerPlayer player;
        final Level level;
        Vec3 previous, trailPrevious;
        long lastMoving = Long.MIN_VALUE, redlineUntil;
        int activeTicks, wallTicks, momentum, sentMomentum = -1;
        boolean barrier;
        double modifier = -1, distanceBank;
        Runner(ServerPlayer p) { player = p; level = p.level(); previous = p.position(); trailPrevious = previous; }
    }
    private record Flurry(ServerPlayer player, LivingEntity target, int damage, long start) {}
    private SpeedService() {}
    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    public static boolean awakened(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getKey().equals(ModAbilities.SUPERSPEED_ID) && e.getValue().isUnlocked()).isPresent();
    }
    public static boolean active(ServerPlayer p) { return RUNNERS.containsKey(p.getUUID()); }
    public static boolean toggle(ServerPlayer p) {
        if (active(p)) { stop(p); return true; }
        RUNNERS.put(p.getUUID(), new Runner(p));
        syncMarker(p, ACCELERATING, true);
        p.displayClientMessage(Component.translatable("speed.heroesevolved.enabled"), true);
        cue(p); return true;
    }
    public static boolean stop(ServerPlayer p) {
        boolean active = RUNNERS.remove(p.getUUID()) != null;
        if (active) com.jayptucker.heroesevolved.network.MomentumPayload.send(p, 0);
        removeModifiers(p);
        syncMarker(p, ACCELERATING, false); syncMarker(p, WATER_RUN, false);
        return active;
    }
    public static boolean redline(ServerPlayer p) {
        Runner r = RUNNERS.computeIfAbsent(p.getUUID(), id -> new Runner(p));
        syncMarker(p, ACCELERATING, true);
        r.redlineUntil = now(p) + 240;
        r.momentum = 100;
        REFLEX.put(p.getUUID(), r.redlineUntil);
        AbilityEffects.at(p, Kind.SPEED_BURST, 5); cue(p); return true;
    }
    /** No call into PowerRules here: its time-stop check consults this method. */
    public static boolean outrunsTime(Entity e) {
        if (!(e instanceof ServerPlayer p)) return false;
        Runner r = RUNNERS.get(p.getUUID());
        return r != null && r.level == p.level() && p.isAlive() && !p.isPassenger() && !p.isSpectator()
                && p.isSprinting() && !p.isShiftKeyDown() && r.lastMoving != Long.MIN_VALUE
                && now(p) - r.lastMoving <= 2 && awakened(p) && !CryoService.isFrozen(p)
                && !EclipseService.arePowersSuppressed(p);
    }
    static boolean canHit(ServerPlayer p, LivingEntity t) {
        return t != p && t.isAlive() && !t.isSpectator() && !p.isAlliedTo(t)
                && !(t instanceof com.jayptucker.heroesevolved.entity.TemporalEchoEntity)
                && (!(t instanceof ServerPlayer other) || p.getServer().isPvpAllowed() && p.canHarmPlayer(other));
    }
    public static LivingEntity target(ServerPlayer p, double baseRange) { return Targeting.livingEntityInSight(p, baseRange, t -> canHit(p, t)); }
    public static boolean flurry(ServerPlayer p, int level) {
        LivingEntity t = target(p, 6);
        if (t == null || FLURRIES.containsKey(p.getUUID())) return false;
        if (p.distanceToSqr(t) > 9) {
            Vec3 delta = t.position().subtract(p.position());
            Vec3 approach = swept(p, delta.normalize().scale(Math.max(0, delta.length() - 1.7)));
            if (approach.distanceToSqr(t.position()) > 9) return false;
            move(p, approach);
        }
        FLURRIES.put(p.getUUID(), new Flurry(p, t, 2 + level / 2, now(p))); cue(p); return true;
    }
    /** Cyclone is the whirlwind now. */
    public static boolean hasWhirlwind(ServerPlayer p) { return SpeedKit.cycloning(p); }
    public static boolean dash(ServerPlayer p, int level) {
        Vec3 direction = horizontal(p.getLookAngle());
        Vec3 destination = swept(p, direction.scale(10 + level * 4));
        if (destination.distanceToSqr(p.position()) < 1) return false;
        move(p, destination); return true;
    }
    static Vec3 horizontal(Vec3 direction) { return new Vec3(direction.x, 0, direction.z).normalize(); }
    /** Quarter-block steps check the entire body's path, not just the final block. */
    public static Vec3 swept(Entity entity, Vec3 displacement) {
        double distance = Math.min(40, displacement.length());
        Vec3 direction = displacement.normalize(), start = entity.position(), last = start;
        int steps = (int) Math.ceil(distance * 4);
        for (int i = 1; i <= steps; i++) {
            Vec3 candidate = start.add(direction.scale(Math.min(distance, i * .25)));
            if (!safe(entity, candidate)) break;
            last = candidate;
        }
        return last;
    }
    public static boolean safe(Entity e, Vec3 point) {
        if (!(e.level() instanceof ServerLevel level) || !Double.isFinite(point.lengthSqr())) return false;
        var box = e.getBoundingBox().move(point.subtract(e.position()));
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (!level.hasChunkAt(pos) || level.getFluidState(pos).is(FluidTags.LAVA) || level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) return false;
        return level.noCollision(e, box);
    }
    static void move(Entity entity, Vec3 point) {
        Vec3 from = entity.position();
        if (entity instanceof ServerPlayer p) p.teleportTo(p.serverLevel(), point.x, point.y, point.z, p.getYRot(), p.getXRot());
        else entity.teleportTo(point.x, point.y, point.z);
        // Horizontal speed is arrested; a burst does not erase an existing lethal fall.
        entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0); entity.hurtMarked = true;
        if (entity instanceof ServerPlayer p) { p.connection.send(new ClientboundSetEntityMotionPacket(p)); cue(p); }
        AbilityEffects.send((ServerLevel) entity.level(), Kind.SPEED_TRAIL, from, point, 1.5);
        AbilityEffects.send((ServerLevel) entity.level(), Kind.SPEED_BURST, point, point.add(0, 1, 0), 1.5);
    }
    private static LivingEntity passenger(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.5), t -> t != p && t.isAlive()
                && !t.isPassenger() && !t.isSpectator() && !(t instanceof Enemy)
                && !(t instanceof com.jayptucker.heroesevolved.entity.TemporalEchoEntity)
                && !com.jayptucker.heroesevolved.time.TemporalEntityEchoService.isReplaying(t)
                && t.distanceToSqr(p) <= 2.25
                && (!(t instanceof ServerPlayer other) || p.isAlliedTo(other) || other.isShiftKeyDown()))
                .stream().min(Comparator.comparingDouble(t -> t.distanceToSqr(p))).orElse(null);
    }
    public static boolean canUseSkill(AbilityUseContext c, int branch) {
        if (c.player().isPassenger()) return false;
        return branch == 0 || (branch == 1 ? target(c.player(), 20) != null : passenger(c.player()) != null);
    }
    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        if (branch == 0) {
            REFLEX.put(p.getUUID(), now(p) + 120); AbilityEffects.at(p, Kind.SPEED_BURST, 2); cue(p); return true;
        }
        if (branch == 1) {
            LivingEntity t = target(p, 20); if (t == null) return false;
            Vec3 difference = t.position().subtract(p.position());
            Vec3 destination = swept(p, difference.scale(Math.max(0, difference.length() - 1.6) / Math.max(.01, difference.length())));
            if (destination.distanceToSqr(t.position()) > 9) return false;
            move(p, destination);
            if (canHit(p, t)) hit(p, t, 6 + c.abilityLevel());
            AbilityEffects.send(p.serverLevel(), Kind.SPEED_IMPACT, p.getEyePosition(), t.getEyePosition(), 2);
            return true;
        }
        LivingEntity carried = passenger(p); if (carried == null) return false;
        Vec3 direction = horizontal(p.getLookAngle()), origin = p.position(), destination = origin;
        // Preserve the passenger's offset, and sweep both bodies before moving either one.
        for (double distance = .25; distance <= 12; distance += .25) {
            Vec3 offset = direction.scale(distance);
            if (!safe(p, origin.add(offset)) || !safe(carried, carried.position().add(offset))) break;
            destination = origin.add(offset);
        }
        if (origin.distanceToSqr(destination) < 1) return false;
        Vec3 passengerDestination = carried.position().add(destination.subtract(origin));
        move(p, destination); move(carried, passengerDestination); return true;
    }
    public static boolean dodge(ServerPlayer p, DamageSource source) {
        if (!(source.getDirectEntity() instanceof Projectile) || !awakened(p) || !PowerRules.canAct(p)
                || p.isPassenger() || REFLEX.getOrDefault(p.getUUID(), 0L) <= now(p)
                || DODGED.getOrDefault(p.getUUID(), 0L) > now(p) || PlayerEnergyService.getEnergy(p) < 3) return false;
        Vec3 forward = horizontal(p.getLookAngle()), side = new Vec3(-forward.z, 0, forward.x);
        for (int sign : new int[]{1, -1}) {
            Vec3 destination = swept(p, side.scale(sign * 2));
            if (destination.distanceToSqr(p.position()) < 2.25) continue;
            if (!PlayerEnergyService.tryConsume(p, 3)) return false;
            move(p, destination); DODGED.put(p.getUUID(), now(p) + 20); return true;
        }
        return false;
    }
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Runner r : List.copyOf(RUNNERS.values())) tickRunner(r.player);
        for (Flurry f : List.copyOf(FLURRIES.values())) {
            long age = now - f.start;
            if (age >= 12 || !awakened(f.player) || !PowerRules.canAct(f.player) || !f.target.isAlive()
                    || f.target.level() != f.player.level() || !canHit(f.player, f.target)
                    || f.player.distanceToSqr(f.target) > 36 || !f.player.hasLineOfSight(f.target)) {
                FLURRIES.remove(f.player.getUUID()); continue;
            }
            if (age % 2 == 0) {
                f.target.invulnerableTime = 0;
                hit(f.player, f.target, f.damage * (f.target instanceof ServerPlayer ? .6F : 1));
                AbilityEffects.send(f.player.serverLevel(), Kind.SPEED_IMPACT, f.player.getEyePosition(), f.target.getEyePosition(), 1);
            }
        }
        SpeedKit.tick(server, now);
        BUFFETED.values().removeIf(until -> until <= now);
        REFLEX.values().removeIf(until -> until <= now); DODGED.values().removeIf(until -> until <= now);
    }
    public static void tickRunner(ServerPlayer p) {
        Runner r = RUNNERS.get(p.getUUID()); if (r == null) return;
        // Determine motion before asking time fields whether the runner can act.
        double distance = p.position().subtract(r.previous).horizontalDistanceSqr();
        if (distance > .01 && distance < 25 && p.isSprinting()) r.lastMoving = now(p);
        if (!awakened(p) || !PowerRules.canAct(p) || p.isPassenger() || p.level() != r.level || p.isFallFlying()) { stop(p); return; }
        int level = PlayerProgressionService.getLevel(p);
        boolean running = p.isSprinting() && !p.isShiftKeyDown();
        boolean redline = now(p) < r.redlineUntil;
        if (running && distance > .01 && distance < 64) r.momentum = Math.min(100, r.momentum + 3);
        else r.momentum = Math.max(0, r.momentum - 6);
        if (running && distance > .01 && distance < 64) runFeats(p, r, Math.sqrt(distance));
        syncMomentum(p, r);
        // Quantize the modifier to avoid sending attribute packets for every tiny charge change.
        double amount = running ? 2 + level + (r.momentum / 25) * .5 + (redline ? 3 : 0) : 0;
        if (r.modifier != amount) {
            removeModifiers(p);
            if (amount > 0) {
                p.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(new AttributeModifier(STRIDE, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                p.getAttribute(Attributes.STEP_HEIGHT).addTransientModifier(new AttributeModifier(STEP, .5, AttributeModifier.Operation.ADD_VALUE));
            }
            r.modifier = amount;
        }
        if (running && r.activeTicks++ % 20 == 0) {
            int cost = redline ? 6 : 3, before = PlayerEnergyService.getEnergy(p);
            if (!PlayerEnergyService.tryConsume(p, cost)) { stop(p); p.displayClientMessage(Component.translatable("speed.heroesevolved.exhausted"), true); return; }
            MasteryService.awardEnergySpent(p, Math.max(0, before - PlayerEnergyService.getEnergy(p)));
        }
        if (running && distance > .015 && distance < 25 && now(p) % 2 == 0) {
            if (r.trailPrevious.distanceToSqr(p.position()) < 100)
                AbilityEffects.send(p.serverLevel(), Kind.SPEED_TRAIL, r.trailPrevious, p.position(), redline ? 1.6 : 1);
            r.trailPrevious = p.position();
        } else if (!running || distance >= 25) r.trailPrevious = p.position();
        // Zero-value attribute markers synchronize eligibility without changing movement values.
        // Surface collision runs on both sides instead of repeatedly pushing the client upward.
        syncMarker(p, WATER_RUN, running && level >= 3);
        if (running && level >= 4 && p.horizontalCollision && r.wallTicks++ < 30 && p.fallDistance < 3) {
            p.setDeltaMovement(p.getDeltaMovement().x, .32, p.getDeltaMovement().z);
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        } else if (p.onGround() && !p.horizontalCollision) r.wallTicks = 0;
        r.previous = p.position();
    }
    /** What fast running does on its own: lightning on the body, buffeted bystanders, the sound barrier, the trial. */
    private static void runFeats(ServerPlayer p, Runner r, double step) {
        long now = now(p);
        ServerLevel level = p.serverLevel();
        if (r.momentum >= AURA_MOMENTUM && now % 3 == 0)
            AbilityEffects.send(level, Kind.SPEED_AURA, p.position(), p.position().add(p.getDeltaMovement()), r.momentum / 100F);
        if (r.momentum >= BUFFET_MOMENTUM) {
            Vec3 heading = horizontal(p.position().subtract(r.previous));
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.4), t -> canHit(p, t))) {
                if (BUFFETED.getOrDefault(t.getUUID(), 0L) > now) continue;
                BUFFETED.put(t.getUUID(), now + 20);
                Vec3 away = t.position().subtract(p.position());
                Vec3 side = new Vec3(-heading.z, 0, heading.x);
                if (side.dot(away) < 0) side = side.reverse();
                hit(p, t, t instanceof ServerPlayer ? 1 : 2);
                t.setDeltaMovement(side.scale(0.9).add(heading.scale(0.4)).add(0, 0.3, 0)); t.hurtMarked = true;
                if (t instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
                AbilityEffects.send(level, Kind.SPEED_IMPACT, p.getEyePosition(), t.getEyePosition(), 0.8);
            }
        }
        if (r.momentum >= BARRIER_MOMENTUM && !r.barrier) {
            r.barrier = true;
            Vec3 heading = horizontal(p.position().subtract(r.previous));
            AbilityEffects.send(level, Kind.SPEED_BOOM, p.position().add(0, 1, 0), p.position().add(0, 1, 0).add(heading), 1.5);
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
            level.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.8F, 0.6F);
        } else if (r.momentum < 70) r.barrier = false;
        // Distance toward the Evolved trial, banked so the trial is not touched every tick.
        r.distanceBank += step;
        if (r.distanceBank >= 20) {
            int banked = (int) r.distanceBank;
            r.distanceBank -= banked;
            com.jayptucker.heroesevolved.progression.EvolutionTrials.advance(p, ModAbilities.SUPERSPEED_ID, banked);
        }
    }

    private static void syncMomentum(ServerPlayer p, Runner r) {
        if (r.sentMomentum >= 0 && Math.abs(r.momentum - r.sentMomentum) < 4 && r.momentum != 0 && r.momentum != 100) return;
        if (r.momentum == r.sentMomentum) return;
        r.sentMomentum = r.momentum;
        com.jayptucker.heroesevolved.network.MomentumPayload.send(p, r.momentum);
    }

    public static int momentum(ServerPlayer p) { Runner r = RUNNERS.get(p.getUUID()); return r == null ? 0 : r.momentum; }
    /** Spends all Momentum if at least {@code minimum} is banked; returns the amount spent, or 0. */
    public static int spendMomentum(ServerPlayer p, int minimum) {
        Runner r = RUNNERS.get(p.getUUID());
        if (r == null || r.momentum < minimum) return 0;
        int spent = r.momentum;
        r.momentum = 0;
        return spent;
    }
    private static boolean powerHit;

    /** True while one of Superspeed's own abilities is dealing damage: the charged punch is for real punches only. */
    public static boolean inPowerHit() { return powerHit; }

    /** Damage dealt by a Superspeed ability (not a punch). */
    public static boolean hit(ServerPlayer p, LivingEntity target, float amount) {
        powerHit = true;
        try { return target.hurt(p.damageSources().playerAttack(p), amount); }
        finally { powerHit = false; }
    }

    /** Spends exactly {@code amount} Momentum if there is that much; true if it was spent. */
    public static boolean drainMomentum(ServerPlayer p, int amount) {
        Runner r = RUNNERS.get(p.getUUID());
        if (r == null || r.momentum < amount) return false;
        r.momentum -= amount;
        return true;
    }

    public static float consumeImpact(ServerPlayer p, boolean pvp) {
        Runner r = RUNNERS.get(p.getUUID());
        if (r == null || r.momentum < 50 || !PowerRules.canAct(p)) return 0;
        float damage = pvp ? 4 : 4 + r.momentum * .08F;
        r.momentum = 0;
        AbilityEffects.at(p, Kind.SPEED_BURST, 2);
        return damage;
    }
    private static void removeModifiers(ServerPlayer p) {
        p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(STRIDE);
        p.getAttribute(Attributes.STEP_HEIGHT).removeModifier(STEP);
    }
    private static void syncMarker(ServerPlayer p, ResourceLocation id, boolean enabled) {
        var attribute = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (enabled && !attribute.hasModifier(id))
            attribute.addTransientModifier(new AttributeModifier(id, 0, AttributeModifier.Operation.ADD_VALUE));
        else if (!enabled) attribute.removeModifier(id);
    }
    public static void forget(ServerPlayer p) {
        stop(p); FLURRIES.remove(p.getUUID()); REFLEX.remove(p.getUUID()); DODGED.remove(p.getUUID()); SpeedKit.forget(p);
        com.jayptucker.heroesevolved.network.MomentumPayload.send(p, 0);
    }
    public static void clear() { for (Runner r : List.copyOf(RUNNERS.values())) stop(r.player); FLURRIES.clear(); REFLEX.clear(); DODGED.clear(); BUFFETED.clear(); SpeedKit.clear(); }
    private static void cue(ServerPlayer p) { p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .7F, 1.4F); }
}
