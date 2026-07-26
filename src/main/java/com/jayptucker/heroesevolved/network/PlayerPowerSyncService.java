package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PlayerPowerSyncService {
    private PlayerPowerSyncService() {
    }

    public static void sync(ServerPlayer player) {
        List<AbilitySnapshot> abilities =
            PlayerAbilityService.getData(player)
                .abilities()
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(
                        entry -> entry.getKey().toString()
                ))
                .map(PlayerPowerSyncService::toSnapshot)
                .toList();

        PacketDistributor.sendToPlayer(
            player,
            new PlayerPowerSyncPayload(
                PlayerEnergyService.getEnergy(player),
                PlayerEnergyService.getMaximumEnergy(player),
                abilities
            )
        );
    }

    private static AbilitySnapshot toSnapshot(
            Map.Entry<net.minecraft.resources.ResourceLocation, AbilityProgress> entry
    ) {
        AbilityProgress progress = entry.getValue();

        return new AbilitySnapshot(
            entry.getKey(),
            progress.isUnlocked(),
            progress.level(),
            progress.mastery()
        );
    }
}