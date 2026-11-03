package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.invisibility.InvisibilityService;
import java.util.Map;

/**
 * Invisibility, inspired by Claude Rains.
 * <ul>
 *   <li>Level 1, Fade: toggle full invisibility; drains stamina while it lasts.</li>
 *   <li>Level 1, Refraction Step: physical movement leaves a refracted echo.</li>
 *   <li>Level 2, Mirage: a public optical double follows a checked route.</li>
 *   <li>Level 3, Shared Veil: a moving field conceals consenting companions.</li>
 *   <li>Level 5, Ghost Protocol: deep concealment and diverging optical echoes.</li>
 *   <li>Always: harder to notice; damage flickers you visible; sensing powers can reveal you.</li>
 * </ul>
 */
public final class InvisibilityAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("invisibility_fade", 1).cost(() -> 6, () -> 10)
                    .activate(c -> InvisibilityService.toggleFade(c.player()))
                    .deactivate(c -> InvisibilityService.unfade(c.player()))
                    .deactivateOnPress(c -> true).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("invisibility_silent_step", 1).cost(() -> 12, () -> 30)
                    .canUse(c -> !c.player().isPassenger())
                    .activate(c -> InvisibilityService.silentStep(c.player(), c.abilityLevel())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("invisibility_unseen_strike", 2).cost(() -> 16, () -> 120)
                    .activate(c -> InvisibilityService.mirage(c.player(), c.abilityLevel())).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("invisibility_shroud", 3).cost(() -> 25, () -> 300)
                    .activate(c -> InvisibilityService.shroud(c.player()))
                    .deactivate(c -> InvisibilityService.endVeil(c.player())).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("invisibility_ghost_protocol", 5).cost(() -> 90, () -> 20 * 90)
                    .activate(c -> InvisibilityService.ghostProtocol(c.player())).build());
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { InvisibilityService.forget(c.player()); }
}
