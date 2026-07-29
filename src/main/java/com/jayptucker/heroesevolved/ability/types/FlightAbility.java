package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActionDefinition;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.flight.FlightService;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class FlightAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(
                    0,
                    0,
                    5,
                    AbilityActivationType.TOGGLE
            );

    private static final AbilityAction FLIGHT_TOGGLE =
            new FlightToggleAction();

    private static final AbilityAction LAUNCH =
            new LaunchAction();

    private static final AbilityAction CYCLONE =
            new CycloneAction();

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, FLIGHT_TOGGLE,
            AbilitySlot.SECONDARY, LAUNCH,
            AbilitySlot.TERTIARY, CYCLONE
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
    public void onRevoked(AbilityUseContext context) {
        FlightService.stopForPowerRemoval(context.player());
    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        // A Flight awakening begins with the power's signature Launch rather
        // than requiring a frightened player to find the keybind first.
        FlightService.launch(context.player());
    }

    private static final class FlightToggleAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "flight_toggle"
                        ),
                        "action.heroesevolved.flight_toggle",
                        1,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger();
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            return FlightService.toggleFlight(context.player())
                    ? AbilityActivationResult.SUCCESS
                    : AbilityActivationResult.REJECTED;
        }
    }

    private static final class LaunchAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "flight_launch"
                        ),
                        "action.heroesevolved.flight_launch",
                        1,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON
                    .flightLaunchEnergyCost
                    .get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON
                    .flightLaunchCooldownTicks
                    .get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger();
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            FlightService.launch(context.player());
            return AbilityActivationResult.SUCCESS;
        }
    }

    private static final class CycloneAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "flight_cyclone"
                        ),
                        "action.heroesevolved.flight_cyclone",
                        5,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.flightCycloneEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.flightCycloneCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger();
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            FlightService.startCyclone(context.player());
            return AbilityActivationResult.SUCCESS;
        }
    }
}
