package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/** Handles the one-time transition from a dormant power to an awakened one. */
public final class AwakeningService {
    private static final ResourceLocation SPECIAL_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "special");

    private AwakeningService() {
    }

    public static boolean tryAwakenAtCriticalHealth(ServerPlayer player) {
        if (player.getHealth() >= HeroesEvolvedConfig.COMMON
                .awakeningHealthThreshold.get()) {
            return false;
        }

        Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignment =
                PlayerAbilityService.getData(player).assignedPower();

        if (assignment.isEmpty() || assignment.get().getValue().isUnlocked()) {
            return false;
        }

        ResourceLocation abilityId = assignment.get().getKey();

        if (!PlayerAbilityService.unlockAbility(player, abilityId)) {
            return false;
        }

        Ability ability = AbilityRegistry.ABILITIES.get(abilityId);

        if (ability != null) {
            ability.onAwaken(new AbilityUseContext(
                    player,
                    abilityId,
                    assignment.get().getValue().level()
            ));
        }

        awardSpecial(player);
        return true;
    }

    private static void awardSpecial(ServerPlayer player) {
        MinecraftServer server = player.getServer();

        if (server == null) {
            return;
        }

        AdvancementHolder advancement = server.getAdvancements().get(
                SPECIAL_ADVANCEMENT
        );

        if (advancement == null) {
            HeroesEvolved.LOGGER.error(
                    "Could not find awakening advancement {}.",
                    SPECIAL_ADVANCEMENT
            );
            return;
        }

        player.getAdvancements().award(advancement, "granted");
    }
}
