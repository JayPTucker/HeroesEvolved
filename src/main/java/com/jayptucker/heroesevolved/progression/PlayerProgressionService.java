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

    /**
     * The power's <b>tier</b>, 1 to 5: what every ability, skill node and damage formula reads as its
     * "level". It is reached at Power Level 1, 3, 6, 10 and 15; see {@link PowerLevels}.
     */
    public static int getLevel(ServerPlayer player) {
        return PowerLevels.tierFor(getPowerLevel(player));
    }

    /** Power Level 1 to 20 (19 until the power's Evolved trial is complete). */
    public static int getPowerLevel(ServerPlayer player) {
        return PowerLevels.levelFor(getData(player).mastery(), evolved(player));
    }

    /** Has the player's assigned power Evolved? */
    public static boolean evolved(ServerPlayer player) {
        return com.jayptucker.heroesevolved.ability.service.PlayerAbilityService.getData(player).assignedPower()
                .map(e -> player.getData(ModDataAttachments.POWER_JOURNEY).isEvolved(e.getKey().getPath())).orElse(false);
    }

    /** Sets the exact Power Level (1 to 20). Setting 20 Evolves the assigned power, skipping its trial. */
    public static PlayerProgressionData setPowerLevel(ServerPlayer player, int level) {
        Objects.requireNonNull(player, "Player cannot be null.");
        if (level >= PowerLevels.EVOLVED) {
            com.jayptucker.heroesevolved.ability.service.PlayerAbilityService.getData(player).assignedPower().ifPresent(e ->
                    player.setData(ModDataAttachments.POWER_JOURNEY,
                            player.getData(ModDataAttachments.POWER_JOURNEY).evolve(e.getKey().getPath())));
        }
        PlayerProgressionData updatedData = new PlayerProgressionData(PowerLevels.masteryFor(level));
        player.setData(ModDataAttachments.PLAYER_PROGRESSION.get(), updatedData);
        rememberSkillLevel(player);
        return updatedData;
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

        rememberSkillLevel(player);

        return updatedData;
    }

    /**
     * Sets a player's power <b>tier</b> (1 to 5) by storing the exact Mastery threshold for the
     * Power Level that reaches it. Use {@link #setPowerLevel} for an exact Power Level.
     */
    public static PlayerProgressionData setLevel(
            ServerPlayer player,
            int level
    ) {
        Objects.requireNonNull(player, "Player cannot be null.");

        long mastery = PowerLevels.masteryFor(PowerLevels.levelForTier(level));
        PlayerProgressionData updatedData = new PlayerProgressionData(mastery);

        player.setData(
                ModDataAttachments.PLAYER_PROGRESSION.get(),
                updatedData
        );

        rememberSkillLevel(player);

        return updatedData;
    }

    private static void rememberSkillLevel(ServerPlayer player) {
        com.jayptucker.heroesevolved.ability.service.PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getValue().isUnlocked())
                .ifPresent(e -> com.jayptucker.heroesevolved.skills.SkillTreeService.build(player, e.getKey()));
    }
}
