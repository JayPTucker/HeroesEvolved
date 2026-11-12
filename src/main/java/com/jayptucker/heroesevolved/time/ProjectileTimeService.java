package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.network.TemporalCatchStatePayload;
import com.jayptucker.heroesevolved.network.TemporalProjectilePayload;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One velocity and gravity snapshot per projectile, even when time fields overlap. */
public final class ProjectileTimeService {
    public static final int STOPPED = -1, NORMAL = 0;
    public static final double CATCH_SPEED = .4;
    private static final ResourceLocation TACZ_BULLET = ResourceLocation.fromNamespaceAndPath("tacz", "bullet");
    private static final Map<Projectile, State> SHOTS = new HashMap<>();
    private static final Set<UUID> CATCHERS = new HashSet<>();

    private ProjectileTimeService() {}

    private static final class State {
        final boolean noGravity;
        final boolean ballistic;
        Vec3 fullVelocity;
        Vec3 appliedVelocity;
        Vec3 start;
        int interval;
        int movementTicks;
        double scale;

        State(Projectile shot) {
            noGravity = shot.isNoGravity();
            fullVelocity = shot.getDeltaMovement();
            ballistic = isTaczBullet(shot);
        }
    }

    public static boolean isTaczBullet(Projectile shot) {
        return TACZ_BULLET.equals(BuiltInRegistries.ENTITY_TYPE.getKey(shot.getType()));
    }

    static boolean beforeTick(Projectile shot) {
        State state = SHOTS.get(shot);
        Vec3 velocity = state == null ? shot.getDeltaMovement() : state.fullVelocity;
        int interval = interval(shot, velocity);
        if (interval == NORMAL) {
            if (state != null) release(shot, state);
            return false;
        }
        if (state == null && velocity.lengthSqr() < 1e-8 && interval != STOPPED) return false;
        if (state == null) {
            state = new State(shot);
            SHOTS.put(shot, state);
        }
        double scale = interval == STOPPED ? 0 : Math.min(1D / interval, CATCH_SPEED / Math.max(1e-8, velocity.length()));
        Vec3 motion = interval == STOPPED ? Vec3.ZERO : velocity.scale(scale);
        boolean changed = state.interval != interval || !motion.equals(state.appliedVelocity);
        if (state.interval != interval) state.movementTicks = 0;
        state.interval = interval;
        state.scale = scale;
        state.appliedVelocity = motion;
        state.start = shot.position();
        shot.setNoGravity(true);
        shot.setDeltaMovement(motion);
        // TACZ ages rounds through tickCount, even though a stopped entity does not run its tick method.
        if (state.ballistic && (interval == STOPPED || (state.movementTicks + 1) * scale < 1))
            shot.tickCount = Math.max(0, shot.tickCount - 1);
        if (changed) sync(shot, state);
        return interval == STOPPED;
    }

    public static void afterTick(Projectile shot) {
        State state = SHOTS.get(shot);
        if (state == null || state.interval == STOPPED) return;
        if (shot.isRemoved()) {
            SHOTS.remove(shot);
            return;
        }
        Vec3 motion = shot.getDeltaMovement();
        if (shot instanceof AbstractArrow
                && shot.position().distanceToSqr(state.start) < 1e-9) {
            // An arrow embedded in a block must stay embedded when time resumes.
            state.fullVelocity = Vec3.ZERO;
            release(shot, state);
            return;
        }
        if (++state.movementTicks * state.scale >= 1) {
            state.movementTicks = 0;
            if (!state.ballistic) state.fullVelocity = motion.scale(1 / state.scale);
        }
        // TACZ applies its own gravity every tick. Keep the controlled travel velocity between ticks.
        if (state.ballistic) shot.setDeltaMovement(state.appliedVelocity);
    }

    private static int interval(Projectile shot, Vec3 velocity) {
        Vec3 end = shot.position().add(velocity);
        if (TimeStopService.stopsProjectile(shot, end)) return STOPPED;
        return Math.max(AdvancedTechniques.projectileInterval(shot, end), TimeSlowService.projectileInterval(shot, end));
    }

    /** A fast shot must also slow if its next normal step would cross a field. */
    public static boolean reachesField(Vec3 start, Vec3 end, Vec3 center, double radius) {
        if (start.distanceToSqr(center) <= radius * radius) return true;
        if (start.equals(end)) return false;
        Vec3 path = end.subtract(start);
        double along = Math.clamp(center.subtract(start).dot(path) / Math.max(1e-8, path.lengthSqr()), 0, 1);
        return start.add(path.scale(along)).distanceToSqr(center) <= radius * radius;
    }

    public static boolean canCatch(ServerPlayer player, Projectile shot) {
        State state = SHOTS.get(shot);
        if (!canCatchInTime(player) || state == null || state.fullVelocity.lengthSqr() < 1e-8) return false;
        Vec3 end = shot.position().add(state.fullVelocity);
        return TimeSlowService.controlsShot(player, shot, end) || TimeStopService.controlsShot(player, shot, end)
                || AdvancedTechniques.controlsShot(player, shot, end);
    }

    public static boolean isInFlight(Projectile shot) {
        State state = SHOTS.get(shot);
        return (state == null ? shot.getDeltaMovement() : state.fullVelocity).lengthSqr() > .0025;
    }

    private static boolean canCatchInTime(ServerPlayer player) {
        boolean timeField = TimeSlowService.hasActiveField(player) || TimeStopService.hasActiveField(player);
        return (timeField && SpaceTimeService.awakened(player) || SpeedService.awakened(player) && AdvancedTechniques.perceiving(player))
                && PowerRules.canAct(player);
    }

    public static void tick(MinecraftServer server) {
        SHOTS.keySet().removeIf(Projectile::isRemoved);
        CATCHERS.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean active = canCatchInTime(player);
            boolean changed = active ? CATCHERS.add(player.getUUID()) : CATCHERS.remove(player.getUUID());
            if (changed) PacketDistributor.sendToPlayer(player, new TemporalCatchStatePayload(active));
        }
    }

    public static void sendToTracker(ServerPlayer player, Projectile shot) {
        State state = SHOTS.get(shot);
        if (state != null) PacketDistributor.sendToPlayer(player, new TemporalProjectilePayload(shot.getId(), state.interval,
                shot.position(), state.appliedVelocity));
    }

    public static void releaseInactive() {
        for (var entry : Map.copyOf(SHOTS).entrySet())
            if (interval(entry.getKey(), entry.getValue().fullVelocity) == NORMAL) release(entry.getKey(), entry.getValue());
    }

    public static void clear() {
        Map.copyOf(SHOTS).forEach(ProjectileTimeService::release);
        CATCHERS.clear();
    }

    private static void release(Projectile shot, State state) {
        SHOTS.remove(shot);
        shot.setNoGravity(state.noGravity);
        shot.setDeltaMovement(state.fullVelocity);
        if (!shot.isRemoved()) sync(shot, state);
    }

    private static void sync(Projectile shot, State state) {
        boolean active = SHOTS.containsKey(shot);
        PacketDistributor.sendToPlayersTrackingEntity(shot, new TemporalProjectilePayload(shot.getId(),
                active ? state.interval : NORMAL, shot.position(), shot.getDeltaMovement()));
    }
}
