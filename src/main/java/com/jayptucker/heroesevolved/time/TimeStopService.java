package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
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

    // Hits landed on frozen creatures by the field's owner. Nothing moves
    // while time is stopped, so the force waits and lands all at once.
    private static final Map<UUID, StoredImpact> STORED_IMPACTS = new HashMap<>();
    /** How deep we are in landing stored blows: a blow can kill, and a death can let more go (reentrant). */
    private static int releasingImpacts;

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
        if (releasingImpacts > 0
                || !(target.level() instanceof ServerLevel level)
                || !(source.getEntity() instanceof ServerPlayer attacker)) {
            return false;
        }

        // Still World, Selective Stasis, a Stasis Seal and Moment's Edge all store their holder's blows the same way.
        TimeStopField field = getContainingField(target, level);
        boolean held = field != null && field.ownerId().equals(attacker.getUUID())
                || attacker.getUUID().equals(SpaceTimeService.holder(target));
        if (!held) {
            return false;
        }

        StoredImpact impact = STORED_IMPACTS.computeIfAbsent(
                target.getUUID(),
                ignored -> new StoredImpact(target, attacker.getUUID())
        );
        impact.damage = Math.min(SpaceTimeSkills.storeCap(attacker, target), impact.damage + amount);
        impact.compound = SpaceTimeSkills.has(attacker, SpaceTimeSkills.COMPOUND);
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
        release(server, ownerId, null);
    }

    /** Has this owner stored blows on this creature? */
    public static boolean hasStored(UUID ownerId, Entity target) {
        StoredImpact impact = STORED_IMPACTS.get(target.getUUID());
        return impact != null && impact.ownerId.equals(ownerId);
    }

    /**
     * Lands the blows {@code ownerId} stored: on one creature, or (null) on every creature they no longer hold, as Still
     * World ends. Compound Interest grows them by a tenth for each one stored.
     */
    public static void release(MinecraftServer server, UUID ownerId, Entity only) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        // Take them out first, then land them: a blow can kill, and a death lets go of what the dead held (another release).
        java.util.List<StoredImpact> landing = new java.util.ArrayList<>();
        var iterator = STORED_IMPACTS.values().iterator();
        while (iterator.hasNext()) {
            StoredImpact impact = iterator.next();
            if (!impact.ownerId.equals(ownerId)) {
                continue;
            }
            if (only != null ? impact.target != only : ownerId.equals(SpaceTimeService.holder(impact.target))) {
                continue;
            }
            iterator.remove();
            landing.add(impact);
        }
        releasingImpacts++;
        try {
            for (StoredImpact impact : landing) {
                var target = impact.target;
                if (target.isRemoved() || !target.isAlive()
                        || !(target.level() instanceof ServerLevel level)) {
                    continue;
                }

                target.invulnerableTime = 0;
                target.hurt(owner == null
                        ? level.damageSources().magic()
                        : owner.damageSources().playerAttack(owner),
                        impact.damage * SpaceTimeSkills.compound(impact.compound, impact.hits));
                // The stopped clock round them cracks and its hands race on as every blow lands at once.
                TimeGeometry.show(level, com.jayptucker.heroesevolved.styles.Piece.TIME_RELEASE, target.position(),
                        target.getEyePosition(), impact.hits, target);
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
            releasingImpacts--;
        }
    }

    public static void clear() { ACTIVE_FIELDS.clear(); ProjectileTimeService.releaseInactive(); STORED_IMPACTS.clear(); }

    public static boolean hasActiveField(ServerPlayer player) {
        return ACTIVE_FIELDS.containsKey(player.getUUID());
    }

    public static boolean isStopped(Entity entity) {
        return SpaceTimeService.isSuspended(entity) || entity.level() instanceof ServerLevel level && getContainingField(entity, level) != null;
    }

    public static void start(ServerPlayer player, int powerLevel) { start(player, powerLevel, false); }

    public static void start(ServerPlayer player, int powerLevel, boolean carryCompanion) {
        ServerLevel level = player.serverLevel();
        // Everyone else sees the traveller frozen where they stood, like the rest of the world.
        TimeVeil.begin(player, TimeVeil.Style.TIME, TimeVeil.STOP);

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
        SpaceTimeSkills.stillWorldStarted(player);
    }

    public static void stop(ServerPlayer player) {
        TimeStopField field = ACTIVE_FIELDS.remove(player.getUUID());

        if (field != null) {
            playTemporalCue(player.serverLevel(), field.center(), player);
            releaseImpacts(player.getServer(), player.getUUID());
            ProjectileTimeService.releaseInactive();
            TimeVeil.end(player, TimeVeil.STOP);
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
                if (owner != null) TimeVeil.end(owner, TimeVeil.STOP);
            } else if (level != null && level.getGameTime() % 10 == 0) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("time.heroesevolved.stopped", (field.remainingTicks + 19) / 20), true);
                // The stopped dome stays visible while it lasts.
                if (TimeVeil.serverWide()) TimeVeil.showToSeers(owner, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.TIME_STOP_PULSE,
                        field.center(), field.center().add(0, 1, 0), HeroesEvolvedConfig.COMMON.timeStopRadius.get());
                else com.jayptucker.heroesevolved.effects.AbilityEffects.send(level, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.TIME_STOP_PULSE,
                        field.center(), field.center().add(0, 1, 0), HeroesEvolvedConfig.COMMON.timeStopRadius.get());
                // Stopped time looks drained and cold to anyone inside it (server-wide, TimeVeil tints every screen).
                if (!TimeVeil.serverWide()) com.jayptucker.heroesevolved.network.ScreenTintPayload.sendAround(
                        level, field.center(),
                        HeroesEvolvedConfig.COMMON.timeStopRadius.get(),
                        0x40506A90, 14,
                        com.jayptucker.heroesevolved.network.ScreenTintPayload
                                .Style.HOLD
                );
            }
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

        if (entity instanceof Projectile projectile) return ProjectileTimeService.beforeTick(projectile);
        return getContainingField(entity, level) != null || SpaceTimeService.isSuspended(entity);
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

    static boolean stopsProjectile(Projectile shot, Vec3 end) {
        return getContainingField(shot, (ServerLevel) shot.level(), end) != null;
    }

    static boolean controlsShot(ServerPlayer player, Projectile shot, Vec3 end) {
        TimeStopField field = ACTIVE_FIELDS.get(player.getUUID());
        double radius = HeroesEvolvedConfig.COMMON.timeStopRadius.get();
        return field != null && !field.hasEnded() && field.dimension().equals(shot.level().dimension())
                && ProjectileTimeService.reachesField(shot.position(), end, field.center(), radius);
    }

    private static TimeStopField getContainingField(
            Entity entity,
            ServerLevel level
    ) {
        return getContainingField(entity, level, entity.position());
    }

    private static TimeStopField getContainingField(Entity entity, ServerLevel level, Vec3 end) {
        double radius = HeroesEvolvedConfig.COMMON.timeStopRadius.get();

        for (TimeStopField field : ACTIVE_FIELDS.values()) {
            boolean everywhere = TimeVeil.serverWide();
            if ((everywhere || field.dimension().equals(level.dimension()))
                    && !field.ownerId().equals(entity.getUUID())
                    && !entity.getUUID().equals(field.companion)
                    // Whatever the traveller (or their companion) carries moves with them.
                    && !field.ownerId().equals(entity.getRootVehicle().getUUID())
                    && !entity.getRootVehicle().getUUID().equals(field.companion)
                    && !entity.isSpectator()
                    && !com.jayptucker.heroesevolved.speed.SpeedService.outrunsTime(entity)
                    && !field.hasEnded()
                    && (everywhere || ProjectileTimeService.reachesField(entity.position(), end, field.center(), radius))) {
                return field;
            }
        }

        return null;
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
        /** Compound Interest, read when the blow was stored (the holder may be gone when they land). */
        private boolean compound;
        private Vec3 push = Vec3.ZERO;

        private StoredImpact(
                net.minecraft.world.entity.LivingEntity target,
                UUID ownerId
        ) {
            this.target = target;
            this.ownerId = ownerId;
        }
    }
}
