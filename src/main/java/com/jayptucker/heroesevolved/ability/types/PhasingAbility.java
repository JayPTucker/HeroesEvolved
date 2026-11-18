package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.phasing.DensityService;
import com.jayptucker.heroesevolved.phasing.DensitySkills;
import com.jayptucker.heroesevolved.phasing.Earthworks;
import com.jayptucker.heroesevolved.phasing.Stones;
import java.util.Map;

/**
 * Density, reworked as earthbending after Hung Gar (the registry id stays {@code phasing} so saved worlds keep the
 * power, and the action ids stay so loadouts and the balance table still match).
 * <ul>
 *   <li>Level 1, Horse Stance: drop low and set your feet (no knockback, stone skin, heavy fists, Root fills twice as
 *   fast); held, it costs stamina. With Shift, Seismic Stomp: every footstep within 24 blocks, through walls.</li>
 *   <li>Level 1, Rock Strike: a stomp tears a stone out of the ground (hold for a boulder); press again to punch it at
 *   what you look at. Free.</li>
 *   <li>Level 3, Earth Shackle: stone climbs a foe's legs and holds them. Press again for Crush (free).</li>
 *   <li>Level 6, Pillar: a column out of the earth you aim at (at your feet, it lifts you). With Shift, a wall; press
 *   again to shove it (free).</li>
 *   <li>Level 15, Rising Mountain: hold to tear up a disc of ground with everyone on it; let go and it slams down.</li>
 * </ul>
 */
public final class PhasingAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            // While tunnelling, the stance key surfaces you, free.
            AbilitySlot.PRIMARY, SimpleAction.builder("phasing_shift", 1).cost(() -> 4, () -> 6)
                    .activate(c -> DensityService.stanceKey(c.player(), c.modifierHeld()))
                    .followUp(c -> DensityService.sinking(c.player()) && DensityService.surface(c.player(), true)).build(),
            // The punch that throws a lifted rock is free and ignores the cooldown; while tunnelling, Grasp from Below.
            AbilitySlot.SECONDARY, SimpleAction.builder("phasing_reach", 1).cost(() -> 16, () -> 60)
                    .activate(c -> DensityService.sinking(c.player()) ? DensitySkills.grasp(c.player(), c.abilityLevel())
                            : Stones.lift(c.player(), c.abilityLevel()))
                    .followUp(Stones::strike)
                    .release(c -> Stones.release(c.player())).build(),
            // Crush, then (with Entombed) the ground closes over them.
            AbilitySlot.TERTIARY, SimpleAction.builder("phasing_embed", 2).cost(() -> 24, () -> 200)
                    .canUse(c -> !DensityService.sinking(c.player()))
                    .activate(c -> DensityService.shackle(c.player(), c.abilityLevel(), 8))
                    .followUp(DensityService::crush).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("phasing_sink", 3).cost(() -> 20, () -> 40)
                    .canUse(c -> !c.player().isPassenger() && !DensityService.sinking(c.player()))
                    .activate(c -> Earthworks.raise(c.player(), c.abilityLevel(), c.modifierHeld()))
                    .followUp(Earthworks::shove).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("phasing_hollow", 5).cost(() -> 60, () -> 20 * 45)
                    .canUse(c -> !DensityService.sinking(c.player()) && !Earthworks.raising(c.player()))
                    .activate(c -> Earthworks.mountain(c.player(), c.abilityLevel()))
                    .release(c -> Earthworks.releaseMountain(c.player())).build());
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { DensityService.forget(c.player()); }
}
