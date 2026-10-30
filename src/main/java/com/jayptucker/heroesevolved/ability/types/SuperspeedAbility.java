package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.speed.SpeedKit;
import com.jayptucker.heroesevolved.speed.SpeedService;
import java.util.Map;

/**
 * Superspeed, after Daphne Millbrook ("when I run, the world stops") and the Flash.
 * <ul>
 *   <li>Level 1, Accelerate: toggle, then sprint. Momentum builds; at 50 lightning crackles over you, at 60
 *   you buffet what you pass, at 100 you break the sound barrier.</li>
 *   <li>Level 1, Blitz: rush an enemy with a flurry; press again to chain to the next. No target: a dash.</li>
 *   <li>Level 2, Snatch: take every dropped item nearby, every enemy shot in the air, and the weapon you look at.</li>
 *   <li>Level 4, Cyclone: run rings around yourself; the wind lifts enemies, then throws them.</li>
 *   <li>Level 5, Lightspeed: one 48-block streak through everything on the line, ending in a sonic boom.</li>
 * </ul>
 */
public final class SuperspeedAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.TOGGLE);
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("speed_accelerate", 1).cost(() -> 0, () -> 0)
                    .canUse(c -> !c.player().isPassenger()).activate(c -> SpeedService.toggle(c.player()))
                    .deactivate(c -> SpeedService.stop(c.player())).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("speed_blitz", 1).cost(() -> 14, () -> 40)
                    .canUse(c -> !c.player().isPassenger())
                    .deactivateOnPress(c -> SpeedKit.chaining(c.player()))
                    .deactivate(c -> SpeedKit.chainNext(c.player()))
                    .activate(c -> SpeedKit.blitz(c.player(), c.abilityLevel())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("speed_snatch", 2).cost(() -> 20, () -> 200)
                    .activate(c -> SpeedKit.snatch(c.player(), c.abilityLevel()) > 0).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("speed_cyclone", 4).cost(() -> 30, () -> 240)
                    .canUse(c -> !SpeedKit.cycloning(c.player()))
                    .activate(c -> SpeedKit.cyclone(c.player(), c.abilityLevel())).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("speed_lightspeed", 5).cost(() -> 50, () -> 900)
                    .canUse(c -> !c.player().isPassenger())
                    .activate(c -> SpeedKit.lightspeed(c.player(), c.abilityLevel()) >= 0).build());
    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onRevoked(AbilityUseContext c) { SpeedService.forget(c.player()); }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
}
