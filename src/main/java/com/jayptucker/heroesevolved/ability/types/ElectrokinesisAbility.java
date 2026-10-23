package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.electro.ElectroService;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Electrokinesis, inspired by Elle Bishop.
 *
 * <ul>
 *   <li>Level 1, Chain Lightning: a bolt that leaps between foes.</li>
 *   <li>Level 2, Ball Lightning: a drifting orb that zaps foes, then
 *   bursts. Static Touch also unlocks: melee hits shock and stun.</li>
 *   <li>Level 3, Thunderstrike: call lightning where you point.</li>
 *   <li>Level 4, Ride the Lightning: dash as a streak of current.</li>
 *   <li>Level 5, Storm Surge: a nova that strikes everything around you;
 *   stronger under a storm.</li>
 *   <li>Always: immune to lightning, which recharges you; thunderstorms
 *   speed up energy regeneration. Using power in water shocks you too.</li>
 * </ul>
 */
public final class ElectrokinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("electro_chain_lightning", 1)
                    .cost(COMMON.electroChainEnergyCost::get,
                            COMMON.electroChainCooldownTicks::get)
                    .activate(context -> {
                        ElectroService.chainLightning(context.player(),
                                context.abilityLevel());
                        return true;
                    })
                    .build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("electro_ball_lightning", 2)
                    .cost(COMMON.electroBallEnergyCost::get,
                            COMMON.electroBallCooldownTicks::get)
                    .activate(context -> {
                        ElectroService.ballLightning(context.player(),
                                context.abilityLevel());
                        return true;
                    })
                    .build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("electro_thunderstrike", 3)
                    .cost(COMMON.electroThunderstrikeEnergyCost::get,
                            COMMON.electroThunderstrikeCooldownTicks::get)
                    .activate(context -> ElectroService.thunderstrike(
                            context.player(), context.abilityLevel()))
                    .build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("electro_ride_the_lightning", 4)
                    .cost(COMMON.electroRideEnergyCost::get,
                            COMMON.electroRideCooldownTicks::get)
                    .canUse(context -> !context.player().isPassenger())
                    .activate(context -> {
                        ElectroService.rideTheLightning(context.player(),
                                context.abilityLevel());
                        return true;
                    })
                    .build(),
            AbilitySlot.QUINARY, SimpleAction.builder("electro_storm_surge", 5)
                    .cost(COMMON.electroStormEnergyCost::get,
                            COMMON.electroStormCooldownTicks::get)
                    .activate(context -> {
                        ElectroService.stormSurge(context.player(),
                                context.abilityLevel());
                        return true;
                    })
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
    public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }

    @Override
    public void onOverexerted(AbilityUseContext context) {
        ElectroService.uncontrolledDischarge(context.player());
    }
}
