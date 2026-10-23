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
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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

    private static final AbilityAction SKYWARD_GRAB =
            new SkywardGrabAction();

    /** Level 3: plunge along your gaze and land as a sonic shockwave. */
    private static final AbilityAction DIVE_BOMB =
            com.jayptucker.heroesevolved.ability.SimpleAction
                    .builder("flight_dive_bomb", 3)
                    .cost(HeroesEvolvedConfig.COMMON.flightDiveEnergyCost::get,
                            HeroesEvolvedConfig.COMMON.flightDiveCooldownTicks::get)
                    .canUse(context -> !context.player().isPassenger()
                            && !com.jayptucker.heroesevolved.flight.DiveBombService
                            .isDiving(context.player()))
                    .activate(context -> {
                        com.jayptucker.heroesevolved.flight.DiveBombService.start(
                                context.player(), context.abilityLevel());
                        return true;
                    })
                    .build();

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, FLIGHT_TOGGLE,
            AbilitySlot.SECONDARY, LAUNCH,
            AbilitySlot.TERTIARY, DIVE_BOMB,
            AbilitySlot.QUATERNARY, SKYWARD_GRAB,
            AbilitySlot.QUINARY, CYCLONE
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
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
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

    /**
     * Nathan Petrelli's signature move: seize someone and rocket into the
     * sky with them. Pressing again hurls them where you are looking.
     */
    private static final class SkywardGrabAction implements AbilityAction {
        private static final double GRAB_RANGE = 6.0D;
        private static final double HURL_SPEED = 2.4D;

        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "flight_skyward_grab"
                        ),
                        "action.heroesevolved.flight_skyward_grab",
                        4,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(AbilityUseContext context) {
            int grabCost = HeroesEvolvedConfig.COMMON.flightGrabEnergyCost.get();
            return EntityCarryService.isCarrying(context.player())
                    ? grabCost / 3
                    : grabCost;
        }

        @Override
        public int cooldownTicks(AbilityUseContext context) {
            // Evaluated after activation: a fresh grab only needs a short
            // pause before the hurl, while a hurl starts the full cooldown.
            return EntityCarryService.isCarrying(context.player())
                    ? 10
                    : HeroesEvolvedConfig.COMMON.flightGrabCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            ServerPlayer player = context.player();
            return !player.isPassenger()
                    && (EntityCarryService.isCarrying(player)
                    || findTarget(player) != null);
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            ServerPlayer player = context.player();
            if (EntityCarryService.isCarrying(player)) {
                Entity thrown = EntityCarryService.hurl(player,
                        player.getLookAngle().scale(HURL_SPEED)
                                .add(0.0D, 0.25D, 0.0D));
                if (thrown == null) {
                    return AbilityActivationResult.REJECTED;
                }
                player.serverLevel().playSound(null, player.getX(), player.getY(),
                        player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, 1.0F, 0.6F);
                player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK,
                        thrown.getX(), thrown.getY() + 0.5D, thrown.getZ(),
                        3, 0.3D, 0.3D, 0.3D, 0.0D);
                return AbilityActivationResult.SUCCESS;
            }

            LivingEntity target = findTarget(player);
            if (target == null || !EntityCarryService.pickUp(player, target)) {
                return AbilityActivationResult.REJECTED;
            }

            // Grabbing from the ground turns straight into a Launch.
            if (!FlightService.hasActiveSession(player)) {
                FlightService.launch(player);
            }
            if (target instanceof ServerPlayer grabbed) {
                grabbed.displayClientMessage(Component.literal(
                        player.getName().getString() + " has you!"
                ), true);
            }
            MasteryService.markPowerUse(player, context.abilityId());
            return AbilityActivationResult.SUCCESS;
        }

        private static LivingEntity findTarget(ServerPlayer player) {
            return Targeting.livingEntityInSight(player, GRAB_RANGE,
                    target -> !(target instanceof TemporalEchoEntity)
                            && !TemporalEntityEchoService.isPastCopy(target)
                            && !target.isPassenger()
                            && target.getPassengers().isEmpty());
        }
    }
}
