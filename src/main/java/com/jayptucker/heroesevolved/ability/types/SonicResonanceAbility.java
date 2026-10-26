package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.sonic.SonicService;
import java.util.Map;

/** An original sonic-voice power inspired by Black Canary: control, resonance and aerial mobility. */
public final class SonicResonanceAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("sonic_cry", 1).cost(() -> 18, () -> 25)
                    .activate(c -> { SonicService.cry(c.player(), c.abilityLevel(), false); return true; }).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("sonic_burst", 2).cost(() -> 30, () -> 120)
                    .activate(c -> { SonicService.burst(c.player(), c.abilityLevel(), false); return true; }).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("sonic_echo_sense", 3).cost(() -> 15, () -> 160)
                    .activate(c -> { SonicService.sense(c.player()); return true; }).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("sonic_vault", 4).cost(() -> 25, () -> 80)
                    .activate(c -> { SonicService.vault(c.player()); return true; }).build(),
            AbilitySlot.QUINARY, new AbilityAction() {
                private final AbilityActionDefinition definition = new AbilityActionDefinition(
                        SonicService.SCREAM_ID, "action.heroesevolved.sonic_scream", 5, 0, 0);
                @Override public AbilityActionDefinition definition() { return definition; }
                @Override public int energyCost(int level) { return 45; }
                @Override public int cooldownTicks(int level) { return 400; }
                @Override public boolean canUse(AbilityUseContext c) { return !SonicService.screaming(c.player()); }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    SonicService.start(c.player(), c.abilityLevel()); return AbilityActivationResult.SUCCESS;
                }
                @Override public void release(AbilityUseContext c) { SonicService.stop(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) {
                    boolean active = SonicService.screaming(c.player()); SonicService.stop(c.player()); return active;
                }
            });
    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onRevoked(AbilityUseContext c) { SonicService.forget(c.player()); }
    @Override public void onAwaken(AbilityUseContext c) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId());
    }
}
