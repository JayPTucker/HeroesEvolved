package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
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
 * Owns the short-lived Time Slow fields created by Time Manipulation users.
 *
 * <p>We deliberately slow individual entity ticks instead of slowing the
 * server itself. That keeps the rest of a multiplayer world responsive while
 * making entities and projectiles in the field update only once per second.</p>
 */
public final class TimeSlowService {
    private static final Map<UUID, TimeSlowField> ACTIVE_FIELDS =
            new HashMap<>();

    // Projectile gravity must be restored to its original state. Tracking it
    // separately prevents Time Slow from changing projectiles that were
    // intentionally created without gravity.
    private static final Map<UUID, ProjectileState>
            SLOWED_PROJECTILES = new HashMap<>();

    private TimeSlowService() {
    }

    public static boolean hasActiveField(ServerPlayer player) {
        return ACTIVE_FIELDS.containsKey(player.getUUID());
    }

    public static void stop(ServerPlayer player) {
        TimeSlowField field = ACTIVE_FIELDS.remove(player.getUUID());

        if (field != null) {
            playEndSound(player.serverLevel(), field, player);
        }

        if (ACTIVE_FIELDS.isEmpty()) {
            restoreAllProjectiles();
        }
    }

    public static void start(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        int durationTicks = HeroesEvolvedConfig.COMMON
                .timeSlowDurationSeconds
                .get() * 20;
        int tickInterval = getTickIntervalForLevel(powerLevel);

        // Starting here, instead of inside the keybind action, keeps the
        // activation sound consistent for manual use and forced awakening.
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSounds.TIME_SLOW_STOP.get(),
                SoundSource.PLAYERS,
                0.90F,
                1.0F
        );

