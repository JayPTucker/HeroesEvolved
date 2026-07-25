package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;

public final class ProgressionCalculator {
    private ProgressionCalculator() {

    }

    public static int levelFor(long mastery) {
        if (mastery < 0) {
            throw new IllegalArgumentException("Mastery cannot be negative.");
        }

        int maximumLevel = HeroesEvolvedConfig.COMMON.maximumLevel.get();
        long requiredMastery = 0;

        for (int level = 1; level < maximumLevel; level++) {
            requiredMastery = saturatingAdd(
                requiredMastery,
                masteryCostForNextLevel(level)
            );

            if (mastery < requiredMastery) {
                return level;
            }
        }

        return maximumLevel;
    }

    public static long masteryRequiredForLevel(int level) {
        int maximumLevel = HeroesEvolvedConfig.COMMON.maximumLevel.get();

        if (level < 1 || level > maximumLevel) {
            throw new IllegalArgumentException(
                "Level must be between 1 and " + maximumLevel + "."
            );
        }

        long requiredMastery = 0;

        for (int currentLevel = 1; currentLevel < level; currentLevel++) {
            requiredMastery = saturatingAdd(
                    requiredMastery,
                    masteryCostForNextLevel(currentLevel)
            );
        }

        return requiredMastery;
    }

    public static long masteryToNextLevel(long mastery) {
        int level = levelFor(mastery);
        int maximumLevel = HeroesEvolvedConfig.COMMON.maximumLevel.get();

        if (level >= maximumLevel) {
            return 0;
        }

        return masteryRequiredForLevel(level + 1) - mastery;
    }

    private static long masteryCostForNextLevel(int currentLevel) {
        long baseCost = HeroesEvolvedConfig.COMMON.baseMasteryPerLevel.get();
        long increase = HeroesEvolvedConfig.COMMON.masteryIncreasePerLevel.get();

        return saturatingAdd(
                baseCost,
                saturatingMultiply(increase, currentLevel - 1L)
        );
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }

        return left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left == 0 || right == 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }

        return left * right;
    }
}
