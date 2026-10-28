package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import java.util.Map;

/**
 * Empathic Mimicry, inspired by Peter Petrelli.
 * <ul>
 *   <li>Level 1, Empathize: absorb the power of the player (or Sylar) you look at.</li>
 *   <li>Level 1, Recall: choose which memory to channel; Shift steps back.</li>
 *   <li>Levels 2, 3 and 5, Echo I/II/III: that power's first three abilities, a level weaker.</li>
 *   <li>Always: near another powered player you regain stamina. Memories persist; one to four by level.</li>
 * </ul>
 */
public final class MimicryAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("mimicry_empathize", 1).cost(() -> 20, () -> 100)
                    .activate(c -> MimicryService.empathize(c.player(), c.abilityLevel())).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("mimicry_recall", 1).cost(() -> 2, () -> 10)
                    .activate(c -> MimicryService.recall(c.player(), 1))
                    .deactivate(c -> MimicryService.recall(c.player(), -1)).build(),
            AbilitySlot.TERTIARY, MimicryService.echo(1, AbilitySlot.PRIMARY, 2),
            AbilitySlot.QUATERNARY, MimicryService.echo(2, AbilitySlot.SECONDARY, 3),
            AbilitySlot.QUINARY, MimicryService.echo(3, AbilitySlot.TERTIARY, 5));
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { MimicryService.forget(c.player()); }
    @Override public void onOverexerted(AbilityUseContext c) { MimicryService.overload(c.player()); }
}
