package com.jayptucker.heroesevolved.ability;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AbilityActionDefinition(
        ResourceLocation id,
        String displayNameKey,
        int unlockLevel,
        int baseEnergyCost,
        int baseCooldownTicks
) {
    public AbilityActionDefinition {
        Objects.requireNonNull(id, "Action ID cannot be null.");
        Objects.requireNonNull(
                displayNameKey,
                "Display name key cannot be null."
        );

        if (unlockLevel < 1) {
            throw new IllegalArgumentException(
                    "Unlock level must be at least one."
            );
        }

        if (baseEnergyCost < 0) {
            throw new IllegalArgumentException(
                    "Energy cost cannot be negative."
            );
        }

        if (baseCooldownTicks < 0) {
            throw new IllegalArgumentException(
                    "Cooldown cannot be negative."
            );
        }
    }

    public boolean isUnlockedAt(int powerLevel) {
        return powerLevel >= unlockLevel;
    }

    public int energyCostForLevel(int powerLevel) {
        return baseEnergyCost;
    }

    public int cooldownTicksForLevel(int powerLevel) {
        return baseCooldownTicks;
    }
}