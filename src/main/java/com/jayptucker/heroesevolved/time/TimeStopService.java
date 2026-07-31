package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative, local Time Stop fields. The server itself keeps
 * running, so energy, networking, and players outside the field remain safe.
 */
public final class TimeStopService {
    private static final Map<UUID, TimeStopField> ACTIVE_FIELDS =
            new HashMap<>();
    private static final Map<UUID, StoppedProjectile> STOPPED_PROJECTILES =
            new HashMap<>();

    private TimeStopService() {
    }

    public static boolean hasActiveField(ServerPlayer player) {
        return ACTIVE_FIELDS.containsKey(player.getUUID());
    }

    public static void start(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();

        playTemporalCue(level, player.position(), player);

        ACTIVE_FIELDS.put(
                player.getUUID(),
                new TimeStopField(
                        player.getUUID(),
                        level.dimension(),
                        player.position(),
                        durationSecondsForLevel(powerLevel) * 20
                )
        );
    }

    public static void stop(ServerPlayer player) {
        TimeStopField field = ACTIVE_FIELDS.remove(player.getUUID());

        if (field != null) {
            playTemporalCue(player.serverLevel(), field.center(), player);
        }

        if (ACTIVE_FIELDS.isEmpty()) {
            restoreAllProjectiles();
        }
    }

    public static void tick(MinecraftServer server) {
        Iterator<TimeStopField> iterator = ACTIVE_FIELDS.values().iterator();

        while (iterator.hasNext()) {
            TimeStopField field = iterator.next();
            ServerLevel level = server.getLevel(field.dimension());
            ServerPlayer owner = server.getPlayerList().getPlayer(
                    field.ownerId()
            );

            boolean hasEnded = level == null
                    || owner == null
                    || EclipseService.arePowersSuppressed(owner)
                    || field.advanceTimer();

            if (hasEnded) {
                iterator.remove();

                if (level != null && owner != null) {
                    playTemporalCue(level, field.center(), owner);
                }
            }
        }

        if (ACTIVE_FIELDS.isEmpty()) {
            restoreAllProjectiles();
        }
    }

    /**
     * Stops all entity ticking inside an active field except its owner. A
     * projectile additionally receives a zero-velocity packet so clients see
     * it suspended in the air instead of predicting continued movement.
     */
    public static boolean shouldStopEntityTick(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        TimeStopField field = getContainingField(entity, level);

        if (entity instanceof Projectile projectile) {
            updateProjectileState(projectile, level, field != null);
        }

        return field != null;
    }

    public static int energyCostForLevel(int powerLevel) {
        return switch (Math.clamp(powerLevel, 3, 5)) {
            case 3 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelThreeEnergyCost.get();
            case 4 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelFourEnergyCost.get();
            case 5 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelFiveEnergyCost.get();
            default -> throw new IllegalStateException(
                    "Unexpected Time Stop power level."
            );
        };
    }

    public static int cooldownTicksForLevel(int powerLevel) {
        return switch (Math.clamp(powerLevel, 3, 5)) {
            case 3 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelThreeCooldownTicks.get();
            case 4 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelFourCooldownTicks.get();
            case 5 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelFiveCooldownTicks.get();
            default -> throw new IllegalStateException(
                    "Unexpected Time Stop power level."
            );
        };
    }

    private static int durationSecondsForLevel(int powerLevel) {
        return switch (Math.clamp(powerLevel, 3, 5)) {
            case 3 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelThreeDurationSeconds.get();
            case 4 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelFourDurationSeconds.get();
            case 5 -> HeroesEvolvedConfig.COMMON
                    .timeStopLevelFiveDurationSeconds.get();
            default -> throw new IllegalStateException(
                    "Unexpected Time Stop power level."
            );
        };
    }

    private static TimeStopField getContainingField(
            Entity entity,
            ServerLevel level
    ) {
        double radius = HeroesEvolvedConfig.COMMON.timeStopRadius.get();
        double radiusSquared = radius * radius;

        for (TimeStopField field : ACTIVE_FIELDS.values()) {
            if (field.dimension().equals(level.dimension())
                    && !field.ownerId().equals(entity.getUUID())
                    && !field.hasEnded()
                    && entity.position().distanceToSqr(field.center())
                    <= radiusSquared) {
                return field;
            }
        }

        return null;
    }

    private static void updateProjectileState(
            Projectile projectile,
            ServerLevel level,
            boolean isStopped
    ) {
        UUID projectileId = projectile.getUUID();

        if (!isStopped) {
            restoreProjectile(projectileId, projectile, level);
            return;
        }

        StoppedProjectile previousState = STOPPED_PROJECTILES.putIfAbsent(
                projectileId,
                new StoppedProjectile(
                        projectile,
                        projectile.isNoGravity(),
                        projectile.getDeltaMovement()
                )
        );

        projectile.setNoGravity(true);
        projectile.setDeltaMovement(Vec3.ZERO);

        // Clients only need the zero-motion packet once, when the projectile
        // first becomes frozen. Re-sending it every tick wastes bandwidth.
        if (previousState == null) {
            level.getChunkSource().broadcastAndSend(
                    projectile,
                    new ClientboundSetEntityMotionPacket(projectile)
            );
        }
    }

    private static void restoreAllProjectiles() {
        for (StoppedProjectile stopped : STOPPED_PROJECTILES.values()) {
            if (stopped.projectile().isRemoved()
                    || !(stopped.projectile().level() instanceof ServerLevel level)) {
                continue;
            }

            stopped.projectile().setNoGravity(stopped.wasNoGravity());
            stopped.projectile().setDeltaMovement(stopped.velocity());
            level.getChunkSource().broadcastAndSend(
                    stopped.projectile(),
                    new ClientboundSetEntityMotionPacket(stopped.projectile())
            );
        }

        STOPPED_PROJECTILES.clear();
    }

    private static void restoreProjectile(
            UUID projectileId,
            Projectile projectile,
            ServerLevel level
    ) {
        StoppedProjectile stopped = STOPPED_PROJECTILES.remove(projectileId);

        if (stopped != null && !projectile.isRemoved()) {
            projectile.setNoGravity(stopped.wasNoGravity());
            projectile.setDeltaMovement(stopped.velocity());
            level.getChunkSource().broadcastAndSend(
                    projectile,
                    new ClientboundSetEntityMotionPacket(projectile)
            );
        }
    }

    /** Plays the same cue for freezing time and returning it to normal. */
    private static void playTemporalCue(
            ServerLevel level,
            Vec3 position,
            ServerPlayer owner
    ) {
        owner.playNotifySound(
                ModSounds.TIME_SLOW_STOP.get(),
                SoundSource.PLAYERS,
                0.90F,
                1.0F
        );
        level.playSound(
                owner,
                position.x,
                position.y,
                position.z,
                ModSounds.TIME_SLOW_STOP.get(),
                SoundSource.PLAYERS,
                0.90F,
                1.0F
        );
    }

    private static final class TimeStopField {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final Vec3 center;
        private int remainingTicks;

        private TimeStopField(
                UUID ownerId,
                ResourceKey<Level> dimension,
                Vec3 center,
                int remainingTicks
        ) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.center = center;
            this.remainingTicks = remainingTicks;
        }

        private UUID ownerId() {
            return ownerId;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private Vec3 center() {
            return center;
        }

        private boolean advanceTimer() {
            remainingTicks--;
            return hasEnded();
        }

        private boolean hasEnded() {
            return remainingTicks <= 0;
        }
    }

    private record StoppedProjectile(
            Projectile projectile,
            boolean wasNoGravity,
            Vec3 velocity
    ) {
    }
}
