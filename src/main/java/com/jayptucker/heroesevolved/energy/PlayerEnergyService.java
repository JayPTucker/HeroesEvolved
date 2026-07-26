package com.jayptucker.heroesevolved.energy;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.server.level.ServerPlayer;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;

import java.util.Objects;

public final class PlayerEnergyService {
    private PlayerEnergyService() {

    }

    public static int getEnergy(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null.");

        return Math.min(
            player.getData(ModDataAttachments.PLAYER_ENERGY.get()).energy(),
            getMaximumEnergy(player)
        );
    }

    public static int getMaximumEnergy(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null.");

        int level = PlayerProgressionService.getLevel(player);
        int baseEnergy = HeroesEvolvedConfig.COMMON.baseEnergy.get();
        int energyPerLevel = HeroesEvolvedConfig.COMMON.energyPerLevel.get();

        return baseEnergy + ((level - 1) * energyPerLevel);
    }

    public static boolean tryConsume(ServerPlayer player, int amount) {
        validateAmount(amount);

        int currentEnergy = getEnergy(player);
        if (currentEnergy < amount) {
            return false;
        }

        setEnergy(player, currentEnergy - amount);
        return true;
    }

    public static void restore(ServerPlayer player, int amount) {
        validateAmount(amount);

        int restoredEnergy = Math.min(
                getMaximumEnergy(player),
                getEnergy(player) + amount
        );

        if (restoredEnergy == getEnergy(player)) {
            return;
        }

        setEnergy(player, restoredEnergy);
    }

    public static void restoreNaturally(ServerPlayer player) {
        restore(
            player,
            HeroesEvolvedConfig.COMMON.energyRegenerationPerSecond.get()
        );
    }

    private static void setEnergy(ServerPlayer player, int energy) {
        player.setData(
                ModDataAttachments.PLAYER_ENERGY.get(),
                new PlayerEnergyData(energy)
        );

        PlayerPowerSyncService.sync(player);
    }

    private static void validateAmount(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Energy amount cannot be negative.");
        }
    }
}
