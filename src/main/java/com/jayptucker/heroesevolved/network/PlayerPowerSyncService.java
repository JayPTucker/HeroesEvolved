package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PlayerPowerSyncService {
    private PlayerPowerSyncService() {
    }

    public static void sync(ServerPlayer player) {
        Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignment =
                PlayerAbilityService.getData(player).assignedPower();

        List<AbilitySnapshot> abilities = assignment
                .map(entry -> List.of(new AbilitySnapshot(
                        entry.getKey(),
                        entry.getValue().isUnlocked(),
                        entry.getValue().level(),
                        entry.getValue().mastery()
                )))
                .orElseGet(List::of);

        List<AbilityActionSnapshot> actions = assignment
                .map(entry -> createActionSnapshots(
                        player,
                        entry.getKey(),
                        entry.getValue()
                ))
                .orElseGet(List::of);

        PacketDistributor.sendToPlayer(
                player,
                new PlayerPowerSyncPayload(
                        PlayerEnergyService.getEnergy(player),
                        PlayerEnergyService.getMaximumEnergy(player),
                        abilities,
                        actions
                )
        );
    }

    private static List<AbilityActionSnapshot> createActionSnapshots(
            ServerPlayer player,
            ResourceLocation powerId,
            AbilityProgress progress
    ) {
        Ability power = AbilityRegistry.ABILITIES.get(powerId);

        if (power == null) {
            return List.of();
        }

        return power.actions()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toSnapshot(
                        player,
                        entry.getKey(),
                        entry.getValue(),
                        progress.level()
                ))
                .toList();
    }

    private static AbilityActionSnapshot toSnapshot(
            ServerPlayer player,
            com.jayptucker.heroesevolved.ability.AbilitySlot slot,
            AbilityAction action,
            int powerLevel
    ) {
        ResourceLocation actionId = action.definition().id();

        return new AbilityActionSnapshot(
                slot,
                action.definition().displayNameKey(),
                action.definition().isUnlockedAt(powerLevel),
                CooldownService.getExpirationGameTime(player, actionId)
        );
    }
}