package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import java.util.Map;

/**
 * Empathic Mimicry, inspired by Peter Petrelli. Its five keys are Echoes of whichever set is channeled: Echo I to V
 * perform that power's five abilities, one level weaker than its owner's, unlocking at the mimic's own Power Levels
 * 1, 3, 6, 10 and 15. Sets come from being near someone with a power (always) and from touching them (Level 6 on);
 * the memory wheel chooses between them. See {@link MimicryService}.
 */
public final class MimicryAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, MimicryService.echo(1, AbilitySlot.PRIMARY, 1),
            AbilitySlot.SECONDARY, MimicryService.echo(2, AbilitySlot.SECONDARY, 2),
            AbilitySlot.TERTIARY, MimicryService.echo(3, AbilitySlot.TERTIARY, 3),
            AbilitySlot.QUATERNARY, MimicryService.echo(4, AbilitySlot.QUATERNARY, 4),
            AbilitySlot.QUINARY, MimicryService.echo(5, AbilitySlot.QUINARY, 5));
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { MimicryService.forget(c.player()); }
    @Override public void onOverexerted(AbilityUseContext c) { MimicryService.overload(c.player()); }
}
