package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.EclipseSavedData;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.EclipseVisualSyncService;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/** Coordinates Eclipse timing, dormant-power rolls, and global announcements. */
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
        long gameTime = level.getGameTime();

        eclipseData.scheduleFirstEclipse(
                gameTime,
                HeroesEvolvedConfig.COMMON.eclipseIntervalTicks.get()
        );

        if (eclipseData.isActive()) {
            if (gameTime >= eclipseData.endGameTime()) {
                end(server);
            }
            return;
        }

        if (gameTime >= eclipseData.nextStartGameTime()) {
            start(server);
        }
    }

    public static boolean start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        EclipseSavedData eclipseData = EclipseSavedData.get(level);

        if (eclipseData.isActive()) {
            return false;
        }

        eclipseData.begin(
                level.getGameTime(),
                HeroesEvolvedConfig.COMMON.eclipseDurationTicks.get(),
                HeroesEvolvedConfig.COMMON.eclipseIntervalTicks.get()
        );

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("The sky darkens as an eclipse begins..."),
                false
        );
        EclipseVisualSyncService.syncToAll(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tryAssignDormantAbility(player);
        }

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

    public static boolean arePowersSuppressed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && isActive(server);
    }

    /** Gives an eligible player one roll for the active Eclipse. */
    public static boolean tryAssignDormantAbility(ServerPlayer player) {
        MinecraftServer server = player.getServer();

        if (server == null || !isActive(server)) {
            return false;
        }

        if (PlayerAbilityService.getData(player).hasAssignedPower()) {
            return false;
        }

        EclipseSavedData eclipseData = EclipseSavedData.get(server.overworld());

        if (player.getData(ModDataAttachments.PLAYER_LAST_ECLIPSE_ROLL.get())
                == eclipseData.eclipseId()) {
            return false;
        }

        // Record the attempt before rolling so reconnecting cannot reroll.
        player.setData(
                ModDataAttachments.PLAYER_LAST_ECLIPSE_ROLL.get(),
                eclipseData.eclipseId()
        );

        if (server.overworld().getRandom().nextDouble()
                >= HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.get()) {
            return false;
        }

        List<ResourceLocation> abilityIds = AbilityRegistry.ABILITIES.keySet()
                .stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();

        if (abilityIds.isEmpty()) {
            HeroesEvolved.LOGGER.warn(
                    "Skipped Eclipse ability assignment because no abilities are registered."
            );
            return false;
        }

        ResourceLocation abilityId = abilityIds.get(
                server.overworld().getRandom().nextInt(abilityIds.size())
        );

        if (!PlayerAbilityService.assignDormantAbility(player, abilityId)) {
            return false;
        }

        awardSomethingFeelsDifferent(server, player);
        return true;
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
