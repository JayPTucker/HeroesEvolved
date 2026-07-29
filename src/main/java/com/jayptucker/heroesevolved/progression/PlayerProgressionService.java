package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class PlayerProgressionService {
    private PlayerProgressionService() {
    }

    public static PlayerProgressionData getData(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null.");
        return player.getData(ModDataAttachments.PLAYER_PROGRESSION.get());
    }

    public static int getLevel(ServerPlayer player) {
        return ProgressionCalculator.levelFor(getData(player).mastery());
    }

    public static PlayerProgressionData awardMastery(
            ServerPlayer player,
            long amount
    ) {
        Objects.requireNonNull(player, "Player cannot be null.");

        PlayerProgressionData updatedData = getData(player).addMastery(amount);

        player.setData(
                ModDataAttachments.PLAYER_PROGRESSION.get(),
                updatedData
        );

        return updatedData;
    }

    /**
     * Sets a player's shared power level by storing the exact Mastery
     * threshold for that level. This keeps every system level-driven rather
     * than creating a separate command-only level value.
     */
    public static PlayerProgressionData setLevel(
            ServerPlayer player,
            int level
    ) {
        Objects.requireNonNull(player, "Player cannot be null.");

        long mastery = ProgressionCalculator.masteryRequiredForLevel(level);
        PlayerProgressionData updatedData = new PlayerProgressionData(mastery);

        player.setData(
                ModDataAttachments.PLAYER_PROGRESSION.get(),
                updatedData
        );

        return updatedData;
    }
}
