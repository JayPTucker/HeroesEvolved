package com.jayptucker.heroesevolved.ability;

public interface Ability {
    AbilityDefinition definition();

    default int energyCost(int level) {
        return definition().energyCostForLevel(level);
    }

    default int cooldownTicks(int level) {
        return definition().cooldownTicksForLevel(level);
    }

    boolean canUse(AbilityUseContext context);

    AbilityActivationResult activate(AbilityUseContext context);

    default void deactivate(AbilityUseContext context) {

    }

    default void tick(AbilityUseContext context) {
        
    }
}
