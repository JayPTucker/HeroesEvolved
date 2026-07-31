package com.jayptucker.heroesevolved.ability;

import net.minecraft.resources.ResourceLocation;

public interface AbilityAction {
    AbilityActionDefinition definition();

    boolean canUse(AbilityUseContext context);

    AbilityActivationResult activate(AbilityUseContext context);

    /**
     * Lets toggle-style actions end an existing effect without paying their
     * activation cost or starting a second cooldown. Shift is the shared
     * "end active ability" modifier used by the input layer.
     */
    default boolean deactivate(AbilityUseContext context) {
        return false;
    }

    default int energyCost(int powerLevel) {
        return definition().energyCostForLevel(powerLevel);
    }

    /** Allows actions with alternate inputs to determine their real cost. */
    default int energyCost(AbilityUseContext context) {
        return energyCost(context.abilityLevel());
    }

    default int cooldownTicks(int powerLevel) {
        return definition().cooldownTicksForLevel(powerLevel);
    }

    default int cooldownTicks(AbilityUseContext context) {
        return cooldownTicks(context.abilityLevel());
    }

    /** Alternate input modes can keep independent cooldowns. */
    default ResourceLocation cooldownId(AbilityUseContext context) {
        return definition().id();
    }
}
