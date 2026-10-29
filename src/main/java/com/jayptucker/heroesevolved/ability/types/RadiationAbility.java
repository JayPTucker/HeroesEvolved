package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.radiation.RadiationService;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Ted Sprague's induced radioactivity: heat, a spreading sickness, EMPs and a core that runs hotter
 * the harder he pushes it.
 *
 * <ul>
 *   <li>Level 1, Radiant Heat: a white-hot cone from the palms; it also boils, thaws and melts.</li>
 *   <li>Level 2, Contamination: a pulse of Radiation Sickness that leaves the ground contaminated.
 *   Shift emits a blinding flash instead.</li>
 *   <li>Level 3, EMP: kill redstone nearby, throw enemies off balance, and vent 25 Core.</li>
 *   <li>Level 4, Controlled Emission: hold to pour Core out through a piercing beam.</li>
 *   <li>Level 5, Exploding Man: detonate. Shift holds it in.</li>
 *   <li>Always: Core builds with every power and every injury. Hot (40) hits harder, Glowing (60)
 *   leaks radiation onto enemies, Critical (80) hits harder still and burns. At 100, vent or
 *   containment fails. Your own blast, fallout and sickness cannot hurt you.</li>
 * </ul>
 */
public final class RadiationAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("radiation_radiant_touch", 1)
                    .cost(COMMON.radiationTouchEnergyCost::get,
                            COMMON.radiationTouchCooldownTicks::get)
                    .activate(context -> RadiationService.radiantTouch(
                            context.player(), context.abilityLevel()))
                    .build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("radiation_pulse", 2)
                    .cost(COMMON.radiationPulseEnergyCost::get,
                            COMMON.radiationPulseCooldownTicks::get)
                    .activate(context -> {
                        RadiationService.radiationPulse(context.player(),
                                context.abilityLevel(), context.modifierHeld());
                        return true;
                    })
                    .build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("radiation_emp", 3)
                    .cost(COMMON.radiationEmpEnergyCost::get,
                            COMMON.radiationEmpCooldownTicks::get)
                    .activate(context -> {
                        RadiationService.emp(context.player());
                        return true;
                    })
                    .build(),
            AbilitySlot.QUATERNARY, new AbilityAction() {
                private final com.jayptucker.heroesevolved.ability.AbilityActionDefinition definition =
                        new com.jayptucker.heroesevolved.ability.AbilityActionDefinition(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "radiation_meltdown_beam"),
                                "action.heroesevolved.radiation_meltdown_beam", 4, 0, 0);
                @Override public com.jayptucker.heroesevolved.ability.AbilityActionDefinition definition() { return definition; }
                @Override public int energyCost(int level) { return COMMON.radiationBeamEnergyCost.get(); }
                @Override public int cooldownTicks(int level) { return COMMON.radiationBeamCooldownTicks.get(); }
                @Override public boolean canUse(AbilityUseContext c) { return RadiationService.canVent(c.player()); }
                // Hold to vent Core through the beam; let go to stop. Shift + key stops at once.
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    return RadiationService.startEmission(c.player(), c.abilityLevel())
                            ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED;
                }
                @Override public void release(AbilityUseContext c) { RadiationService.releaseEmission(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) { return RadiationService.stopEmission(c.player()); }
            },
            AbilitySlot.QUINARY, SimpleAction.builder("radiation_exploding_man", 5)
                    .cost(COMMON.radiationDetonationEnergyCost::get,
                            COMMON.radiationDetonationCooldownTicks::get)
                    .canUse(context -> !RadiationService.isDetonating(context.player()))
                    .activate(context -> {
                        RadiationService.beginDetonation(context.player(),
                                context.abilityLevel(), true);
                        return true;
                    })
                    .deactivate(context -> RadiationService.holdItIn(context.player()))
                    .build()
    );

    @Override
    public AbilityDefinition definition() {
        return DEFINITION;
    }

    @Override
    public Map<AbilitySlot, AbilityAction> actions() {
        return ACTIONS;
    }

    @Override
    public boolean canUse(AbilityUseContext context) {
        return false;
    }

    @Override
    public AbilityActivationResult activate(AbilityUseContext context) {
        return AbilityActivationResult.REJECTED;
    }

    @Override
    public void tick(AbilityUseContext context) {
        RadiationService.tickPlayer(context.player(), context.gameTime());
    }

    @Override
    public void onRevoked(AbilityUseContext context) {
        RadiationService.stopAll(context.player());
    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(
                context.player(), context.abilityId());
    }

    @Override
    public void onOverexerted(AbilityUseContext context) {
        RadiationService.radiationBurn(context.player());
    }
}
