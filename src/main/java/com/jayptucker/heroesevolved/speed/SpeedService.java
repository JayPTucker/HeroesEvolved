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
import net.minecraft.core.particles.ParticleTypes;
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
    /** Momentum at which you vibrate through walls you run into, and the thickest wall you can pass. */
    public static final int VIBRATE_MOMENTUM = 80;
    public static final double VIBRATE_WALL = 3;
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
        int activeTicks, wallTicks, momentum, sentMomentum = -1, runMomentum;
        long vibrateReady, gustReady;
        /** The fastest step of the last few ticks, fading: a sudden stop is measured against it. */
        double peak, lastStep;
        boolean barrier;
        double modifier = -1, distanceBank, speed;
        Vec3 heading = Vec3.ZERO;
        Runner(ServerPlayer p) { player = p; level = p.level(); previous = p.position(); trailPrevious = previous; }
    }
    private record Flurry(ServerPlayer player, LivingEntity target, int damage, long start) {}
    /** A blast queued until the end of the tick, so vanilla knockback cannot halve it; then a window for slamming into a wall. */
    private record Impact(ServerPlayer attacker, LivingEntity target, Vec3 push, float force, long until) {}
    private static final List<Impact> PENDING = new ArrayList<>();
    private static final Map<UUID, Impact> SLAMS = new HashMap<>();
    /** Target -> tick before which it cannot be impacted again; target -> end of a rapid-punch flurry; runner -> last punch / catch. */
    private static final Map<UUID, Long> IMPACTED = new HashMap<>(), PUMMELED = new HashMap<>(), LAST_PUNCH = new HashMap<>(), CAUGHT = new HashMap<>();
    public static final ResourceLocation QUICK_HANDS = ResourceLocation.fromNamespaceAndPath("heroesevolved", "speed_quick_hands");
    private SpeedService() {}
    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    public static boolean awakened(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getKey().equals(ModAbilities.SUPERSPEED_ID) && e.getValue().isUnlocked()).isPresent();
    }
    public static boolean active(ServerPlayer p) { return RUNNERS.containsKey(p.getUUID()); }
    public static boolean toggle(ServerPlayer p) {
        if (active(p)) { stop(p); return true; }
        RUNNERS.put(p.getUUID(), new Runner(p));
        syncMarker(p, ACCELERATING, true); quickHands(p, true);
        p.displayClientMessage(Component.translatable("speed.heroesevolved.enabled"), true);
        cue(p); return true;
    }
    public static boolean stop(ServerPlayer p) {
        boolean active = RUNNERS.remove(p.getUUID()) != null;
        if (active) com.jayptucker.heroesevolved.network.MomentumPayload.send(p, 0);
        removeModifiers(p);
        syncMarker(p, ACCELERATING, false); syncMarker(p, WATER_RUN, false); quickHands(p, false);
        return active;
    }
    public static boolean redline(ServerPlayer p) {
        Runner r = RUNNERS.computeIfAbsent(p.getUUID(), id -> new Runner(p));
        syncMarker(p, ACCELERATING, true); quickHands(p, true);
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
        if (entity instanceof ServerPlayer p) {
            p.connection.send(new ClientboundSetEntityMotionPacket(p)); cue(p);
            // A burst ends where it ends: that is not a runner skidding to a stop.
            Runner r = RUNNERS.get(p.getUUID());
            if (r != null) { r.peak = 0; r.gustReady = now(p) + 10; }
        }
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
        applyImpacts(now);
        SpeedKit.tick(server, now);
        SpeedOverrun.tick(server, now);
        BUFFETED.values().removeIf(until -> until <= now);
        IMPACTED.values().removeIf(until -> until <= now); PUMMELED.values().removeIf(until -> until <= now);
        LAST_PUNCH.values().removeIf(at -> at + 40 <= now); CAUGHT.values().removeIf(until -> until <= now);
        REFLEX.values().removeIf(until -> until <= now); DODGED.values().removeIf(until -> until <= now);
    }
    public static void tickRunner(ServerPlayer p) {
        Runner r = RUNNERS.get(p.getUUID()); if (r == null) return;
        // Determine motion before asking time fields whether the runner can act.
        double distance = p.position().subtract(r.previous).horizontalDistanceSqr();
        if (distance > .01 && distance < 25 && p.isSprinting()) {
            r.lastMoving = now(p); r.speed = Math.sqrt(distance);
            r.heading = horizontal(p.position().subtract(r.previous));
        }
        if (!awakened(p) || !PowerRules.canAct(p) || p.isPassenger() || p.level() != r.level || p.isFallFlying()) { stop(p); return; }
        int level = PlayerProgressionService.getLevel(p);
        boolean running = p.isSprinting() && !p.isShiftKeyDown();
        boolean redline = now(p) < r.redlineUntil;
        if (running && distance > .01 && distance < 64) r.momentum = Math.min(100, r.momentum + 3);
        // A tick with no step while still running is usually only the client's moves arriving unevenly
        // (two in one server tick, none in the next): Momentum fades only once you have really stopped.
        else if (!running || now(p) - r.lastMoving > 2) r.momentum = Math.max(0, r.momentum - 6);
        if (running && distance > .01 && distance < 64) runFeats(p, r, Math.sqrt(distance));
        if (running && distance > .01 && distance < 64) easeHunger(p, Math.sqrt(distance));
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
        if (running && distance > .01) r.runMomentum = r.momentum;
        if (vibrate(p, r)) { r.previous = p.position(); return; }
        stopping(p, r, distance);
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

    /**
     * Vibrate: at 80 Momentum a wall you run into is no wall at all. Your molecules shake through
     * anything up to three blocks thick and you come out the other side still running. Bedrock,
     * barriers and protected ground still stop you, and you never end up inside a block.
     */
    private static boolean vibrate(ServerPlayer p, Runner r) {
        long now = now(p);
        // Hitting a wall stops you dead, and the client stops sprinting, so look at the run you just had.
        if (r.runMomentum < VIBRATE_MOMENTUM || now - r.lastMoving > 3 || now < r.vibrateReady || p.isShiftKeyDown()) return false;
        Vec3 heading = r.heading.lengthSqr() > 0 ? r.heading : horizontal(p.getLookAngle());
        if (!Double.isFinite(heading.x) || !wallAhead(p, heading)) return false;
        Vec3 from = p.position(), to = com.jayptucker.heroesevolved.phasing.Passage.through(p, heading, VIBRATE_WALL + 1.5);
        r.vibrateReady = now + 8;
        if (to == null) return false;
        p.teleportTo(p.serverLevel(), to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        // Through the wall with the run intact: Momentum carries on, and so does the heading.
        r.momentum = r.runMomentum; r.lastMoving = now; r.trailPrevious = to;
        p.setDeltaMovement(heading.scale(.6)); p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        AbilityEffects.send(p.serverLevel(), Kind.SPEED_VIBRATE, from, to, 1);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, .5F, 2F);
        p.serverLevel().playSound(null, BlockPos.containing(from), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .6F, 1.8F);
        return true;
    }

    /** The share of vanilla's sprinting hunger (0.1 a block) a runner pays with Accelerate on: at Power Level 1, and at 20. */
    public static final float RUN_HUNGER_START = .6F, RUN_HUNGER_MASTERED = .15F;

    /**
     * The share of sprinting hunger this runner pays: the more they have grown into the power, the less running
     * costs their body. 60% at Power Level 1, falling evenly to 15% at Power Level 20 (about 27% at 15, where
     * Overrun opens: the twenty-odd seconds flat out it takes then cost under half a bar).
     */
    public static float runHunger(ServerPlayer p) {
        float k = Math.clamp((PlayerProgressionService.getPowerLevel(p) - 1) / 19F, 0, 1);
        return RUN_HUNGER_START + (RUN_HUNGER_MASTERED - RUN_HUNGER_START) * k;
    }

    /**
     * At fifty blocks a second vanilla's sprint hunger would empty a full bar in about twelve seconds; Accelerate
     * is paid for in stamina, so a runner's body pays only {@link #runHunger} of it. This refunds the rest of
     * what the step just cost (it was charged when the move arrived, earlier in the tick).
     */
    private static void easeHunger(ServerPlayer p, double step) {
        var food = p.getFoodData();
        food.setExhaustion(Math.max(0, food.getExhaustionLevel() - (float) (.1 * step * (1 - runHunger(p)))));
    }

    /** Speed (blocks a tick) a stop must come down from to throw a gust; Momentum it needs; how far the gust reaches. */
    public static final double GUST_SPEED = .9, GUST_REACH = 4.5;
    public static final int GUST_MOMENTUM = 40;

    /** Watches for a sudden stop: the step falling to a third of the recent pace in a tick or two. */
    private static void stopping(ServerPlayer p, Runner r, double distanceSqr) {
        // A jump of five blocks or more in a tick is a burst or a teleport, not running.
        if (distanceSqr >= 25) { r.peak = 0; return; }
        double step = Math.sqrt(distanceSqr);
        // Moves can arrive unevenly (two in one server tick, none in the next), so the pace is the mean of
        // two ticks, and a stop must show in both the pace and the latest step.
        double pace = (step + r.lastStep) / 2;
        r.lastStep = step;
        if (r.peak >= GUST_SPEED && pace < r.peak * .35 && step < r.peak * .35 && r.runMomentum >= GUST_MOMENTUM && now(p) >= r.gustReady) {
            gust(p, r, r.peak);
            r.peak = 0;
            return;
        }
        r.peak = Math.max(pace, r.peak * .88);
    }

    /**
     * A sudden stop at speed: the air you were dragging along keeps going. It rolls on past you and shoves
     * back whatever is close, hardest straight ahead; it does no harm.
     */
    static void gust(ServerPlayer p, Runner r, double peak) {
        ServerLevel level = p.serverLevel();
        r.gustReady = now(p) + 20;
        Vec3 heading = r.heading.lengthSqr() > 0 ? r.heading : horizontal(p.getLookAngle());
        float strength = (float) Math.clamp((peak - GUST_SPEED) / 1.6 + .3, .3, 1);
        for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(GUST_REACH, 1.5, GUST_REACH),
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof LivingEntity l && canHit(p, l))) {
            Vec3 rel = e.position().subtract(p.position()).multiply(1, 0, 1);
            double d = rel.length();
            if (d > GUST_REACH) continue;
            Vec3 away = d < 1e-3 ? heading : rel.scale(1 / d);
            double ahead = away.dot(heading);
            double k = (1 - d / GUST_REACH) * (ahead > 0 ? .6 + .4 * ahead : .45) * strength;
            Vec3 direction = horizontal(away.add(heading.scale(Math.max(0, ahead))));
            if (!Double.isFinite(direction.x)) direction = heading;
            double resist = e instanceof LivingEntity l ? Math.max(.2, 1 - l.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) : 1;
            Vec3 shove = direction.scale((.3 + .6 * k) * resist).add(0, (.18 + .15 * k) * resist, 0);
            e.setDeltaMovement(shove); e.hurtMarked = true;
            if (e instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        com.jayptucker.heroesevolved.network.SetPiecePayload.send(level, com.jayptucker.heroesevolved.styles.Piece.SPEED_GUST,
                p.position(), p.position().add(heading), strength, null);
        level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, .6F + .5F * strength, 1.35F);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .6F, .7F);
    }

    /** Test hook: a stop from {@code peak} blocks a tick, as if the runner had just skidded to a halt. */
    public static void gustForTest(ServerPlayer p, double peak) {
        Runner r = RUNNERS.computeIfAbsent(p.getUUID(), id -> new Runner(p));
        gust(p, r, peak);
    }

    /** Running flat out: Accelerate on, sprinting, at least {@code momentum} Momentum, really covering {@code speed} blocks a tick. */
    public static boolean flatOut(ServerPlayer p, double speed, int momentum) {
        Runner r = RUNNERS.get(p.getUUID());
        return r != null && r.lastMoving != Long.MIN_VALUE && now(p) - r.lastMoving <= 2 && r.speed >= speed
                && r.momentum >= momentum && p.isSprinting() && !p.isShiftKeyDown();
    }

    /** After a jump through time: the run is spent, and the next step is measured from where you are now. */
    static void landed(ServerPlayer p) {
        Runner r = RUNNERS.get(p.getUUID());
        if (r == null) return;
        r.momentum = 0; r.runMomentum = 0; r.peak = 0; r.previous = p.position(); r.trailPrevious = p.position();
        r.lastMoving = Long.MIN_VALUE; r.gustReady = now(p) + 20;
    }

    /** A real wall ahead: something solid even after stepping up a block, not just a ledge to climb. */
    public static boolean wallAhead(Entity e, Vec3 heading) {
        return !e.level().noCollision(e, e.getBoundingBox().move(heading.scale(.35)).move(0, .6, 0).deflate(.02, 0, .02));
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
    /** Running speed in blocks per tick that starts to land as an impact, and the speed at which it is full. */
    static final double IMPACT_SPEED = .6, FULL_IMPACT_SPEED = 2;

    /** How hard a punch lands from actual running speed: 0 below {@link #IMPACT_SPEED}, 1 at full. */
    public static float impactForce(ServerPlayer p) {
        Runner r = RUNNERS.get(p.getUUID());
        if (r == null || r.lastMoving == Long.MIN_VALUE || now(p) - r.lastMoving > 3 || !PowerRules.canAct(p)) return 0;
        return (float) Math.min(1, Math.max(0, (r.speed - IMPACT_SPEED) / (FULL_IMPACT_SPEED - IMPACT_SPEED)));
    }

    /**
     * A punch thrown at speed: extra damage scaled by how fast you were really moving, and the target
     * blasted along your running line. Returns the bonus damage. Players take less of both.
     */
    public static float speedImpact(ServerPlayer p, LivingEntity t) {
        Runner r = RUNNERS.get(p.getUUID());
        if (r == null || !canHit(p, t)) return 0;
        float force = impactForce(p);
        if (force <= 0) return 0;
        boolean pvp = t instanceof ServerPlayer;
        Vec3 toward = horizontal(t.position().subtract(p.position()));
        Vec3 line = r.heading.lengthSqr() > 0 ? r.heading : horizontal(p.getLookAngle());
        Vec3 direction = horizontal(line.scale(.65).add(Double.isFinite(toward.x) ? toward.scale(.35) : Vec3.ZERO));
        if (!Double.isFinite(direction.x)) direction = horizontal(p.getLookAngle());
        double resist = t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * .85;
        double scale = (pvp ? .6 : 1) * Math.max(.15, 1 - resist);
        Vec3 push = direction.scale((.9 + 2.3 * force) * scale).add(0, (.35 + .3 * force) * Math.max(.4, scale), 0);
        if (IMPACTED.getOrDefault(t.getUUID(), 0L) > now(p)) return 0;
        IMPACTED.put(t.getUUID(), now(p) + 10);
        PENDING.removeIf(i -> i.target == t);
        PENDING.add(new Impact(p, t, push, force, now(p) + 15));
        // Hitting something solid costs you some of your run.
        r.momentum = Math.max(0, r.momentum - 15);

        ServerLevel level = p.serverLevel();
        Vec3 at = t.position().add(0, t.getBbHeight() * .6, 0);
        AbilityEffects.send(level, Kind.SPEED_IMPACT, p.getEyePosition(), at, 1.4 + force * 1.6);
        if (force >= .6) AbilityEffects.send(level, Kind.SPEED_BOOM, at, at.add(direction), .8 + force);
        level.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1, .7F);
        level.playSound(null, t.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, .25F + force * .35F, 1.6F - force * .4F);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, at, 8, .25F + force * .6F, 6);
        return pvp ? 1 + 1.5F * force : 2 + 8 * force;
    }

    /** Applies queued blasts after vanilla's own knockback, and slams flying targets into walls. */
    static void applyImpacts(long now) {
        for (Impact i : List.copyOf(PENDING)) {
            LivingEntity t = i.target;
            if (!t.isAlive()) continue;
            t.setDeltaMovement(i.push); t.hurtMarked = true;
            if (t instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
            else SLAMS.put(t.getUUID(), i);
        }
        PENDING.clear();
        for (Impact i : List.copyOf(SLAMS.values())) {
            LivingEntity t = i.target;
            if (!t.isAlive() || now > i.until || t.level() != i.attacker.level()) { SLAMS.remove(t.getUUID()); continue; }
            if (!t.horizontalCollision) continue;
            SLAMS.remove(t.getUUID());
            ServerLevel level = (ServerLevel) t.level();
            t.invulnerableTime = 0;
            hit(i.attacker, t, 2 + 4 * i.force);
            Vec3 at = t.position().add(0, t.getBbHeight() * .5, 0);
            AbilityEffects.send(level, Kind.SPEED_IMPACT, at, at.add(i.push.normalize()), 2 + i.force);
            level.playSound(null, t.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .45F, .6F);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, at, 8, .4F + i.force * .5F, 6);
        }
    }

    /** While Accelerate is on, your arms are as fast as your legs: weapons recharge three times faster. */
    private static void quickHands(ServerPlayer p, boolean enabled) {
        var attribute = p.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) return;
        if (enabled && !attribute.hasModifier(QUICK_HANDS))
            attribute.addTransientModifier(new AttributeModifier(QUICK_HANDS, 2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!enabled) attribute.removeModifier(QUICK_HANDS);
    }

    /** Fastest a speedster's punches can land on the same body, in ticks. */
    static final int PUNCH_INTERVAL = 2;

    /**
     * Before a punch lands: a speedster's next blow comes faster than a body can flinch, so the
     * hurt-immunity left by the previous one is waived (at most every {@link #PUNCH_INTERVAL} ticks).
     */
    public static void beforePunch(ServerPlayer p, Entity target) {
        if (!(target instanceof LivingEntity t) || !SpeedMovement.accelerating(p) || !canHit(p, t)) return;
        long now = now(p);
        if (LAST_PUNCH.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) + PUNCH_INTERVAL > now) return;
        LAST_PUNCH.put(p.getUUID(), now);
        t.invulnerableTime = 0;
    }

    /**
     * A punch's damage. Inside a flurry of rapid punches every blow after the first does half damage
     * (a third against players); the first carries the Momentum charge and the running impact.
     */
    public static float punch(ServerPlayer p, LivingEntity t, float amount) {
        boolean pvp = t instanceof ServerPlayer;
        float total = amount + consumeImpact(p, pvp) + speedImpact(p, t);
        if (!SpeedMovement.accelerating(p)) return total;
        long now = now(p);
        boolean followUp = PUMMELED.getOrDefault(t.getUUID(), 0L) > now;
        PUMMELED.put(t.getUUID(), now + 10);
        if (!followUp) return total;
        AbilityEffects.send(p.serverLevel(), Kind.SPEED_IMPACT, p.getEyePosition(), t.getEyePosition(), .7);
        return total * (pvp ? .35F : .5F);
    }

    /**
     * Right-click a shot in flight to pluck it out of the air. Arrows and thrown items you could pick
     * up go into your pocket; anything else (fireballs, skulls) is simply snuffed out.
     */
    public static boolean catchShot(ServerPlayer p, Entity e) {
        if (!(e instanceof Projectile shot) || shot.isRemoved() || shot.getOwner() == p || !SpeedMovement.accelerating(p)) return false;
        long now = now(p);
        if (CAUGHT.getOrDefault(p.getUUID(), 0L) > now || shot.level() != p.level()) return false;
        Vec3 eye = p.getEyePosition(), to = shot.position().subtract(eye);
        // Server-side reach and aim check: within 7 blocks and roughly where you are looking, and still flying.
        if (to.lengthSqr() > 49 || to.lengthSqr() > 1 && to.normalize().dot(p.getLookAngle()) < .8
                || shot.getDeltaMovement().lengthSqr() < .0025) return false;
        if (!PlayerEnergyService.tryConsume(p, 2)) return false;
        CAUGHT.put(p.getUUID(), now + 4);
        net.minecraft.world.item.ItemStack prize = net.minecraft.world.item.ItemStack.EMPTY;
        if (shot instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow && arrow.pickup == net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED)
            prize = arrow.getPickupItemStackOrigin().copy();
        else if (shot instanceof net.minecraft.world.entity.projectile.ThrowableItemProjectile thrown) prize = thrown.getItem().copy();
        Vec3 at = shot.position();
        shot.discard();
        if (!prize.isEmpty() && !p.getInventory().add(prize)) p.drop(prize, false);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        ServerLevel level = p.serverLevel();
        AbilityEffects.send(level, Kind.SPEED_IMPACT, eye, at, .8);
        AbilityEffects.send(level, Kind.SPEED_TRAIL, p.position(), at.subtract(0, .9, 0), .6);
        level.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .6F, 1.6F);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .4F, 2F);
        return true;
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
        stop(p); FLURRIES.remove(p.getUUID()); REFLEX.remove(p.getUUID()); DODGED.remove(p.getUUID()); SpeedKit.forget(p); SpeedOverrun.forget(p);
        com.jayptucker.heroesevolved.network.MomentumPayload.send(p, 0);
    }

    /**
     * A death or a change of world: the run ends as {@link #forget} ends it, but the last minute is kept, so a
     * fast enough runner can still go back into it (undoing the death, or back through the portal).
     */
    public static void interrupt(ServerPlayer p) {
        stop(p); FLURRIES.remove(p.getUUID()); REFLEX.remove(p.getUUID()); DODGED.remove(p.getUUID()); SpeedKit.forget(p); SpeedOverrun.interrupt(p);
        com.jayptucker.heroesevolved.network.MomentumPayload.send(p, 0);
    }
    public static void clear() { for (Runner r : List.copyOf(RUNNERS.values())) stop(r.player); FLURRIES.clear(); REFLEX.clear(); DODGED.clear(); BUFFETED.clear(); PENDING.clear(); SLAMS.clear(); IMPACTED.clear(); PUMMELED.clear(); LAST_PUNCH.clear(); CAUGHT.clear(); SpeedKit.clear(); SpeedOverrun.clear(); }
    private static void cue(ServerPlayer p) { p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .7F, 1.4F); }
}
