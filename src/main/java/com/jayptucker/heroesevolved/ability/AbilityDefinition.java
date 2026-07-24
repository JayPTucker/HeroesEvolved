package com.jayptucker.heroesevolved.ability;

import java.util.Objects;

public record AbilityDefinition(
    int baseEnergyCost,
    int baseCooldownTicks,
    int maxLevel,
    AbilityActivationType activationType

) {
    public AbilityDefinition {
        if (baseEnergyCost < 0) {
            throw new IllegalArgumentException("Base energy cost cannot be negative.");
        }
        if (baseCooldownTicks < 0) {
            throw new IllegalArgumentException("Base cooldown cost cannot be negative.");
        }
        if (maxLevel < 1) {
            throw new IllegalArgumentException("Maximum level must be at least one.");
        }

        Objects.requireNonNull(activationType, "Activation type cannot be null.");
    }

    public int energyCostForLevel(int level) {
        validateLevel(level);
        return baseEnergyCost;
    }

    public int cooldownTicksForLevel(int level) {
        validateLevel(level);
        return baseCooldownTicks;
    }

    private void validateLevel(int level) {
        if (level < 1 || level > maxLevel) {
            throw new IllegalArgumentException(
                "Level must be between 1 and " + maxLevel + ", but was " + level + "."
            );
        }
    }
}
