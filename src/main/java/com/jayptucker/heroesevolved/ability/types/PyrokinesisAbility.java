package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.pyro.PyroService;
import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Pyrokinesis, inspired by Meredith and Flint Gordon.
 *
 * <ul>
 *   <li>Level 1, Flare: a fire bolt that burns a creature or lights a surface.</li>
 *   <li>Level 1, Flame Palm: a held flamethrower, up to five seconds.</li>
 *   <li>Level 2, Ring of Fire: real fire around you; hostile projectiles burn up crossing it.</li>
 *   <li>Level 3, Backdraft: a fire-propelled leap with a cushioned landing.</li>
 *   <li>Level 5, Inferno: a three-second charge, then a fire nova. Blue at high Heat.</li>
 *   <li>Always: immune to fire (and lava from level 3), punches ignite from level 2,
 *   fire and lava restore stamina, water quenches Heat, and cold hurts more.
 *   Crouch while casting to keep flames from catching on blocks.</li>
 * </ul>
 */
public final class PyrokinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("pyro_flare", 1)
                    .cost(COMMON.pyroFlareEnergyCost::get, COMMON.pyroFlareCooldownTicks::get)
                    .activate(c -> PyroService.flare(c.player(), c.abilityLevel()))
                    .build(),
            AbilitySlot.SECONDARY, new AbilityAction() {
                private final AbilityActionDefinition definition = new AbilityActionDefinition(
                        PyroService.PALM_ID, "action.heroesevolved.pyro_flame_palm", 1, 0, 0);
                @Override public AbilityActionDefinition definition() { return definition; }
                @Override public int energyCost(int level) { return COMMON.pyroPalmEnergyCost.get(); }
                @Override public int cooldownTicks(int level) { return COMMON.pyroPalmCooldownTicks.get(); }
                @Override public boolean canUse(AbilityUseContext c) {
                    return !PyroService.isChanneling(c.player()) && !c.player().isInWaterOrBubble();
                }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    PyroService.startPalm(c.player(), c.abilityLevel());
                    return AbilityActivationResult.SUCCESS;
                }
                @Override public void release(AbilityUseContext c) { PyroService.stopPalm(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) { return PyroService.stopPalm(c.player()); }
            },
            AbilitySlot.TERTIARY, SimpleAction.builder("pyro_ring_of_fire", 2)
                    .cost(COMMON.pyroRingEnergyCost::get, COMMON.pyroRingCooldownTicks::get)
                    .activate(c -> PyroService.ringOfFire(c.player(), c.abilityLevel()))
                    .build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("pyro_backdraft", 3)
                    .cost(COMMON.pyroBackdraftEnergyCost::get, COMMON.pyroBackdraftCooldownTicks::get)
                    .canUse(c -> !c.player().isPassenger())
                    .activate(c -> PyroService.backdraft(c.player(), c.abilityLevel()))
                    .build(),
            AbilitySlot.QUINARY, SimpleAction.builder("pyro_inferno", 5)
                    .cost(COMMON.pyroInfernoEnergyCost::get, COMMON.pyroInfernoCooldownTicks::get)
                    .canUse(c -> !PyroService.isCharging(c.player()))
                    .activate(c -> PyroService.startInferno(c.player(), c.abilityLevel()))
                    .deactivate(c -> PyroService.cancelInferno(c.player()))
                    .build()
    );

    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }
    @Override public void onRevoked(AbilityUseContext context) { PyroService.forget(context.player()); }
    @Override public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }
    @Override public void onOverexerted(AbilityUseContext context) { PyroService.overheat(context.player()); }
}
