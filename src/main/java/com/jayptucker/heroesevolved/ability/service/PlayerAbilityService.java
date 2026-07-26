package com.jayptucker.heroesevolved.ability.service;

import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
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
        if (data.hasAbility(abilityId)) {
            return false;
        }

        player.setData(
            ModDataAttachments.PLAYER_ABILITIES.get(),
            data.assignDormant(abilityId)
        );

        return true;
    }

    public static boolean grantAbility(
        ServerPlayer player,
        ResourceLocation abilityId
    ) {
        validateRegisteredAbility(abilityId);

        PlayerAbilityData data = getData(player);

        if (!data.hasAbility(abilityId)) {
            player.setData(
                ModDataAttachments.PLAYER_ABILITIES.get(),
                data.assignDormant(abilityId).unlock(abilityId)
            );

            return true;
        }

        return unlockAbility(player, abilityId);
    }
    public static boolean unlockAbility(
        ServerPlayer player,
        ResourceLocation abilityId
    ) {
        PlayerAbilityData data = getData(player);
        Optional<AbilityProgress> progress = data.ability(abilityId);

        if (progress.isEmpty() || progress.get().isUnlocked()) {
            return false;
        }

        player.setData(
            ModDataAttachments.PLAYER_ABILITIES.get(),
            data.unlock(abilityId)
        );

        return true;
    }

    private static void validateRegisteredAbility(ResourceLocation abilityId) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        if (!AbilityRegistry.ABILITIES.containsKey(abilityId)) {
            throw new IllegalArgumentException("Unknown ability: " + abilityId);
        }
    }
}
