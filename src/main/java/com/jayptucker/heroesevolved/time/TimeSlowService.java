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
import net.minecraft.world.entity.player.Player;
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
 * server itself. Entities update less often and projectiles move a shorter
 * distance each tick, while the rest of the world stays responsive.</p>
 */
public final class TimeSlowService {
    private static final Map<UUID, TimeSlowField> ACTIVE_FIELDS =
            new HashMap<>();

    private TimeSlowService() {
    }

    public static void clear() { ACTIVE_FIELDS.clear(); ProjectileTimeService.releaseInactive(); }

    public static boolean hasActiveField(ServerPlayer player) {
        return ACTIVE_FIELDS.containsKey(player.getUUID());
    }

    public static boolean reaches(ServerPlayer player) {
        double radius = HeroesEvolvedConfig.COMMON.timeSlowRadius.get();
        return ACTIVE_FIELDS.values().stream().anyMatch(field -> !field.hasEnded() && (TimeVeil.serverWide()
                || field.dimension.equals(player.level().dimension()) && player.position().distanceToSqr(field.center) <= radius * radius));
    }

    /** Every live field, for {@link TimeFlow}: each follows its owner. */
    static void describe(MinecraftServer server, java.util.List<TimeFlow.Field> into) {
        double radius = HeroesEvolvedConfig.COMMON.timeSlowRadius.get();
        for (TimeSlowField field : ACTIVE_FIELDS.values()) {
            if (field.hasEnded()) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(field.ownerId);
            into.add(new TimeFlow.Field(field.ownerId, owner == null ? -1 : owner.getId(), field.dimension, field.center, radius,
                    field.tickInterval, true));
        }
    }

    static int playerSlowInterval(ServerPlayer target) {
        int interval = 1;
        for (TimeSlowField field : ACTIVE_FIELDS.values())
            if (!field.ownerId().equals(target.getUUID()) && !TemporalSharing.movesWith(field.ownerId, target) && !field.hasEnded()
                    && (TimeVeil.serverWide() || field.dimension.equals(target.level().dimension())
                    && target.position().distanceToSqr(field.center) <= Math.pow(HeroesEvolvedConfig.COMMON.timeSlowRadius.get(), 2)))
                interval = Math.max(interval, field.tickInterval());
        return interval;
    }

    /** The id of Between Seconds' stamina drain. */
    public static final String DRAIN = "time_slow";
    private static final int HELD_TICKS = 20 * 60 * 10;

