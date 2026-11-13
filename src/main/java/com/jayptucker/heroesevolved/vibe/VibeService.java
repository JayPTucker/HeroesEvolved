package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VibeService {
    public enum Blast { PALM, LANCE, REVERB, CROSSWAVE }

    private record Shot(ServerPlayer owner, ServerLevel level, Vec3 position, Vec3 aim, double range, Blast kind, int tier, long due,
                        float boost) {}
    private record Wave(ServerPlayer owner, ServerLevel level, Vec3 hand, Vec3 end, float scale, float damage,
                        boolean heavy, Set<UUID> struck, long start, double travel) {}
    private record Lock(ServerPlayer owner, ServerLevel level, Vec3 center, double radius, int tier, long due, boolean prism) {}
    private record Desync(ServerLevel level, long until) {}
    private static final class Guard {
        final ServerPlayer owner;
        final ServerLevel level;
        final long started;
        final float width;
        final int duration;
        final boolean mirror;
        int stored;
        Vec3 lastCenter;

        Guard(ServerPlayer owner, boolean mirror) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.started = level.getGameTime();
            this.mirror = mirror;
            width = mirror ? VibeGeometry.HORIZON_WIDTH : VibeGeometry.GUARD_WIDTH;
            duration = mirror ? 100 : 70;
        }
    }

    private static final Map<UUID, Shot> SHOTS = new HashMap<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final Map<UUID, Guard> GUARDS = new HashMap<>();
    private static final Map<UUID, Lock> LOCKS = new HashMap<>();
    private static final Map<UUID, Desync> DESYNCED = new HashMap<>();
    /** Overflow's collapses, queued to land when Return Fire's wave reaches its end. */
    private static final List<Lock> OVERFLOWS = new ArrayList<>();
    /** Slingshot: the tick until which a viber's next blast fires at once and harder. */
    private static final Map<UUID, Long> PRIMED = new HashMap<>();
    static final int SLINGSHOT_TICKS = 60, OVERFLOW_MIN = 3;
    static final float SLINGSHOT_BOOST = 1.3F, OVERFLOW_RADIUS = 3.2F;

    private VibeService() {}

    static boolean nativePower(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(ModAbilities.VIBE_ID) && entry.getValue().isUnlocked()).isPresent();
    }

    static boolean active(ServerPlayer player) {
        return !player.isRemoved() && PowerRules.canAct(player) && (nativePower(player) || MimicryService.isBorrowingVibe(player));
    }

    public static boolean ready(ServerPlayer player) {
        return active(player) && !player.isPassenger() && !player.isVehicle() && !EntityCarryService.isCarrying(player);
    }

    static boolean hostile(ServerPlayer owner, LivingEntity target) {
        return StyleOps.isFoe(owner, target) && !(target instanceof TemporalEchoEntity);
    }

    public static boolean blast(AbilityUseContext context, Blast kind) {
        ServerPlayer player = context.player();
        if (!ready(player) || SHOTS.containsKey(player.getUUID())) return false;
        Vec3 direction = player.getLookAngle();
        Vec3 hand = VibeGeometry.hand(player, direction, 1, false);
        Vec3 eye = player.getEyePosition();
        double range = switch (kind) {
            case PALM -> 17;
            case LANCE -> 27;
            case REVERB -> 21;
            case CROSSWAVE -> 24;
        };
        Vec3 end = VibeGeometry.clip(player, eye, eye.add(direction.scale(range)));
        if (hand.distanceToSqr(end) < .2 || end.subtract(eye).dot(direction) < .8) return VibeBreaches.fail(player, "blocked");
        // Slingshot: a blast cast soon after crossing your own breach skips its wind-up and hits harder.
        Long primed = PRIMED.remove(player.getUUID());
        boolean slung = primed != null && primed > player.serverLevel().getGameTime();
        int windup = slung ? 1 : kind == Blast.PALM ? 3 : 6;
        ArmPose pose = switch (kind) {
            case PALM -> ArmPose.VIBE_PALM;
            case LANCE -> ArmPose.VIBE_LANCE;
            case REVERB, CROSSWAVE -> ArmPose.VIBE_CROSS;
        };
        ArmPosePayload.show(player, pose, 17);
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_GATHER, hand, end, windup, player);
        StyleOps.sound(player.serverLevel(), hand, SoundEvents.AMETHYST_BLOCK_CHIME, .6F, .75F);
        SHOTS.put(player.getUUID(), new Shot(player, player.serverLevel(), player.position(), direction, range, kind,
                context.abilityLevel(), player.serverLevel().getGameTime() + windup, slung ? SLINGSHOT_BOOST : 1));
        return true;
    }

    /** Slingshot: crossing your own breach primes your next blast. */
    static void prime(ServerPlayer player) {
        if (SkillTreeService.has(player, "vibe_1_0"))
            PRIMED.put(player.getUUID(), player.serverLevel().getGameTime() + SLINGSHOT_TICKS);
    }

    static boolean primed(ServerPlayer player) {
        Long until = PRIMED.get(player.getUUID());
        return until != null && until > player.serverLevel().getGameTime();
    }

    private static void release(Shot shot) {
        ServerPlayer player = shot.owner();
        Vec3 direction = shot.aim();
        Vec3 eye = player.getEyePosition();
        Vec3 hand = VibeGeometry.hand(player, direction, 1, false);
        Vec3 end = VibeGeometry.clip(player, eye, eye.add(direction.scale(shot.range())));
        if (end.subtract(eye).dot(direction) < .6) return;
        end = VibeGeometry.clip(player, hand, end);
        if (end.subtract(hand).dot(direction) < .2) return;
        float scale = switch (shot.kind()) {
            case PALM -> .85F;
            case LANCE -> .48F;
            case REVERB -> 1.45F;
            case CROSSWAVE -> 1.8F;
        };
        boolean doublePalm = shot.kind() == Blast.REVERB || shot.kind() == Blast.CROSSWAVE;
        Set<UUID> struck = new HashSet<>();
        float damage = (shot.kind() == Blast.PALM ? 4 + shot.tier() : 7 + shot.tier() * 1.5F) * shot.boost();
        if (shot.boost() > 1) SetPiecePayload.send(shot.level(), Piece.VIBE_HIT, hand, hand.add(direction), 1.1F, player);
        Vec3 through = SkillTreeService.has(player, "vibe_0_0")
                ? seam(player, hand, end, shot.range(), scale, damage, doublePalm, struck) : null;
        wave(player, hand, through != null ? through : end, scale, damage, doublePalm, false, struck);
        if (doublePalm) {
            Vec3 side = VibeGeometry.side(direction);
            if (player.getMainArm() == HumanoidArm.RIGHT) side = side.reverse();
            Vec3 offHand = VibeGeometry.hand(player, direction, 1, true);
            Vec3 offEnd = VibeGeometry.clip(player, offHand, end.add(side.scale(shot.kind() == Blast.CROSSWAVE ? 3 : .85)));
            if (offEnd.subtract(offHand).dot(direction) > .2)
                wave(player, offHand, offEnd, scale * .82F, damage, true, true, struck);
        }
        if (shot.kind() == Blast.CROSSWAVE)
            CameraShakePayload.sendAround(shot.level(), hand, 26, .27F, 11);
    }

    private static void wave(ServerPlayer player, Vec3 hand, Vec3 end, float scale, float damage, boolean heavy,
                             boolean offHand, Set<UUID> struck) {
        wave(player, hand, end, scale, damage, heavy, offHand, struck, offHand ? 2 : 0);
    }

    private static void wave(ServerPlayer player, Vec3 hand, Vec3 end, float scale, float damage, boolean heavy,
                             boolean offHand, Set<UUID> struck, int delay) {
        ServerLevel level = player.serverLevel();
        SetPiecePayload.send(level, offHand ? Piece.VIBE_CROSSWAVE : Piece.VIBE_WAVE, hand, end, scale, player, delay);
        if (!offHand) StyleOps.sound(level, hand, ModSounds.VIBE_PULSE.get(), heavy ? 1.15F : .8F, heavy ? .82F : 1.18F);
        WAVES.add(new Wave(player, level, hand, end, scale, damage, heavy, struck,
                level.getGameTime() + delay, VibeGeometry.waveTravel(hand.distanceTo(end))));
    }

    /**
     * Through the Seam: a wave that crosses one of the owner's open breach mouths carries on out of the other one,
     * with the range it had left, as soon as the front reaches the mouth. Both waves share one strike list.
     * Returns where the first wave should stop (the mouth it enters), or null if it crosses no mouth.
     */
    private static Vec3 seam(ServerPlayer player, Vec3 hand, Vec3 end, double range, float scale, float damage,
                             boolean heavy, Set<UUID> struck) {
        VibeBreaches.Seam seam = VibeBreaches.seam(player, hand, end);
        if (seam == null) return null;
        double used = hand.distanceTo(seam.through());
        double left = range - used;
        if (left < 2) return null;
        Vec3 start = seam.out().add(seam.direction().scale(.35));
        Vec3 far = VibeGeometry.clip(player, start, start.add(seam.direction().scale(left)));
        if (far.subtract(start).dot(seam.direction()) < 1) return null;
        int delay = (int) Math.ceil(VibeGeometry.waveTravel(used));
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_HIT, seam.through(), seam.through().add(seam.direction()), .9F, null, delay);
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_HIT, start, start.add(seam.direction()), .9F, null, delay);
        wave(player, start, far, scale, damage, heavy, false, struck, delay);
        return seam.through();
    }

    private static boolean advance(Wave wave) {
        ServerPlayer player = wave.owner();
        ServerLevel level = wave.level();
        if (player.serverLevel() != level || !active(player)) return true;
        long age = level.getGameTime() - wave.start();
        if (age <= 0) return false;
        float scale = wave.scale();
        Vec3 hand = wave.hand(), end = wave.end(), line = end.subtract(hand);
        double previous = (age - 1) / wave.travel(), current = Math.min(1.06, age / wave.travel());
        Vec3 from = hand.add(line.scale(previous)), to = hand.add(line.scale(current));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(scale + 1.5),
                entity -> hostile(player, entity)).stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(hand))).limit(24).toList()) {
            Vec3 center = target.getBoundingBox().getCenter();
            double along = center.subtract(hand).dot(line) / Math.max(.01, line.lengthSqr());
            double radius = scale * (.45 + .55 * Mth.clamp(along, 0, 1)) + .17;
            if (wave.struck().contains(target.getUUID()) || along < previous || along > current
                    || target.getBoundingBox().inflate(radius).clip(hand, end).isEmpty()
                    || VibeGeometry.clip(player, hand, center).distanceToSqr(center) > .01) continue;
            boolean read = VibeSight.marked(player, target);
            if (!target.hurt(player.damageSources().indirectMagic(player, player), wave.damage() * (read ? 1.3F : 1))) continue;
            wave.struck().add(target.getUUID());
            Vec3 push = line.normalize().multiply(1, 0, 1).scale(wave.heavy() ? 1.2 : .8);
            double resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            StyleOps.push(target, push.add(0, wave.heavy() ? .33 : .23, 0).scale(1 - resistance));
            SetPiecePayload.send(level, Piece.VIBE_HIT, center, center.add(line.normalize()), read ? 1.05F : .7F, target);
            if (read && SkillTreeService.has(player, "vibe_afterimage")) desync(target, 28);
        }
        return age >= Math.ceil(wave.travel() * 1.06);
    }

    public static boolean guard(ServerPlayer player, boolean mirror) {
        if (!ready(player)) return false;
        if (GUARDS.containsKey(player.getUUID())) return VibeBreaches.fail(player, "guard_empty");
        Guard guard = new Guard(player, mirror);
        GUARDS.put(player.getUUID(), guard);
        ArmPosePayload.show(player, ArmPose.VIBE_GUARD, 14);
        StyleOps.sound(guard.level, player.position(), ModSounds.VIBE_BREACH.get(), .65F, 1.58F);
        return true;
    }

    public static boolean closeGuard(ServerPlayer player) {
        Guard guard = GUARDS.remove(player.getUUID());
        if (guard == null) return false;
        ArmPosePayload.clearIf(player, ArmPose.VIBE_GUARD);
        if (guard.lastCenter != null) {
            Vec3 facing = StyleOps.facing(player);
            SetPiecePayload.send(guard.level, Piece.VIBE_GUARD_CLOSE, guard.lastCenter,
                    guard.lastCenter.add(facing), guard.width, player);
        }
        return true;
    }

    public static boolean returnFire(AbilityUseContext context) {
        ServerPlayer player = context.player();
        Guard guard = GUARDS.get(player.getUUID());
        if (guard == null || guard.stored == 0 || !ready(player)) return false;
        closeGuard(player);
        Vec3 aim = player.getLookAngle();
        Vec3 hand = VibeGeometry.hand(player, aim, 1, false);
        Vec3 end = VibeGeometry.clip(player, hand, hand.add(aim.scale(23)));
        ArmPosePayload.show(player, ArmPose.VIBE_LANCE, 14);
        float bonus = SkillTreeService.has(player, "vibe_backscatter") ? 2.7F : 1.8F;
        wave(player, hand, end, .65F + guard.stored * .14F, 5 + guard.stored * bonus, true, false, new HashSet<>());
        // Overflow: a full enough bank bursts into interference where the wave stops.
        if (guard.stored >= OVERFLOW_MIN && SkillTreeService.has(player, "vibe_reservoir")) {
            Vec3 at = StyleOps.ground(player.serverLevel(), end.subtract(aim.scale(.4)));
            long due = player.serverLevel().getGameTime() + (long) Math.ceil(VibeGeometry.waveTravel(hand.distanceTo(end)));
            OVERFLOWS.add(new Lock(player, player.serverLevel(), at, OVERFLOW_RADIUS, context.abilityLevel(), due, false));
        }
        return true;
    }

    private static Vec3 guardCenter(ServerPlayer player, Vec3 normal) {
        return player.position().add(0, player.isCrouching() ? 1 : 1.23, 0).add(normal.scale(1.48));
    }

    public static boolean hasGuard() { return !GUARDS.isEmpty(); }

    private static void tickGuard(Guard guard) {
        ServerPlayer player = guard.owner;
        long age = guard.level.getGameTime() - guard.started;
        if (!ready(player) || player.serverLevel() != guard.level || age >= guard.duration) {
            closeGuard(player);
            return;
        }
        Vec3 normal = StyleOps.facing(player);
        Vec3 center = guardCenter(player, normal);
        guard.lastCenter = center;
        if (age == 1 || age % 8 == 0) {
            SetPiecePayload.send(guard.level, Piece.VIBE_GUARD, center, center.add(normal), guard.width + guard.stored * 4, player);
            ArmPosePayload.show(player, ArmPose.VIBE_GUARD, 12);
        }
    }

    /** Impact hooks also catch very fast shots whose whole step took place between server ticks. */
    public static boolean intercept(ServerLevel level, Entity shot, Vec3 from, Vec3 to) {
        if (shot.isRemoved() || GUARDS.isEmpty()) return false;
        for (Guard guard : GUARDS.values())
            if (guard.level == level && intercept(guard, shot, from, to)) return true;
        return false;
    }

    private static boolean intercept(Guard guard, Entity shot, Vec3 from, Vec3 to) {
        ServerPlayer player = guard.owner;
        if (!ready(player) || player.serverLevel() != guard.level || guard.level.getGameTime() - guard.started >= guard.duration
                || !(shot instanceof Projectile) && !PowerWeapons.isBullet(shot)) return false;
        Entity source = shot instanceof Projectile projectile ? projectile.getOwner() : null;
        if (source instanceof LivingEntity living && !hostile(player, living)) return false;
        Vec3 normal = StyleOps.facing(player);
        if (to.subtract(from).dot(normal) >= -.0001) return false;
        Vec3 center = guardCenter(player, normal);
        Vec3 hit = VibeGeometry.crossing(from, to, center, normal, guard.width, VibeGeometry.PORTAL_HEIGHT);
        if (hit == null || VibeGeometry.clip(player, from, hit).distanceToSqr(hit) > .0025
                || VibeGeometry.clip(player, player.getEyePosition(), center).distanceToSqr(center) > .0025) return false;
        guard.stored = Math.min(5, guard.stored + 1);
        // Return Address: the guard remembers where each shot came from.
        if (source instanceof LivingEntity shooter && SkillTreeService.has(player, "vibe_0_2")) VibeSight.mark(player, shooter);
        shot.discard();
        SetPiecePayload.send(guard.level, Piece.VIBE_HIT, hit, hit.add(normal), .7F, null);
        StyleOps.sound(guard.level, hit, ModSounds.VIBE_SHUT.get(), .45F, 1.7F);
        EvolutionTrials.advance(player, ModAbilities.VIBE_ID);
        return true;
    }

    public static boolean lock(AbilityUseContext context, boolean prism) {
        ServerPlayer player = context.player();
        if (!ready(player) || LOCKS.containsKey(player.getUUID())) return false;
        Vec3 center = VibeBreaches.aimedGround(player, prism ? 23 : 16);
        if (center == null) center = StyleOps.ground(player.serverLevel(), player.position());
        float radius = prism ? 6.8F : 5;
        ArmPosePayload.show(player, ArmPose.VIBE_LOCK, 25);
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_LOCK_WARN, center, center, radius, null);
        StyleOps.sound(player.serverLevel(), center, SoundEvents.RESPAWN_ANCHOR_CHARGE, .82F, 1.35F);
        LOCKS.put(player.getUUID(), new Lock(player, player.serverLevel(), center, radius, context.abilityLevel(),
                player.serverLevel().getGameTime() + VibeGeometry.LOCK_WARNING_TICKS, prism));
        return true;
    }

    private static void collapse(Lock lock) {
        ServerPlayer player = lock.owner();
        Vec3 center = lock.center();
        SetPiecePayload.send(lock.level(), Piece.VIBE_LOCK, center, center, (float) lock.radius(), null);
        StyleOps.sound(lock.level(), center, ModSounds.VIBE_COLLAPSE.get(), .72F, .77F);
        CameraShakePayload.sendAround(lock.level(), center, 27, .3F, 18);
        for (LivingEntity target : StyleOps.foes(player, center, lock.radius())) {
            Vec3 body = target.getBoundingBox().getCenter();
            if (!hostile(player, target) || VibeGeometry.clip(player, center.add(0, .65, 0), body).distanceToSqr(body) > .01) continue;
            boolean read = VibeSight.marked(player, target);
            if (!target.hurt(player.damageSources().indirectMagic(player, player), 4 + lock.tier() + (read ? 4 : 0))) continue;
            int duration = read && SkillTreeService.has(player, "vibe_fixed_point") ? 100 : lock.prism() ? 80 : 55;
            desync(target, duration);
            SetPiecePayload.send(lock.level(), Piece.VIBE_HIT, body, body.add(0, 1, 0), 1, target);
        }
    }

    /** Package-visible so tests can check the speedster rule directly: the test server never allows PvP. */
    static void desync(LivingEntity target, int duration) {
        StyleOps.effect(target, MobEffects.MOVEMENT_SLOWDOWN, duration, 2);
        if (target instanceof ServerPlayer player && SpeedService.awakened(player)) {
            var level = player.serverLevel();
            DESYNCED.merge(player.getUUID(), new Desync(level, level.getGameTime() + duration),
                    (old, next) -> old.level() == level && old.until() >= next.until() ? old : next);
            SpeedService.interrupt(player);
            Vec3 velocity = player.getDeltaMovement();
            StyleOps.push(player, velocity.multiply(.24, 1, .24));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("vibe.heroesevolved.desynced"), true);
        }
    }

    public static boolean desynced(ServerPlayer player) {
        var lock = DESYNCED.get(player.getUUID());
        return lock != null && lock.level() == player.serverLevel() && lock.until() > lock.level().getGameTime();
    }

    public static void awaken(ServerPlayer owner, LivingEntity ward, List<LivingEntity> threats) {
        ArmPosePayload.show(owner, ArmPose.VIBE_OPEN, 20);
        VibeBreaches.rescue(owner, ward, threats);
        Vec3 at = ward.position();
        SetPiecePayload.send(owner.serverLevel(), Piece.VIBE_LOCK, at, at, 4, null);
        for (LivingEntity threat : threats) {
            if (!hostile(owner, threat) || threat.distanceToSqr(ward) > 49) continue;
            StyleOps.shove(threat, at, 1.12, .22);
            desync(threat, 45);
        }
        StyleOps.sound(owner.serverLevel(), at, ModSounds.VIBE_BREACH.get(), .8F, 1.45F);
    }

    public static void tick(MinecraftServer server) {
        for (Wave wave : List.copyOf(WAVES)) if (advance(wave)) WAVES.remove(wave);
        for (Shot shot : List.copyOf(SHOTS.values())) {
            if (!ready(shot.owner()) || shot.owner().serverLevel() != shot.level()
                    || shot.owner().position().distanceToSqr(shot.position()) > 16) SHOTS.remove(shot.owner().getUUID());
            else if (shot.level().getGameTime() >= shot.due()) {
                SHOTS.remove(shot.owner().getUUID());
                release(shot);
            }
        }
        for (Lock lock : List.copyOf(LOCKS.values())) {
            if (!ready(lock.owner()) || lock.owner().serverLevel() != lock.level()) LOCKS.remove(lock.owner().getUUID());
            else if (lock.level().getGameTime() >= lock.due()) {
                LOCKS.remove(lock.owner().getUUID());
                collapse(lock);
            }
        }
        for (Lock overflow : List.copyOf(OVERFLOWS)) {
            if (!active(overflow.owner()) || overflow.owner().serverLevel() != overflow.level()) OVERFLOWS.remove(overflow);
            else if (overflow.level().getGameTime() >= overflow.due()) {
                OVERFLOWS.remove(overflow);
                collapse(overflow);
            }
        }
        PRIMED.entrySet().removeIf(entry -> entry.getValue() < server.overworld().getGameTime() - SLINGSHOT_TICKS);
        for (Guard guard : List.copyOf(GUARDS.values())) tickGuard(guard);
        DESYNCED.values().removeIf(lock -> lock.level().getGameTime() >= lock.until());
        VibeBreaches.tick(server);
        VibeSight.tick(server);
    }

    public static void forget(ServerPlayer player) {
        SHOTS.remove(player.getUUID());
        WAVES.removeIf(wave -> wave.owner() == player);
        LOCKS.remove(player.getUUID());
        OVERFLOWS.removeIf(lock -> lock.owner() == player);
        PRIMED.remove(player.getUUID());
        closeGuard(player);
        if (!VibeBreaches.crossing(player)) VibeBreaches.close(player);
        VibeSight.forget(player);
    }

    public static void clear() {
        SHOTS.clear();
        WAVES.clear();
        LOCKS.clear();
        OVERFLOWS.clear();
        PRIMED.clear();
        GUARDS.clear();
        DESYNCED.clear();
        VibeSight.clear();
        VibeBreaches.clear();
    }
}
