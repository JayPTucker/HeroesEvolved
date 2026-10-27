package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.expansion.PrecognitionService;
import java.util.Map;

public final class PrecognitionAbility implements Ability {
    private static AbilityAction action(String name, int level, int cost, int cooldown, int slot) {
        return SimpleAction.builder(name, level).cost(() -> cost, () -> cooldown).activate(c -> PrecognitionService.activate(c, slot)).build();
    }
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, action("precog_premonition", 1, 12, 160, 0),
            AbilitySlot.SECONDARY, action("precog_paint", 1, 18, 200, 1),
            AbilitySlot.TERTIARY, action("precog_sever_intent", 2, 24, 160, 2),
            AbilitySlot.QUATERNARY, action("precog_fate_thread", 3, 32, 600, 3),
            AbilitySlot.QUINARY, action("precog_convergence", 5, 40, 500, 4));
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { PrecognitionService.awaken(c.player()); }
    @Override public void onRevoked(AbilityUseContext c) { PrecognitionService.forget(c.player()); }
}
