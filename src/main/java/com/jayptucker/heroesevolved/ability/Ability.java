package com.jayptucker.heroesevolved.ability;
import java.util.Map;
import java.util.Optional;

public interface Ability {
    AbilityDefinition definition();

    default Map<AbilitySlot, AbilityAction> actions() {
        // Regeneration has no manually activated actions yet,
        // so existing powers remain valid without changes.
        return Map.of();
    }

    default Optional<AbilityAction> action(AbilitySlot slot) {
        return Optional.ofNullable(actions().get(slot));
    }

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