    /**
     * Between Seconds as the key starts it: it lasts while the traveller pays for it ({@link #drainPerSecond}),
     * until the key is pressed again or the stamina runs out, and holds recovery meanwhile. {@link #start} keeps
     * the fixed-length field the awakening reflex uses.
     */
    public static void hold(ServerPlayer player, int powerLevel) {
        start(player, powerLevel, HELD_TICKS, true);
        com.jayptucker.heroesevolved.energy.StaminaDrain.start(player, DRAIN, drainPerSecond(powerLevel), true, () -> {
            if (!hasActiveField(player)) return;
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("time.heroesevolved.slowed_out"), true);
            stop(player);
        });
    }

    /** Stamina a second: 5, a little less as the power grows (4 at Level 15). */
    public static double drainPerSecond(int powerLevel) {
        return com.jayptucker.heroesevolved.ability.PowerBalance.drain(DRAIN, 5) * (1 - .05 * (Math.clamp(powerLevel, 1, 5) - 1));
    }

    public static void stop(ServerPlayer player) {
        com.jayptucker.heroesevolved.energy.StaminaDrain.stop(player, DRAIN);
        TimeSlowField field = ACTIVE_FIELDS.remove(player.getUUID());

        if (field != null) {
            playEndSound(player.serverLevel(), field, player);
            ProjectileTimeService.releaseInactive();
            TimeVeil.end(player, TimeVeil.SLOW);
            TemporalVoiceState.publish(player.getServer());
        }
    }

    public static void start(ServerPlayer player, int powerLevel) {
        start(player, powerLevel, HeroesEvolvedConfig.COMMON.timeSlowDurationSeconds.get() * 20, false);
    }

    private static void start(ServerPlayer player, int powerLevel, int durationTicks, boolean held) {
        ServerLevel level = player.serverLevel();
        // From now on everyone else sees a still figure where the traveller stood (before any effect gives them away).
        TimeVeil.begin(player, TimeVeil.Style.TIME, TimeVeil.SLOW);
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

        com.jayptucker.heroesevolved.effects.AbilityEffects.at(player, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.TEMPORAL_FIELD, HeroesEvolvedConfig.COMMON.timeSlowRadius.get());
        ACTIVE_FIELDS.put(
                player.getUUID(),
                new TimeSlowField(
                        player.getUUID(),
                        level.dimension(),
                        player.position(),
                        durationTicks,
                        tickInterval,
                        held
                )
        );
        TemporalVoiceState.publish(player.getServer());
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

            // Matches Time Stop: an Eclipse suppresses active fields too.
            boolean hasEnded = level == null
                    || owner == null
                    || !owner.isAlive() || owner.level() != level || !SpaceTimeService.awakened(owner)
                    || com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(owner)
                    || EclipseService.arePowersSuppressed(owner)
                    || field.advanceTimer();

            if (hasEnded) {
                iterator.remove();

                if (level != null && owner != null) {
                    playEndSound(level, field, owner);
                }
                if (owner != null) {
                    TimeVeil.end(owner, TimeVeil.SLOW);
                    com.jayptucker.heroesevolved.energy.StaminaDrain.stop(owner, DRAIN);
                }
            } else {
                field.center = owner.position();
                if (level.getGameTime() % 10 != 0) continue;
                // The bubble stays visible while it lasts.
                if (TimeVeil.serverWide()) TimeVeil.showToSeers(owner, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.TIME_SLOW_PULSE,
                        field.center(), field.center().add(0, 1, 0), HeroesEvolvedConfig.COMMON.timeSlowRadius.get());
                else com.jayptucker.heroesevolved.effects.AbilityEffects.send(level, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.TIME_SLOW_PULSE,
                        field.center(), field.center().add(0, 1, 0), HeroesEvolvedConfig.COMMON.timeSlowRadius.get());
                if (field.held) {
                    double rate = com.jayptucker.heroesevolved.energy.StaminaDrain.rate(owner);
                    int seconds = rate <= 0 ? 0 : (int) (com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(owner) / rate);
                    owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("time.heroesevolved.slowed_held",
                            String.format("%.0f", rate), seconds), true);
                } else owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("time.heroesevolved.slowed", (field.remainingTicks + 19) / 20), true);
                // Slowed time takes on a faded, sepia warmth (server-wide, TimeVeil tints every screen instead).
                if (!TimeVeil.serverWide()) com.jayptucker.heroesevolved.network.ScreenTintPayload.sendAround(
                        level, field.center(),
                        HeroesEvolvedConfig.COMMON.timeSlowRadius.get(),
                        0x30806030, 14,
                        com.jayptucker.heroesevolved.network.ScreenTintPayload
                                .Style.HOLD
                );
            }
        }

    }

    /**
     * Returns true on all but one tick in the field's level-based interval.
     * Cancelling those entity ticks slows AI, falling blocks, item entities,
     * and other non-projectile entities at the field's current mastery level.
     */
    public static int mobInterval(Entity entity) {
        TimeSlowField field = getContainingField(entity, entity.position());
        return field == null ? 1 : field.tickInterval();
    }

    public static boolean shouldSkipEntityTick(Entity entity) {
        if (entity instanceof Player || entity instanceof Projectile || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        TimeSlowField field = getContainingField(entity, entity.position());

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

    static int projectileInterval(Projectile shot, Vec3 end) {
        TimeSlowField field = getContainingField(shot, end);
        return field == null ? ProjectileTimeService.NORMAL : field.tickInterval();
    }

    static boolean controlsShot(ServerPlayer player, Projectile shot, Vec3 end) {
        TimeSlowField field = ACTIVE_FIELDS.get(player.getUUID());
        double radius = HeroesEvolvedConfig.COMMON.timeSlowRadius.get();
        return field != null && !field.hasEnded() && field.dimension().equals(shot.level().dimension())
                && ProjectileTimeService.reachesField(shot.position(), end, field.center(), radius);
    }

    private static TimeSlowField getContainingField(Entity entity, Vec3 end) {
        double radius = HeroesEvolvedConfig.COMMON.timeSlowRadius.get();
        TimeSlowField strongestField = null;

        for (TimeSlowField field : ACTIVE_FIELDS.values()) {
            boolean everywhere = TimeVeil.serverWide();
            if (!everywhere && !field.dimension().equals(entity.level().dimension())
                    || field.ownerId().equals(entity.getUUID()) || entity.isSpectator()
                    || TemporalSharing.movesWith(field.ownerId, entity)
                    || TemporalMobTime.perceives(entity)
                    // Whatever the traveller carries moves with them.
                    || field.ownerId().equals(entity.getRootVehicle().getUUID())
                    || com.jayptucker.heroesevolved.speed.SpeedService.outrunsTime(entity)
                    || field.hasEnded()) {
                continue;
            }

            if (everywhere || ProjectileTimeService.reachesField(entity.position(), end, field.center(), radius)) {
                if (strongestField == null
                        || field.tickInterval()
                        > strongestField.tickInterval()) {
                    strongestField = field;
                }
            }
        }

        return strongestField;
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
        private Vec3 center;
        private int remainingTicks;
        private final int tickInterval;
        private final boolean held;

        private TimeSlowField(
                UUID ownerId,
                ResourceKey<Level> dimension,
                Vec3 center,
                int remainingTicks,
                int tickInterval,
                boolean held
        ) {
            this.held = held;
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
}
