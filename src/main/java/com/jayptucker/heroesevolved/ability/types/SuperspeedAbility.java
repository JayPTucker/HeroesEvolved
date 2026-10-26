package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.speed.SpeedService;
import java.util.Map;

public final class SuperspeedAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.TOGGLE);
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("speed_accelerate", 1).cost(() -> 0, () -> 0)
                    .canUse(c -> !c.player().isPassenger()).activate(c -> SpeedService.toggle(c.player()))
                    .deactivate(c -> SpeedService.stop(c.player())).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("speed_quickstep", 1).cost(() -> 10, () -> 20)
                    .canUse(c -> !c.player().isPassenger()).activate(c -> SpeedService.dash(c.player(), c.abilityLevel())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("speed_flurry", 2).cost(() -> 22, () -> 100)
                    .canUse(c -> SpeedService.target(c.player(), 6) != null).activate(c -> SpeedService.flurry(c.player(), c.abilityLevel())).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("speed_whirlwind", 4).cost(() -> 30, () -> 180)
                    .activate(c -> SpeedService.whirlwind(c.player())).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("speed_redline", 5).cost(() -> 35, () -> 400)
                    .canUse(c -> !c.player().isPassenger()).activate(c -> SpeedService.redline(c.player()))
                    .deactivate(c -> SpeedService.stop(c.player())).build());
    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onRevoked(AbilityUseContext c) { SpeedService.forget(c.player()); }
    @Override public void onAwaken(AbilityUseContext c) { com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId()); }
}
