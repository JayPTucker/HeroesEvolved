package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftService;

import java.util.Map;

/**
 * Shapeshifting (James Martin's power, later Sylar's, in Heroes). Phases 1 to 5 of docs/shapeshifting.md: all five
 * core keys. The tree, classes, trial and awakening come in phase 6.
 */
public final class ShapeshiftingAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);
    // Unlock numbers are tiers (1 to 5: Power Level 1, 3, 6, 10 and 15), not Power Levels.
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("shapeshift_morph", 1).cost(() -> 0, () -> 20)
                    .followUp(c -> !c.modifierHeld() && ShapeshiftService.revertOnPress(c.player()))
                    .activate(c -> c.modifierHeld() ? ShapeshiftService.openWheel(c.player()) : ShapeshiftService.study(c.player()))
                    .build(),
            // Each form's own attack; it pays its own stamina and keeps a cooldown per move.
            AbilitySlot.SECONDARY, SimpleAction.builder("shapeshift_instinct", 2).cost(() -> 0, () -> 6)
                    .activate(c -> com.jayptucker.heroesevolved.shapeshift.ShapeshiftInstinct.use(c.player()))
                    .build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("shapeshift_decoy", 3).cost(() -> 16, () -> 200)
                    .activate(c -> com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.decoy(c.player()))
                    .build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("shapeshift_reflection", 4).cost(() -> 18, () -> 240)
                    .activate(c -> com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.reflect(c.player()))
                    .build(),
            AbilitySlot.QUINARY, SimpleAction.builder("shapeshift_nightmare", 5).cost(() -> 40, () -> 600)
                    .activate(c -> com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.nightmare(c.player()))
                    .build());

    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }

    @Override public void onAwaken(AbilityUseContext c) { PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { ShapeshiftService.forget(c.player()); }
}
