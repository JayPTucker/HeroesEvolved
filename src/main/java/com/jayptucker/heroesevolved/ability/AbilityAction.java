package com.jayptucker.heroesevolved.ability;

public interface AbilityAction {
    AbilityActionDefinition definition();

    boolean canUse(AbilityUseContext context);

    AbilityActivationResult activate(AbilityUseContext context);

    default int energyCost(int powerLevel) {
        return definition().energyCostForLevel(powerLevel);
    }

    default int cooldownTicks(int powerLevel) {
        return definition().cooldownTicksForLevel(powerLevel);
    }
}