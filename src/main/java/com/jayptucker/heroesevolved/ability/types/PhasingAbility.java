package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.phasing.PhasingService;
import java.util.Map;

/**
 * Phasing, inspired by D.L. Hawkins.
 * <ul>
 *   <li>Level 1, Phase Step: walk through the wall ahead. Shift brings a friend.</li>
 *   <li>Level 1, Intangible: toggle; blows, projectiles, falls and blasts pass through you.</li>
 *   <li>Level 2, Phase Strike: reach inside a target, ignoring armor.</li>
 *   <li>Level 3, Phase Drop: sink through the floor, or rise with Shift.</li>
 *   <li>Level 5, Phase Walk: eight seconds walking through walls.</li>
 *   <li>Always: you cannot suffocate in walls, and you never end a phase inside one.</li>
 * </ul>
 */
public final class PhasingAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("phasing_step", 1).cost(() -> 14, () -> 30)
                    .canUse(c -> !c.player().isPassenger())
                    .activate(c -> PhasingService.phaseStep(c.player(), c.abilityLevel(), c.modifierHeld())).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("phasing_intangible", 1).cost(() -> 8, () -> 10)
                    .activate(c -> PhasingService.toggleIntangible(c.player()))
                    .deactivate(c -> PhasingService.solidify(c.player())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("phasing_strike", 2).cost(() -> 20, () -> 80)
                    .activate(c -> PhasingService.phaseStrike(c.player(), c.abilityLevel())).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("phasing_drop", 3).cost(() -> 18, () -> 60)
                    .canUse(c -> !c.player().isPassenger())
                    .activate(c -> PhasingService.phaseDrop(c.player(), c.modifierHeld(), 16, 0)).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("phasing_walk", 5).cost(() -> 70, () -> 20 * 60)
                    .activate(c -> PhasingService.phaseWalk(c.player())).build());
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { PhasingService.forget(c.player()); }
}
