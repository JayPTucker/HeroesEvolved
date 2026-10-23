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
 * Ted Sprague's induced radioactivity: heat, toxic emissions, EMPs and dangerous stress.
 *
 * <ul>
 *   <li>Level 1, Radiant Touch: white-hot hands burn and irradiate.</li>
 *   <li>Level 2, Radiation Pulse: a wave of radiation sickness around you.</li>
 *   <li>Level 3, EMP: disable nearby redstone electronics temporarily.</li>
 *   <li>Level 4, Controlled Emission: vent stress as a focused heat beam.</li>
 *   <li>Level 5, Exploding Man: detonate. Shift holds it in.</li>
 *   <li>Always: every radiation power builds stress. Above 60% you
 *   glow orange and leak radiation; injury also builds stress. At 100% vent
 *   before containment fails. Your own nuclear blast and fallout cannot hurt you.</li>
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
            AbilitySlot.QUATERNARY, SimpleAction.builder("radiation_meltdown_beam", 4)
                    .cost(COMMON.radiationBeamEnergyCost::get,
                            COMMON.radiationBeamCooldownTicks::get)
                    .canUse(context -> RadiationService.canVent(context.player()))
                    .activate(context -> RadiationService.meltdownBeam(
                            context.player(), context.abilityLevel()))
                    .build(),
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
