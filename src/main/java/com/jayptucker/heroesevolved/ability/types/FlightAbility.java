package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.flight.DiveBombService;
import com.jayptucker.heroesevolved.flight.FlightDynamicsService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/** Airspeed becomes a resource: bank it in cruise, then redirect it into aerial maneuvers. */
public final class FlightAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.TOGGLE);
    // Keep action IDs stable: existing learned/equipped loadouts survive the redesign.
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("flight_toggle", 1)
                    .canUse(c -> !c.player().isPassenger())
                    .activate(c -> {
                        DiveBombService.cancel(c.player());
                        FlightDynamicsService.cancelSkyburst(c.player());
                        FlightSkillService.cancelBlocking(c.player());
                        return FlightService.toggleFlight(c.player());
                    }).build(),
            AbilitySlot.SECONDARY, new AbilityAction() {
                private final AbilityActionDefinition definition = new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath("heroesevolved", "flight_launch"),
                        "action.heroesevolved.flight_launch", 1, 0, 0);
                @Override public AbilityActionDefinition definition() { return definition; }
                @Override public int energyCost(int level) { return COMMON.flightLaunchEnergyCost.get(); }
                @Override public int cooldownTicks(int level) { return COMMON.flightLaunchCooldownTicks.get(); }
                @Override public boolean canUse(AbilityUseContext c) { return freeToManeuver(c.player()); }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    FlightDynamicsService.pressSkyburst(c.player());
                    return AbilityActivationResult.SUCCESS;
                }
                @Override public void release(AbilityUseContext c) { FlightDynamicsService.releaseSkyburst(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) { return FlightDynamicsService.cancelSkyburst(c.player()); }
            },
            AbilitySlot.TERTIARY, SimpleAction.builder("flight_dive_bomb", 3)
                    .cost(COMMON.flightDiveEnergyCost::get, COMMON.flightDiveCooldownTicks::get)
                    .canUse(c -> DiveBombService.canStart(c.player()))
                    .activate(c -> { DiveBombService.start(c.player(), c.abilityLevel()); return true; })
                    .deactivate(c -> DiveBombService.cancel(c.player())).build(),
            AbilitySlot.QUATERNARY, new SkyhookAction(),
            AbilitySlot.QUINARY, SimpleAction.builder("flight_cyclone", 5)
                    .cost(COMMON.flightCycloneEnergyCost::get, COMMON.flightCycloneCooldownTicks::get)
                    .canUse(c -> freeToManeuver(c.player()))
                    .activate(c -> { FlightService.startCyclone(c.player()); return true; })
                    .deactivate(c -> FlightService.cancelCyclone(c.player())).build()
    );

    private static boolean freeToManeuver(ServerPlayer player) {
        return !player.isPassenger() && !player.isInWaterOrBubble() && !player.isFallFlying()
                && !DiveBombService.isDiving(player) && !FlightService.isCycloneActive(player)
                && !FlightDynamicsService.isCharging(player)
                && !FlightSkillService.isRolling(player) && !FlightSkillService.isChanneling(player);
    }

    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }
    @Override public void onRevoked(AbilityUseContext context) {
        FlightService.stopForPowerRemoval(context.player());
        DiveBombService.forget(context.player());
        FlightSkillService.forget(context.player());
        com.jayptucker.heroesevolved.flight.FlightSkillPassives.forget(context.player());
        EntityCarryService.release(context.player());
    }
    @Override public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }

    /** Rescue a friend or carry a foe skyward; stored airspeed makes the hurl stronger. */
    private static final class SkyhookAction implements AbilityAction {
        private final AbilityActionDefinition definition = new AbilityActionDefinition(
                ResourceLocation.fromNamespaceAndPath("heroesevolved", "flight_skyward_grab"),
                "action.heroesevolved.flight_skyward_grab", 4, 0, 0);
        @Override public AbilityActionDefinition definition() { return definition; }
        @Override public int energyCost(AbilityUseContext c) {
            return EntityCarryService.isCarrying(c.player()) ? COMMON.flightGrabEnergyCost.get() / 3 : COMMON.flightGrabEnergyCost.get();
        }
        @Override public int cooldownTicks(AbilityUseContext c) {
            return EntityCarryService.isCarrying(c.player()) ? 10 : COMMON.flightGrabCooldownTicks.get();
        }
        @Override public boolean canUse(AbilityUseContext c) {
            return freeToManeuver(c.player()) && (EntityCarryService.isCarrying(c.player()) || target(c.player()) != null);
        }
        @Override public boolean deactivate(AbilityUseContext c) {
            Entity released = EntityCarryService.hurl(c.player(), Vec3.ZERO);
            if (released == null) return false;
            if (released instanceof LivingEntity living) {
                living.resetFallDistance();
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0));
            }
            ArmPosePayload.clear(c.player());
            return true;
        }
        @Override public AbilityActivationResult activate(AbilityUseContext c) {
            ServerPlayer player = c.player();
            if (EntityCarryService.isCarrying(player)) {
                double momentum = FlightDynamicsService.consumeMomentum(player);
                Entity thrown = EntityCarryService.hurl(player, player.getLookAngle()
                        .scale(1.8 + momentum * .012).add(0, .25, 0));
                if (thrown == null) return AbilityActivationResult.REJECTED;
                if (player.isAlliedTo(thrown) && thrown instanceof LivingEntity friend) {
                    friend.resetFallDistance();
                    friend.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0));
                }
                AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_SKYBURST, thrown.position(),
                        thrown.position().add(player.getLookAngle()), 1.2 + momentum * .01);
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, 1, .6F);
                ArmPosePayload.show(player, ArmPose.TWO_HANDS, 8);
                return AbilityActivationResult.SUCCESS;
            }
            LivingEntity target = target(player);
            if (target == null || !EntityCarryService.pickUp(player, target)) return AbilityActivationResult.REJECTED;
            if (!FlightService.hasActiveSession(player)) FlightService.launch(player);
            ArmPosePayload.show(player, ArmPose.TWO_HANDS, 20);
            MasteryService.markPowerUse(player, c.abilityId());
            return AbilityActivationResult.SUCCESS;
        }
        private static LivingEntity target(ServerPlayer player) {
            return Targeting.livingEntityInSight(player, 7, entity -> !(entity instanceof TemporalEchoEntity)
                    && !TemporalEntityEchoService.isPastCopy(entity) && !entity.isPassenger()
                    && entity.getPassengers().isEmpty());
        }
    }
}
