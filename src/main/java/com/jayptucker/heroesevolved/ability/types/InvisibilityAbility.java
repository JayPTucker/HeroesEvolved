package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.invisibility.ForceFieldService;
import com.jayptucker.heroesevolved.invisibility.InvisibilityService;
import java.util.Map;

/**
 * Invisibility: light bent round you, your friends and the things you touch, and light made solid as force fields.
 * <ul>
 *   <li>Level 1, Fade: toggle invisibility; friends within three blocks fade with you.</li>
 *   <li>Level 1, Lens Shield: a curved shield follows your aim; press again for Force Push.</li>
 *   <li>Level 2, Force Bridge: a walkable span of force along your aim.</li>
 *   <li>Level 3, Bubble: hold a foe in a sphere (press again to crush it) or shelter your friends.</li>
 *   <li>Level 5, Force Nova: a telegraphed burst that breaks your constructs into shards, then you vanish.</li>
 * </ul>
 * Action ids are kept from the older kits, so loadouts, balance and config keys carry over.
 */
public final class InvisibilityAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("invisibility_fade", 1).cost(() -> 6, () -> 10)
                    .activate(c -> InvisibilityService.toggleFade(c.player()))
                    .deactivate(c -> InvisibilityService.unfade(c.player()))
                    .deactivateOnPress(c -> true).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("invisibility_silent_step", 1).cost(() -> 14, () -> 80)
                    .activate(c -> ForceFieldService.raiseShield(c.player()))
                    .followUp(c -> ForceFieldService.push(c.player())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("invisibility_unseen_strike", 2).cost(() -> 16, () -> 80)
                    .activate(c -> ForceFieldService.bridge(c.player())).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("invisibility_shroud", 3).cost(() -> 25, () -> 200)
                    .activate(c -> ForceFieldService.bubble(c.player()))
                    .followUp(c -> ForceFieldService.crush(c.player())).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("invisibility_ghost_protocol", 5).cost(() -> 90, () -> 20 * 90)
                    .activate(c -> ForceFieldService.nova(c.player())).build());
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { InvisibilityService.forget(c.player()); }
}
