package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.pyro.PyroFlightService;
import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Pyrokinesis, inspired by Meredith and Flint Gordon.
 *
 * <ul>
 *   <li>Level 1, Cinder Spear: tap a fire bolt or charge a piercing line of fire.</li>
 *   <li>Level 1, Dragon's Breath: a held flamethrower that pushes enemies back.</li>
 *   <li>Level 2, Firestorm: aimed fire field that gathers burning enemies.</li>
 *   <li>Level 3, Flame Flight: sustained jet flight, with a Meteor Dive alternate.</li>
 *   <li>Level 5, Sunfall: gather an aimed fire sun and drop it on a locked target.</li>
 *   <li>Always: immune to fire (and lava from level 3), punches ignite from level 2,
 *   fire and lava restore stamina, water quenches Heat, and cold hurts more.
 *   Crouch while casting to keep flames from catching on blocks.</li>
 * </ul>
 */
public final class PyrokinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, new AbilityAction() {
                private final AbilityActionDefinition definition = new AbilityActionDefinition(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "pyro_flare"),
                        "action.heroesevolved.pyro_flare", 1, 0, 0);
                @Override public AbilityActionDefinition definition() { return definition; }
                @Override public int energyCost(int level) { return COMMON.pyroFlareEnergyCost.get(); }
                @Override public int cooldownTicks(int level) { return COMMON.pyroFlareCooldownTicks.get(); }
                @Override public boolean canUse(AbilityUseContext c) {
                    return !PyroService.isChargingSpear(c.player()) && !c.player().isInWaterOrBubble();
                }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    return PyroService.startSpear(c.player(), c.abilityLevel())
                            ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED;
                }
                @Override public void release(AbilityUseContext c) { PyroService.releaseSpear(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) { return PyroService.cancelSpear(c.player()); }
            },
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
                    .deactivate(c -> PyroService.collapseRing(c.player(), c.abilityLevel()))
                    .build(),
            AbilitySlot.QUATERNARY, new AbilityAction() {
                private final AbilityActionDefinition definition = new AbilityActionDefinition(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "pyro_backdraft"),
                        "action.heroesevolved.pyro_backdraft", 3, 0, 0);
                @Override public AbilityActionDefinition definition() { return definition; }
                @Override public int energyCost(int level) { return COMMON.pyroBackdraftEnergyCost.get(); }
                @Override public int cooldownTicks(int level) { return COMMON.pyroBackdraftCooldownTicks.get(); }
                @Override public boolean canUse(AbilityUseContext c) {
                    return !c.player().isPassenger() && !c.player().isInWaterRainOrBubble();
                }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    return PyroFlightService.toggle(c.player(), c.abilityLevel())
                            ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED;
                }
                @Override public boolean deactivateOnPress(AbilityUseContext c) { return true; }
                @Override public boolean deactivate(AbilityUseContext c) { return PyroFlightService.deactivate(c.player(), c.modifierHeld()); }
            },
            AbilitySlot.QUINARY, new AbilityAction() {
                private final AbilityActionDefinition definition = new AbilityActionDefinition(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "pyro_inferno"),
                        "action.heroesevolved.pyro_inferno", 5, 0, 0);
                @Override public AbilityActionDefinition definition() { return definition; }
                @Override public int energyCost(int level) { return COMMON.pyroInfernoEnergyCost.get(); }
                @Override public int cooldownTicks(int level) { return COMMON.pyroInfernoCooldownTicks.get(); }
                @Override public boolean canUse(AbilityUseContext c) {
                    return !PyroService.isCharging(c.player()) && !c.player().isInWaterOrBubble();
                }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    return PyroService.startInferno(c.player(), c.abilityLevel())
                            ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED;
                }
                @Override public void release(AbilityUseContext c) { PyroService.releaseInferno(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) { return PyroService.cancelInferno(c.player()); }
            }
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
