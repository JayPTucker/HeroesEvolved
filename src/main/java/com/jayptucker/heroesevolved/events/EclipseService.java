package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.EclipseCalendar;
import com.jayptucker.heroesevolved.data.EclipseSavedData;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import com.jayptucker.heroesevolved.data.EclipseTimeline.Phase;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import com.jayptucker.heroesevolved.network.EclipseVisualSyncService;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Comparator;
import java.util.List;

/**
 * Coordinates Eclipse timing, dormant-power rolls, and global announcements.
 *
 * <p>An Eclipse falls on every {@link EclipseCalendar#intervalDays() Nth} day with its peak at
 * noon. The moon slides onto the sun (approach), covers it completely (totality), then slides
 * off (departure). Dormant powers are handed out at totality, and only totality suppresses
 * powers. The first power a world ever sees is always Space-Time Manipulation, given to one
 * player chosen at random from everyone online at that totality; every later power is random.
 */
public final class EclipseService {
    private static final ResourceLocation SOMETHING_FEELS_DIFFERENT_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "something_feels_different"
            );

    private EclipseService() {
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        EclipseSavedData eclipseData = EclipseSavedData.get(level);
        EclipseCalendar.setIntervalDays(HeroesEvolvedConfig.COMMON.eclipseIntervalDays.get());
        long gameTime = level.getGameTime();

        if (eclipseData.isActive()) {
            Phase phase = eclipseData.timeline().phaseAt(gameTime);

            if (phase == Phase.NONE) {
                end(server);
            } else if (phase != eclipseData.announced()) {
                enterPhase(server, eclipseData, phase);
            }
            return;
        }

        long dayTime = level.getDayTime();
        long day = EclipseCalendar.day(dayTime);
        eclipseData.rewindCalendar(day);

        if (!EclipseCalendar.isEclipseDay(day) || eclipseData.lastEclipseDay() == day) {
            return;
        }

        EclipseTimeline planned = configuredTimeline(0);
        long sinceFirstContact = EclipseCalendar.timeOfDay(dayTime)
                - EclipseCalendar.firstContactTimeOfDay(planned.approachTicks(), planned.totalityTicks());

        // A server that comes up mid-Eclipse joins it in progress, so the peak still lands at noon.
        if (sinceFirstContact >= 0 && sinceFirstContact < planned.endGameTime()) {
            begin(server, configuredTimeline(gameTime - sinceFirstContact), day);
        }
    }

    /** Starts a full Eclipse now, from first contact. */
    public static boolean start(MinecraftServer server) {
        if (isActive(server)) {
            return false;
        }

        ServerLevel level = server.overworld();
        begin(server, configuredTimeline(level.getGameTime()), EclipseCalendar.day(level.getDayTime()));
        return true;
    }

    /** Jumps straight to totality, starting an Eclipse if needed. Used by admins and tests. */
    public static boolean beginTotality(MinecraftServer server) {
        ServerLevel level = server.overworld();
        EclipseSavedData eclipseData = EclipseSavedData.get(level);

        if (phase(server) == Phase.TOTALITY) {
            return false;
        }

        EclipseTimeline timeline = configuredTimeline(
                level.getGameTime() - HeroesEvolvedConfig.COMMON.eclipseApproachTicks.get());

        if (eclipseData.isActive()) {
            eclipseData.retime(timeline);
        } else {
            begin(server, timeline, EclipseCalendar.day(level.getDayTime()));
        }

        enterPhase(server, eclipseData, Phase.TOTALITY);
        return true;
    }

    public static boolean end(MinecraftServer server) {
        EclipseSavedData eclipseData = EclipseSavedData.get(server.overworld());

        if (!eclipseData.isActive()) {
            return false;
        }

        eclipseData.end();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("The eclipse fades from the sky."),
                false
        );
        EclipseVisualSyncService.syncToAll(server);
        return true;
    }

    public static boolean isActive(MinecraftServer server) {
        return EclipseSavedData.get(server.overworld()).isActive();
    }

    public static EclipseTimeline timeline(MinecraftServer server) {
        return EclipseSavedData.get(server.overworld()).timeline();
    }

    public static Phase phase(MinecraftServer server) {
        return timeline(server).phaseAt(server.overworld().getGameTime());
    }

    public static boolean isTotality(MinecraftServer server) {
        return phase(server) == Phase.TOTALITY;
    }

    /** Only the black sun silences powers; the partial phases before and after do not. */
    public static boolean arePowersSuppressed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && isTotality(server);
    }

    /** A one-line status for admins: the current phase, or when the next Eclipse falls. */
    public static Component describe(MinecraftServer server) {
        ServerLevel level = server.overworld();
        EclipseTimeline timeline = timeline(server);
        long now = level.getGameTime();

        return switch (timeline.phaseAt(now)) {
            case APPROACH -> Component.literal("Eclipse approaching totality: " + seconds(timeline.totalityStart() - now) + " left.");
            case TOTALITY -> Component.literal("Totality: powers suppressed for " + seconds(timeline.totalityEnd() - now) + ".");
            case DEPARTURE -> Component.literal("Eclipse departing: " + seconds(timeline.endGameTime() - now) + " left.");
            case NONE -> {
                long today = EclipseCalendar.day(level.getDayTime());
                EclipseSavedData eclipseData = EclipseSavedData.get(level);
                long next = EclipseCalendar.nextEclipseDay(eclipseData.lastEclipseDay() == today ? today + 1 : today);
                yield Component.literal("No Eclipse is active. Next Eclipse: day " + (next + 1)
                        + (next == today ? " (today, totality at noon)." : " (in " + (next - today) + " days, totality at noon)."));
            }
        };
    }

    /**
     * True until the world's first power is handed out. That one is always Space-Time
     * Manipulation, and it skips the usual chance roll.
     */
    public static boolean firstPowerPending(MinecraftServer server) {
        return PowerOwnershipSavedData.get(server).noneClaimed();
    }

    /**
     * Totality's hand-out. While the world has no power yet, one player chosen at random from
     * {@code online} receives Space-Time Manipulation first; then everyone gets their usual roll.
     */
    public static void handOutPowers(MinecraftServer server, List<ServerPlayer> online) {
        if (firstPowerPending(server)) {
            List<ServerPlayer> eligible = online.stream().filter(player -> canRoll(server, player)).toList();

            if (!eligible.isEmpty()) {
                tryAssignDormantAbility(eligible.get(server.overworld().getRandom().nextInt(eligible.size())));
            }
        }

        for (ServerPlayer player : online) {
            tryAssignDormantAbility(player);
        }
    }

    /** Powerless, and has not rolled during this Eclipse yet. */
    private static boolean canRoll(MinecraftServer server, ServerPlayer player) {
        return !PlayerAbilityService.getData(player).hasAssignedPower()
                && player.getData(ModDataAttachments.PLAYER_LAST_ECLIPSE_ROLL.get())
                != EclipseSavedData.get(server.overworld()).eclipseId();
    }

    /** Gives an eligible player one roll for the current totality. */
    public static boolean tryAssignDormantAbility(ServerPlayer player) {
        MinecraftServer server = player.getServer();

        if (server == null || !isTotality(server) || !canRoll(server, player)) {
            return false;
        }

        // Record the attempt before rolling so reconnecting cannot reroll.
        player.setData(
                ModDataAttachments.PLAYER_LAST_ECLIPSE_ROLL.get(),
                EclipseSavedData.get(server.overworld()).eclipseId()
        );

        ResourceLocation abilityId;

        if (firstPowerPending(server)) {
            abilityId = ModAbilities.TIME_MANIPULATION_ID;
        } else {
            if (server.overworld().getRandom().nextDouble()
                    >= HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.get()) {
                return false;
            }

            List<ResourceLocation> abilityIds = AbilityRegistry.ABILITIES.keySet()
                    .stream()
                    .filter(power -> PlayerAbilityService.availableTo(player, power))
                    .filter(com.jayptucker.heroesevolved.ability.registry.ModAbilities::rollable)
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .toList();

            if (abilityIds.isEmpty()) {
                HeroesEvolved.LOGGER.warn(
                        "Skipped Eclipse ability assignment because no unclaimed abilities remain."
                );
                return false;
            }

            abilityId = abilityIds.get(
                    server.overworld().getRandom().nextInt(abilityIds.size())
            );
        }

        if (!PlayerAbilityService.assignDormantAbility(player, abilityId)) {
            return false;
        }

        // The chosen feel it; nobody is told what it is, or that it waits for a fight.
        player.sendSystemMessage(Component.literal("Something in your blood answers the dark.")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.8F);
        awardSomethingFeelsDifferent(server, player);
        return true;
    }

    private static EclipseTimeline configuredTimeline(long startGameTime) {
        return new EclipseTimeline(
                startGameTime,
                HeroesEvolvedConfig.COMMON.eclipseApproachTicks.get(),
                HeroesEvolvedConfig.COMMON.eclipseTotalityTicks.get(),
                HeroesEvolvedConfig.COMMON.eclipseDepartureTicks.get()
        );
    }

    private static void begin(MinecraftServer server, EclipseTimeline timeline, long day) {
        EclipseSavedData eclipseData = EclipseSavedData.get(server.overworld());
        eclipseData.begin(timeline, day);
        Phase phase = timeline.phaseAt(server.overworld().getGameTime());

        if (phase != Phase.NONE) {
            enterPhase(server, eclipseData, phase);
        }
    }

    private static void enterPhase(MinecraftServer server, EclipseSavedData eclipseData, Phase phase) {
        eclipseData.announce(phase);

        switch (phase) {
            case APPROACH -> server.getPlayerList().broadcastSystemMessage(
                    Component.literal("The moon begins to slide across the sun..."),
                    false
            );
            case TOTALITY -> {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("Totality. The sun is gone, and every power on Earth falls silent.")
                                .withStyle(ChatFormatting.DARK_AQUA),
                        false
                );

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 1.0F, 0.5F);
                }

                handOutPowers(server, List.copyOf(server.getPlayerList().getPlayers()));
            }
            case DEPARTURE -> {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("A diamond of light breaks the dark. The sun, and your powers, return."),
                        false
                );

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 1.0F, 0.7F);
                }
            }
            case NONE -> {
            }
        }

        EclipseVisualSyncService.syncToAll(server);
    }

    private static String seconds(long ticks) {
        long total = Math.max(0, ticks) / 20;
        return total >= 60 ? total / 60 + "m " + total % 60 + "s" : total + "s";
    }

    private static void awardSomethingFeelsDifferent(
            MinecraftServer server,
            ServerPlayer player
    ) {
        AdvancementHolder advancement = server.getAdvancements().get(
                SOMETHING_FEELS_DIFFERENT_ADVANCEMENT
        );

        if (advancement == null) {
            HeroesEvolved.LOGGER.error(
                    "Could not find Eclipse advancement {}.",
                    SOMETHING_FEELS_DIFFERENT_ADVANCEMENT
            );
            return;
        }

        player.getAdvancements().award(advancement, "granted");
    }
}
