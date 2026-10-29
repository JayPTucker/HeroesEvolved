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

    // Hits landed on frozen creatures by the field's owner. Nothing moves
    // while time is stopped, so the force waits and lands all at once.
    private static final Map<UUID, StoredImpact> STORED_IMPACTS = new HashMap<>();
    private static boolean releasingImpacts;

    private TimeStopService() {
    }

    /**
     * Holds damage the field's owner deals to a frozen creature until time
     * resumes, the way Hiro strikes a frozen opponent and every blow lands
     * the instant time restarts.
     *
     * @return true if the damage was stored and must not apply now
     */
    public static boolean storeImpact(
            net.minecraft.world.entity.LivingEntity target,
            net.minecraft.world.damagesource.DamageSource source,
            float amount
    ) {
        if (releasingImpacts
                || !(target.level() instanceof ServerLevel level)
                || !(source.getEntity() instanceof ServerPlayer attacker)) {
            return false;
        }

        TimeStopField field = getContainingField(target, level);
        if (field == null || !field.ownerId().equals(attacker.getUUID())) {
            return false;
        }

        StoredImpact impact = STORED_IMPACTS.computeIfAbsent(
                target.getUUID(),
                ignored -> new StoredImpact(target, attacker.getUUID())
        );
        impact.damage = Math.min(target instanceof ServerPlayer ? 12 : 40, impact.damage + amount);
        impact.hits = Math.min(8, impact.hits + 1);
        impact.push = impact.push.add(attacker.getLookAngle().scale(0.35D));

        // A frozen ripple marks each blow waiting to land.
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.6D,
                target.getZ(), Math.min(20, 6 + impact.hits * 2), 0.25D, 0.3D, 0.25D, 0.0D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
                SoundSource.PLAYERS, 0.7F, 0.5F + impact.hits * 0.1F);
        return true;
    }

    private static void releaseImpacts(MinecraftServer server, UUID ownerId) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        releasingImpacts = true;
        try {
            var iterator = STORED_IMPACTS.values().iterator();
            while (iterator.hasNext()) {
                StoredImpact impact = iterator.next();
                if (!impact.ownerId.equals(ownerId)) {
                    continue;
                }
                iterator.remove();

                var target = impact.target;
                if (target.isRemoved() || !target.isAlive()
                        || !(target.level() instanceof ServerLevel level)) {
                    continue;
                }

                target.invulnerableTime = 0;
                target.hurt(owner == null
                        ? level.damageSources().magic()
                        : owner.damageSources().playerAttack(owner),
                        impact.damage);
                double pushLength = Math.min(3.0D, impact.push.length());
                Vec3 push = impact.push.lengthSqr() < 0.0001D
                        ? Vec3.ZERO
                        : impact.push.normalize().scale(pushLength);
                target.setDeltaMovement(push.add(0.0D, 0.2D + pushLength * 0.1D, 0.0D));
                target.hurtMarked = true;

                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.6D,
                        target.getZ(), Math.min(40, 10 + impact.hits * 4),
                        0.3D, 0.4D, 0.3D, 0.4D);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                        target.getX(), target.getY() + target.getBbHeight() * 0.6D,
                        target.getZ(), Math.min(6, impact.hits), 0.3D, 0.3D, 0.3D, 0.0D);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS, 1.0F, 0.8F);
            }
        } finally {
            releasingImpacts = false;
        }
    }

    public static void clear() { ACTIVE_FIELDS.clear(); restoreAllProjectiles(); STORED_IMPACTS.clear(); }

    public static boolean hasActiveField(ServerPlayer player) {
        return ACTIVE_FIELDS.containsKey(player.getUUID());
    }

    public static boolean isStopped(Entity entity) {
        return SpaceTimeService.isSuspended(entity) || entity.level() instanceof ServerLevel level && getContainingField(entity, level) != null;
    }

    public static void start(ServerPlayer player, int powerLevel) { start(player, powerLevel, false); }

    public static void start(ServerPlayer player, int powerLevel, boolean carryCompanion) {
        ServerLevel level = player.serverLevel();

        playTemporalCue(level, player.position(), player);

        UUID companion = carryCompanion ? level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(1), e -> e != player && e.isAlive() && !e.isSpectator()
                        && !(e instanceof com.jayptucker.heroesevolved.entity.TemporalEchoEntity)
                        && !TemporalEntityEchoService.isReplaying(e)
                        && (!(e instanceof ServerPlayer other) || player.isAlliedTo(other) || other.isShiftKeyDown()))
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .map(Entity::getUUID).orElse(null) : null;
        if (companion != null) TemporalStoryService.reach(player, TemporalStoryService.RESCUE);
        com.jayptucker.heroesevolved.effects.AbilityEffects.at(player,
                com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.TEMPORAL_FIELD,
                HeroesEvolvedConfig.COMMON.timeStopRadius.get());
        ACTIVE_FIELDS.put(
                player.getUUID(),
                new TimeStopField(
                        player.getUUID(),
                        level.dimension(),
                        player.position(),
                        durationSecondsForLevel(powerLevel) * 20, companion
                )
        );
    }

    public static void stop(ServerPlayer player) {
        TimeStopField field = ACTIVE_FIELDS.remove(player.getUUID());

        if (field != null) {
            playTemporalCue(player.serverLevel(), field.center(), player);
            releaseImpacts(player.getServer(), player.getUUID());
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
                    || !owner.isAlive() || owner.level() != level || !SpaceTimeService.awakened(owner)
                    || com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(owner)
                    || EclipseService.arePowersSuppressed(owner)
                    || field.advanceTimer();

            if (hasEnded) {
                iterator.remove();

                if (level != null && owner != null) {
                    playTemporalCue(level, field.center(), owner);
                }
                releaseImpacts(server, field.ownerId());
            } else if (level != null && level.getGameTime() % 10 == 0) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("time.heroesevolved.stopped", (field.remainingTicks + 19) / 20), true);
                // The stopped dome stays visible while it lasts.
                com.jayptucker.heroesevolved.effects.AbilityEffects.send(level, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.TIME_STOP_PULSE,
                        field.center(), field.center().add(0, 1, 0), HeroesEvolvedConfig.COMMON.timeStopRadius.get());
                // Stopped time looks drained and cold to anyone inside it.
                com.jayptucker.heroesevolved.network.ScreenTintPayload.sendAround(
                        level, field.center(),
                        HeroesEvolvedConfig.COMMON.timeStopRadius.get(),
                        0x40506A90, 14,
                        com.jayptucker.heroesevolved.network.ScreenTintPayload
                                .Style.HOLD
                );
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

        return field != null || SpaceTimeService.isSuspended(entity);
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
            case 3 -> Math.min(HeroesEvolvedConfig.COMMON
                    .timeStopLevelThreeDurationSeconds.get(), 6);
            case 4 -> Math.min(HeroesEvolvedConfig.COMMON
                    .timeStopLevelFourDurationSeconds.get(), 8);
            case 5 -> Math.min(HeroesEvolvedConfig.COMMON
                    .timeStopLevelFiveDurationSeconds.get(), 10);
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
                    && !entity.getUUID().equals(field.companion)
                    && !entity.isSpectator()
                    && !com.jayptucker.heroesevolved.speed.SpeedService.outrunsTime(entity)
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
        private final UUID companion;

        private TimeStopField(
                UUID ownerId,
                ResourceKey<Level> dimension,
                Vec3 center,
                int remainingTicks, UUID companion
        ) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.center = center;
            this.remainingTicks = remainingTicks;
            this.companion = companion;
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

    private static final class StoredImpact {
        private final net.minecraft.world.entity.LivingEntity target;
        private final UUID ownerId;
        private float damage;
        private int hits;
        private Vec3 push = Vec3.ZERO;

        private StoredImpact(
                net.minecraft.world.entity.LivingEntity target,
                UUID ownerId
        ) {
            this.target = target;
            this.ownerId = ownerId;
        }
    }

    private record StoppedProjectile(
            Projectile projectile,
            boolean wasNoGravity,
            Vec3 velocity
    ) {
    }
}
