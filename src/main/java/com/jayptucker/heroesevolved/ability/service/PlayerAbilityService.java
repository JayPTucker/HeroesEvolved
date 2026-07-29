package com.jayptucker.heroesevolved.ability.service;

import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

public final class PlayerAbilityService {
    private PlayerAbilityService() {
    }

    public static PlayerAbilityData getData(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null.");

        return player.getData(ModDataAttachments.PLAYER_ABILITIES.get());
    }

    public static Optional<AbilityProgress> getAbility(
            ServerPlayer player,
            ResourceLocation abilityId
    ) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        return getData(player).ability(abilityId);
    }

    public static boolean assignDormantAbility(
            ServerPlayer player,
            ResourceLocation abilityId
    ) {
        validateRegisteredAbility(abilityId);

        PlayerAbilityData data = getData(player);

        if (data.hasAssignedPower()) {
            return false;
        }

        saveData(player, data.assignDormant(abilityId));
        return true;
    }

    public static boolean grantAbility(
            ServerPlayer player,
            ResourceLocation abilityId
    ) {
        validateRegisteredAbility(abilityId);

        PlayerAbilityData data = getData(player);

        if (data.hasAssignedPower()) {
            return false;
        }

        saveData(
                player,
                data.assignDormant(abilityId).unlock(abilityId)
        );

        return true;
    }

    public static boolean unlockAbility(
            ServerPlayer player,
            ResourceLocation abilityId
    ) {
        validateRegisteredAbility(abilityId);

        PlayerAbilityData data = getData(player);
        Optional<AbilityProgress> progress = data.ability(abilityId);

        if (progress.isEmpty() || progress.get().isUnlocked()) {
            return false;
        }

        saveData(player, data.unlock(abilityId));
        return true;
    }

    public static boolean replaceWithAbility(
            ServerPlayer player,
            ResourceLocation abilityId
    ) {
        validateRegisteredAbility(abilityId);

        PlayerAbilityData data = getData(player);

        if (data.hasAbility(abilityId)
                && data.ability(abilityId)
                .map(AbilityProgress::isUnlocked)
                .orElse(false)) {
            return false;
        }

        saveData(player, data.replaceWithUnlockedPower(abilityId));
        return true;
    }

    public static boolean removeAssignedAbility(ServerPlayer player) {
        PlayerAbilityData data = getData(player);

        return data.assignedPower().map(assignment -> {
            Ability ability = AbilityRegistry.ABILITIES.get(assignment.getKey());

            if (ability != null) {
                ability.onRevoked(new AbilityUseContext(
                        player,
                        assignment.getKey(),
                        assignment.getValue().level()
                ));
            }

            saveData(player, data.clearAssignedPower());
            return true;
        }).orElse(false);
    }

    private static void saveData(
            ServerPlayer player,
            PlayerAbilityData data
    ) {
        player.setData(
                ModDataAttachments.PLAYER_ABILITIES.get(),
                data
        );

        PlayerPowerSyncService.sync(player);
    }

    private static void validateRegisteredAbility(ResourceLocation abilityId) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        if (!AbilityRegistry.ABILITIES.containsKey(abilityId)) {
            throw new IllegalArgumentException("Unknown ability: " + abilityId);
        }
    }
}