        ACTIVE_FIELDS.put(
                player.getUUID(),
                new TimeSlowField(
                        player.getUUID(),
                        level.dimension(),
                        player.position(),
                        durationTicks,
                        tickInterval
                )
        );
    }

    /**
     * Advances each field's explicit ten-second timer. Field state is
     * intentionally in memory only, so a server restart cannot leave time
     * permanently slowed in a saved world.
     */
    public static void tick(MinecraftServer server) {
        Iterator<TimeSlowField> iterator = ACTIVE_FIELDS.values().iterator();

        while (iterator.hasNext()) {
            TimeSlowField field = iterator.next();
            ServerLevel level = server.getLevel(field.dimension());
            ServerPlayer owner = server.getPlayerList().getPlayer(
                    field.ownerId()
            );

            boolean hasEnded = level == null
                    || owner == null
                    || field.advanceTimer();

            if (hasEnded) {
                iterator.remove();

                if (level != null && owner != null) {
                    playEndSound(level, field, owner);
                }
            }
        }

        // This is a safety net for projectiles removed or unloaded before
        // their next entity tick can restore their original gravity setting.
        if (ACTIVE_FIELDS.isEmpty()) {
            restoreAllProjectiles();
        }
    }

    /**
     * Returns true on all but one tick in the field's level-based interval.
     * Cancelling those entity ticks slows AI, falling blocks, item entities,
     * and other non-projectile entities at the field's current mastery level.
     */
    public static boolean shouldSkipEntityTick(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        TimeSlowField field = getContainingField(entity, level);

        if (entity instanceof Projectile projectile) {
            updateProjectileMotion(
                    projectile,
                    level,
                    field != null,
                    field == null ? 1 : field.tickInterval()
            );

            // Projectiles move every server tick at one twentieth of their
            // stored velocity. This avoids the client/server rubber-banding
            // caused by pausing an arrow for nineteen ticks at a time.
            return false;
        }

        if (field == null) {
            return false;
        }

        int interval = field.tickInterval();
        long gameTime = level.getGameTime();

        // Staggering entities prevents every delayed entity from updating on
        // one expensive server tick while keeping each at the same slow rate.
        return Math.floorMod(entity.getId(), interval)
                != Math.floorMod(gameTime, interval);
    }

    /**
     * Captures the result of a projectile's slow movement ticks. After its
     * level-based interval completes, that result becomes the next full-speed velocity,
     * matching one normal tick of drag without applying it twenty times.
     */
    public static void afterEntityTick(Entity entity) {
        if (!(entity instanceof Projectile projectile)) {
            return;
        }

        ProjectileState state = SLOWED_PROJECTILES.get(projectile.getUUID());

        if (state == null) {
            return;
        }

        if (projectile.isRemoved()) {
            SLOWED_PROJECTILES.remove(projectile.getUUID());
            return;
        }

        state.advanceMovementTick();

        if (state.shouldUpdateFullVelocity()) {
            state.updateFullVelocity(projectile.getDeltaMovement());
        }
    }

    private static TimeSlowField getContainingField(
            Entity entity,
            ServerLevel level
    ) {
        double radius = HeroesEvolvedConfig.COMMON.timeSlowRadius.get();
        double radiusSquared = radius * radius;
        TimeSlowField strongestField = null;

        for (TimeSlowField field : ACTIVE_FIELDS.values()) {
            if (!field.dimension().equals(level.dimension())
                    || field.ownerId().equals(entity.getUUID())
                    || field.hasEnded()) {
                continue;
            }

            if (entity.position().distanceToSqr(field.center())
                    <= radiusSquared) {
                if (strongestField == null
                        || field.tickInterval()
                        > strongestField.tickInterval()) {
                    strongestField = field;
                }
            }
        }

        return strongestField;
    }

    private static void updateProjectileMotion(
            Projectile projectile,
            ServerLevel level,
            boolean isSlowed,
            int tickInterval
    ) {
        UUID projectileId = projectile.getUUID();

        if (!isSlowed) {
            restoreProjectile(projectileId, projectile, level);
            return;
        }

        ProjectileState state = SLOWED_PROJECTILES.computeIfAbsent(
                    projectileId,
                    ignored -> new ProjectileState(
                            projectile,
                            projectile.isNoGravity(),
                            projectile.getDeltaMovement(),
                            tickInterval
                    )
        );

        state.setTickInterval(tickInterval);

        // Time Slow controls when the projectile advances. Leaving vanilla
        // gravity active would make arrows fall between their slow updates.
        projectile.setNoGravity(true);
        projectile.setDeltaMovement(
                state.fullVelocity().scale(1.0D / tickInterval)
        );

        if (state.consumeVelocitySyncRequest()) {
            level.getChunkSource().broadcastAndSend(
                    projectile,
                    new ClientboundSetEntityMotionPacket(projectile)
            );
        }
    }

    private static void restoreAllProjectiles() {
        for (ProjectileState state : SLOWED_PROJECTILES.values()) {
            if (!state.projectile().isRemoved()) {
                state.projectile().setNoGravity(state.wasNoGravity());
                state.projectile().setDeltaMovement(state.fullVelocity());

                if (state.projectile().level() instanceof ServerLevel level) {
                    level.getChunkSource().broadcastAndSend(
                            state.projectile(),
                            new ClientboundSetEntityMotionPacket(
                                    state.projectile()
                            )
                    );
                }
            }
        }

        SLOWED_PROJECTILES.clear();
    }

    private static void restoreProjectile(
            UUID projectileId,
            Projectile projectile,
            ServerLevel level
    ) {
        ProjectileState state = SLOWED_PROJECTILES.remove(
                projectileId
        );

        if (state != null && !projectile.isRemoved()) {
            projectile.setNoGravity(state.wasNoGravity());
            projectile.setDeltaMovement(state.fullVelocity());
            level.getChunkSource().broadcastAndSend(
                    projectile,
                    new ClientboundSetEntityMotionPacket(projectile)
            );
        }
    }

    private static void playEndSound(
            ServerLevel level,
            TimeSlowField field,
            ServerPlayer owner
    ) {
        // The owner receives a direct sound packet, which prevents the end
        // cue from being lost if they have moved away from the field center.
        owner.playNotifySound(
                ModSounds.TIME_SLOW_STOP.get(),
                SoundSource.PLAYERS,
                0.90F,
                1.0F
        );

        // Everyone else close to the field still hears the same cue once.
        level.playSound(
                owner,
                field.center().x,
                field.center().y,
                field.center().z,
                ModSounds.TIME_SLOW_STOP.get(),
                SoundSource.PLAYERS,
                0.90F,
                1.0F
        );
    }

    private static int getTickIntervalForLevel(int powerLevel) {
        return switch (Math.clamp(powerLevel, 1, 5)) {
            case 1 -> HeroesEvolvedConfig.COMMON
                    .timeSlowLevelOneTickIntervalTicks.get();
            case 2 -> HeroesEvolvedConfig.COMMON
                    .timeSlowLevelTwoTickIntervalTicks.get();
            case 3 -> HeroesEvolvedConfig.COMMON
                    .timeSlowLevelThreeTickIntervalTicks.get();
            case 4 -> HeroesEvolvedConfig.COMMON
                    .timeSlowLevelFourTickIntervalTicks.get();
            case 5 -> HeroesEvolvedConfig.COMMON
                    .timeSlowLevelFiveTickIntervalTicks.get();
            default -> throw new IllegalStateException(
                    "Unexpected Time Slow power level."
            );
        };
    }

    private static final class TimeSlowField {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final Vec3 center;
        private int remainingTicks;
        private final int tickInterval;

        private TimeSlowField(
                UUID ownerId,
                ResourceKey<Level> dimension,
                Vec3 center,
                int remainingTicks,
                int tickInterval
        ) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.center = center;
            this.remainingTicks = remainingTicks;
            this.tickInterval = tickInterval;
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

        private int tickInterval() {
            return tickInterval;
        }
    }

    private static final class ProjectileState {
        private final Projectile projectile;
        private final boolean wasNoGravity;
        private Vec3 fullVelocity;
        private int tickInterval;
        private int movementTicks;
        private boolean velocitySyncRequested = true;

        private ProjectileState(
                Projectile projectile,
                boolean wasNoGravity,
                Vec3 fullVelocity,
                int tickInterval
        ) {
            this.projectile = projectile;
            this.wasNoGravity = wasNoGravity;
            this.fullVelocity = fullVelocity;
            this.tickInterval = tickInterval;
        }

        private Projectile projectile() {
            return projectile;
        }

        private boolean wasNoGravity() {
            return wasNoGravity;
        }

        private Vec3 fullVelocity() {
            return fullVelocity;
        }

        private void advanceMovementTick() {
            movementTicks++;
        }

        private boolean shouldUpdateFullVelocity() {
            return movementTicks >= tickInterval;
        }

        private void updateFullVelocity(Vec3 slowedVelocity) {
            fullVelocity = slowedVelocity.scale(
                    tickInterval
            );
            movementTicks = 0;
            velocitySyncRequested = true;
        }

        private void setTickInterval(int tickInterval) {
            if (this.tickInterval != tickInterval) {
                this.tickInterval = tickInterval;
                movementTicks = 0;
                velocitySyncRequested = true;
            }
        }

        private boolean consumeVelocitySyncRequest() {
            boolean requested = velocitySyncRequested;
            velocitySyncRequested = false;
            return requested;
        }
    }
}
