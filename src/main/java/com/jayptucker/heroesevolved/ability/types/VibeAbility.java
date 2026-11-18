package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.vibe.VibeBreaches;
import com.jayptucker.heroesevolved.vibe.VibeService;
import com.jayptucker.heroesevolved.vibe.VibeSight;

import java.util.Map;

/** Cisco Ramon's dimensional vibrations, from The Flash. */
public final class VibeAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            // Held: a steady blast from the palm, draining stamina until the key comes up.
            AbilitySlot.PRIMARY, SimpleAction.builder("vibe_blast", 1).cost(() -> 4, () -> 10)
                    .followUp(c -> VibeService.endStream(c.player()))
                    .release(c -> VibeService.endStream(c.player()))
                    .activate(VibeService::stream).build(),
            // Tap: the frequency wheel (free). Hold: channel a breach to the last frequency used (Shift: where you
            // look). Press while open: close it.
            AbilitySlot.SECONDARY, SimpleAction.builder("vibe_breach", 2).cost(() -> 20, () -> 130)
                    .followUp(VibeBreaches::breachFollowUp)
                    .release(VibeBreaches::breachRelease)
                    .travel(VibeBreaches::travelCost)
                    .activate(VibeBreaches::breach).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("vibe_read", 3).cost(() -> 12, () -> 100)
                    .activate(c -> VibeSight.read(c.player(), false)).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("vibe_breach_guard", 4).cost(() -> 21, () -> 170)
                    .followUp(c -> !c.modifierHeld() && VibeService.returnFire(c))
                    .deactivate(c -> VibeService.closeGuard(c.player()))
                    .activate(c -> VibeService.guard(c.player(), false)).build(),
            // Rift Fall (the id is Frequency Lock's, so loadouts and the balance table carry over).
            AbilitySlot.QUINARY, SimpleAction.builder("vibe_frequency_lock", 5).cost(() -> 42, () -> 340)
                    .activate(com.jayptucker.heroesevolved.vibe.RiftFall::cast).build());

    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }

    @Override public void onAwaken(AbilityUseContext c) { PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { VibeService.forget(c.player()); }
}
