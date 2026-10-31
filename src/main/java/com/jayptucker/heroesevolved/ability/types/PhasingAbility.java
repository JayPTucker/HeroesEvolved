package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.phasing.DensityService;
import java.util.Map;

/**
 * Density, after D.L. Hawkins (the registry id stays {@code phasing} so saved worlds keep the power).
 * <ul>
 *   <li>Level 1, Shift: one notch toward Ghost; with Shift held, one toward Dense. Holding a notch costs stamina.</li>
 *   <li>Level 1, Reach Through: a hand inside an enemy (armour does nothing, and they cannot heal for a moment),
 *   or through a wall into the chest behind it.</li>
 *   <li>Level 2, Embed: phase an enemy halfway into the floor and make them solid again.</li>
 *   <li>Level 3, Sink: drop into the ground and move through it on your Breath. Press again to surface,
 *   with an uppercut for whoever is standing above you.</li>
 *   <li>Level 5, Hollow Ground: every enemy around you sinks into the ground at once. At Dense, Bedrock Slam
 *   instead: a shockwave, and stone-hard skin.</li>
 *   <li>Always: at Ghost, walk into a wall to pass through it (crouch to lean on it). You cannot suffocate,
 *   and a passage always ends in open space.</li>
 * </ul>
 */
public final class PhasingAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("phasing_shift", 1).cost(() -> 4, () -> 6)
                    .canUse(c -> !DensityService.sinking(c.player()))
                    .activate(c -> DensityService.shift(c.player(), c.modifierHeld())).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("phasing_reach", 1).cost(() -> 16, () -> 60)
                    .activate(c -> DensityService.reach(c.player(), c.abilityLevel())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("phasing_embed", 2).cost(() -> 24, () -> 200)
                    .activate(c -> DensityService.embed(c.player(), c.abilityLevel(), 6)).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("phasing_sink", 3).cost(() -> 20, () -> 40)
                    .canUse(c -> !c.player().isPassenger())
                    .activate(c -> DensityService.sinkOrSurface(c.player(), c.abilityLevel())).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("phasing_hollow", 5).cost(() -> 60, () -> 20 * 45)
                    .canUse(c -> !DensityService.sinking(c.player()))
                    .activate(c -> DensityService.ultimate(c.player(), c.abilityLevel())).build());
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { DensityService.forget(c.player()); }
}
