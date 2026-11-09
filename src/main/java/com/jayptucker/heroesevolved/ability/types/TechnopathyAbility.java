package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.technopathy.TechnopathyService;
import java.util.Map;

/** A prepared battlefield and a physical machine network, rather than another lightning weapon. */
public final class TechnopathyAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, action("handshake", 1, 8, 20),
            AbilitySlot.SECONDARY, action("remote_hand", 1, 5, 12),
            AbilitySlot.TERTIARY, action("tripwire", 2, 20, 70),
            AbilitySlot.QUATERNARY, action("intercept", 3, 30, 240),
            AbilitySlot.QUINARY, action("machine_city", 5, 85, 1200));

    private static AbilityAction action(String action, int tier, int energy, int cooldown) {
        return SimpleAction.builder("technopathy_" + action, tier).cost(() -> energy, () -> cooldown)
                .canUse(TechnopathyService::canUse)
                .activate(c -> TechnopathyService.core(c, action))
                .deactivate(c -> c.modifierHeld() && TechnopathyService.cancel(c.player(), action)).build();
    }
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }
    @Override public void onRevoked(AbilityUseContext context) { TechnopathyService.forget(context.player()); }
}
