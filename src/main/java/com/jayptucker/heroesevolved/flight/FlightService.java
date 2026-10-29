package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.OverexertionService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.network.FlightVisualSyncService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FlightService {
    private static final double LAUNCH_ANGLE_RADIANS =
            Math.toRadians(75.0D);
    private static final double CYCLONE_ORBIT_HEIGHT = 3.0D;
    private static final int CYCLONE_ASCENT_TICKS = 15;
    private static final int TEMPEST_WINDUP_TICKS = 20;
    private static final int SKYBURST_ASCENT_TICKS = 12;

    // Cyclones are short-lived combat effects. Keeping their active state in
    // memory avoids persisting an incomplete tornado across a server restart.
    private static final Map<UUID, CycloneState> ACTIVE_CYCLONES =
            new HashMap<>();

    private static final ResourceLocation FLIGHT_PERMISSION_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "flight_permission"
            );

    private static final AttributeModifier FLIGHT_PERMISSION_MODIFIER =
            new AttributeModifier(
                    FLIGHT_PERMISSION_MODIFIER_ID,
                    1.0D,
                    AttributeModifier.Operation.ADD_VALUE
            );

    private FlightService() {
    }

    public static boolean isFlightAuthorized(ServerPlayer player) {
        return com.jayptucker.heroesevolved.ability.service.PlayerAbilityService
                .getAbility(player, ModAbilities.FLIGHT_ID)
                .map(com.jayptucker.heroesevolved.ability.data.AbilityProgress::isUnlocked).orElse(false)
                || com.jayptucker.heroesevolved.mimicry.MimicryService.isBorrowingFlight(player);
    }

    public static boolean hasActiveSession(ServerPlayer player) {
        return player.getData(
                ModDataAttachments.PLAYER_FLIGHT.get()
        ).sessionActive();
    }

    public static boolean isCycloneActive(ServerPlayer player) {
        return ACTIVE_CYCLONES.containsKey(player.getUUID());
    }

    public static void stopForPowerRemoval(ServerPlayer player) {
        if (hasActiveSession(player)) endSession(player, false);
        else {
            FlightDynamicsService.forget(player);
            ACTIVE_CYCLONES.remove(player.getUUID());
        }
    }

    public static void launch(ServerPlayer player) {
        player.serverLevel().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSounds.FLIGHT_LAUNCH.get(),
                SoundSource.PLAYERS,
                0.90F,
                1.0F
        );

        boolean grounded = player.onGround();
        startFlightSession(player, grounded ? SKYBURST_ASCENT_TICKS : 0);
        player.resetFallDistance();
        if (grounded) {
            applyLaunchAscentVelocity(player);
            Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
            AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_SKYBURST,
                    center, center.add(0, 1, 0), 1.6D);
        } else {
            Vec3 direction = player.getLookAngle().normalize();
            setPlayerVelocity(player, direction.scale(Math.min(3.6D,
                    HeroesEvolvedConfig.COMMON.flightForwardSpeed.get())));
            FlightDynamicsService.beginBurst(player, 12);
            Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
            AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_SKYBURST,
                    center, center.add(direction), 1.6D);
        }
        MasteryService.markPowerUse(player, ModAbilities.FLIGHT_ID);
    }

    public static boolean toggleFlight(ServerPlayer player) {
        PlayerFlightData flightData = player.getData(
                ModDataAttachments.PLAYER_FLIGHT.get()
        );

        if (flightData.flightEnabled()) {
            endSession(player, false);
            return true;
        }

        // Flight Toggle may be used from the ground or while falling.
        // Launch is optional mobility, not a prerequisite for sustained flight.
        startFlightSession(player, 0);
        return true;
    }

    /** Tempest: gather a visible vortex, orbit its eye, then throw its captives clear. */
    public static void startCyclone(ServerPlayer player) {
        FlightDynamicsService.cancelSkyburst(player);
        double strength = 1.0D + FlightDynamicsService.consumeMomentum(player) / 200.0D;
        Vec3 horizontalLook = horizontalLookDirection(player);
        double orbitRadius = HeroesEvolvedConfig.COMMON.flightCycloneOrbitRadius.get();
        Vec3 center = player.position().add(horizontalLook.scale(orbitRadius));
        Vec3 fromCenter = player.position().subtract(center);
        startFlightSession(player, 0);
        ACTIVE_CYCLONES.put(player.getUUID(), new CycloneState(center,
                Math.atan2(fromCenter.z, fromCenter.x),
                HeroesEvolvedConfig.COMMON.flightCycloneDurationSeconds.get() * 20,
                CYCLONE_ASCENT_TICKS, TEMPEST_WINDUP_TICKS, strength));
        setPlayerVelocity(player, Vec3.ZERO);
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_TEMPEST_CHARGE,
                center, center, HeroesEvolvedConfig.COMMON.flightCycloneRadius.get());
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.0F, 0.75F);
        MasteryService.markPowerUse(player, ModAbilities.FLIGHT_ID);
        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    /** Shift + V always cancels for free, including during the wind-up. */
    public static boolean cancelCyclone(ServerPlayer player) {
        if (ACTIVE_CYCLONES.remove(player.getUUID()) == null) return false;
        setPlayerVelocity(player, Vec3.ZERO);
        player.setData(ModDataAttachments.PLAYER_FLIGHT.get(),
                player.getData(ModDataAttachments.PLAYER_FLIGHT.get()).withVisualPoseActive(false));
        player.resetFallDistance();
        FlightVisualSyncService.syncToTrackingPlayers(player);
        return true;
    }

    public static void setForwardInput(ServerPlayer player, boolean movingForward) {
        PlayerFlightData data = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        if (!data.flightEnabled() || isCycloneActive(player)) return;
        if (movingForward && !com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(player)) return;
        player.setData(ModDataAttachments.PLAYER_FLIGHT.get(), data.withForwardInput(movingForward));
        // Keep key state current without letting maneuver input trigger a cruise shockwave.
        if (FlightDynamicsService.isCharging(player) || DiveBombService.isDiving(player)
                || FlightSkillService.isRolling(player) || FlightSkillService.isChanneling(player)) return;
        refreshSpeedState(player);
    }

    public static void changeSpeed(ServerPlayer player, int direction) {
        PlayerFlightData data = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        if (direction == 0 || !data.flightEnabled() || player.onGround() || !player.isAlive()
                || !com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(player)
                || player.isSpectator() || player.isPassenger() || isCycloneActive(player)
                || FlightDynamicsService.isCharging(player) || DiveBombService.isDiving(player)
                || FlightSkillService.isRolling(player) || FlightSkillService.isChanneling(player)
                || EclipseService.arePowersSuppressed(player)
                || !isFlightAuthorized(player)) return;
        int level = PlayerProgressionService.getLevel(player);
        int step = FlightSpeedControl.adjust(data.speedStep(), direction, level);
        player.setData(ModDataAttachments.PLAYER_FLIGHT.get(), data.withSpeedStep(step));
        if (step == 0 && data.speedStep() > 0) setPlayerVelocity(player, player.getDeltaMovement().scale(.25D));
        refreshSpeedState(player);
        FlightVisualSyncService.syncToTrackingPlayers(player);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.heroesevolved.flight_speed", step + 1, FlightSpeedControl.maximumStep(level) + 1), true);
    }

    private static void refreshSpeedState(ServerPlayer player) {
        PlayerFlightData data = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        int step = Math.min(data.speedStep(), FlightSpeedControl.maximumStep(PlayerProgressionService.getLevel(player)));
        boolean active = data.movingForward() && step > 0 && !player.onGround() && data.launchTicksRemaining() == 0
                && !FlightDynamicsService.isCharging(player) && !FlightSkillService.isRolling(player)
                && !FlightSkillService.isChanneling(player);
        PlayerFlightData updated = data.withSpeedStep(step).withVisualPoseActive(active);
        if (updated == data) return;
        player.setData(ModDataAttachments.PLAYER_FLIGHT.get(), updated);
        if (active && !data.visualPoseActive()) triggerFlightBoostSonicBoom(player);
        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    static void startFlightSession(
            ServerPlayer player,
            int launchDelayTicks
    ) {
        boolean grantedMayfly = player.getData(ModDataAttachments.PLAYER_FLIGHT.get()).grantedMayfly()
                || grantFlightPermission(player);

        player.getAbilities().flying = true;
        player.onUpdateAbilities();

        player.setData(
                ModDataAttachments.PLAYER_FLIGHT.get(),
                PlayerFlightData.empty().startLaunch(
                        grantedMayfly,
                        launchDelayTicks
                )
        );

        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    public static void tick(ServerPlayer player) {
        PlayerFlightData flightData = player.getData(
                ModDataAttachments.PLAYER_FLIGHT.get()
        );

        // Persistent movement is subject to the same gates as activation.
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isFallFlying()
                || player.isInWaterOrBubble() || EclipseService.arePowersSuppressed(player)
                || (flightData.sessionActive() && !isFlightAuthorized(player))) {
            if (flightData.sessionActive()) {
                endSession(player, player.isAlive());
            } else FlightDynamicsService.forget(player);
            return;
        }

        if (!flightData.sessionActive()) {
            return;
        }
        if (com.jayptucker.heroesevolved.ability.service.PowerRules.immobilized(player)) {
            FlightDynamicsService.forget(player);
            cancelCyclone(player);
            setPlayerVelocity(player, Vec3.ZERO);
            return;
        }

        if (!flightData.flightEnabled()) {
            if (player.onGround()) {
                endSession(player, false);
            }

            return;
        }

        // A session started on the ground may remain ready for takeoff.
        // Once the player has actually left the ground, landing ends it.
        if (!player.onGround()) {
            if (!flightData.hasBeenAirborne()) {
                flightData = flightData.markAirborne();
                player.setData(
                        ModDataAttachments.PLAYER_FLIGHT.get(),
                        flightData
                );
            }
        } else if (flightData.hasBeenAirborne() && !FlightDynamicsService.isCharging(player)
                && !isCycloneActive(player) && flightData.launchTicksRemaining() == 0) {
            endSession(player, false);
            return;
        }

        flightData = handlePendingForwardLaunch(player, flightData);

        // Launch has its own one-time cost. Sustained-flight drain begins
        // only after its upward/forward launch movement has finished.
        if (flightData.launchTicksRemaining() > 0) {
            return;
        }

        boolean cycloneActive = tickCyclone(player);
        flightData = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());

        if (!cycloneActive && !FlightDynamicsService.isCharging(player) && !DiveBombService.isDiving(player)
                && !FlightSkillService.isRolling(player) && !FlightSkillService.isChanneling(player)) {
            refreshSpeedState(player);
            flightData = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
            applyForwardFlightMovement(player, flightData);
        }

        if (flightData.visualPoseActive()) {
            MasteryService.markPowerUse(player, ModAbilities.FLIGHT_ID);
        }

        long gameTime = player.serverLevel().getGameTime();

        // Both modes use the pitched flight pose; cyclone yaw follows travel.

        // Wind rushing past at Boost speed whitens the edges of vision.
        if (flightData.visualPoseActive() && gameTime % 10 == 0) {
            com.jayptucker.heroesevolved.network.ScreenTintPayload.send(
                    player, 0x30E8F4FF, 14,
                    com.jayptucker.heroesevolved.network.ScreenTintPayload.Style.EDGE
            );
        }

        // Vapor is reconstructed on clients from the existing synchronized flight pose.

        // Cyclone already charges a large, one-time activation cost. It must
        // not also trigger Flight's sustained drain and overexert its owner.
        if (cycloneActive) {
            return;
        }

        if (gameTime % 20 != 0) {
            return;
        }

        int energyDrain = flightData.visualPoseActive()
                ? HeroesEvolvedConfig.COMMON
                .flightBoostEnergyDrainPerSecond
                .get()
                : HeroesEvolvedConfig.COMMON
                .flightEnergyDrainPerSecond
                .get();

        if (PlayerEnergyService.getEnergy(player) < energyDrain) {
            // Sustained Flight is still an ability use. If its next drain
            // cannot be paid, the player overexerts once, then Flight ends.
            // This uses the shared system so every power has the same
            // damage, Weakness, Nausea, chat warning, and crimson border.
            OverexertionService.apply(player, energyDrain);
            endSession(player, true);
            return;
        }

        if (PlayerEnergyService.tryConsume(player, energyDrain)) {
            MasteryService.awardEnergySpent(player, energyDrain);
        }
    }

    private static PlayerFlightData handlePendingForwardLaunch(
            ServerPlayer player,
            PlayerFlightData flightData
    ) {
        if (flightData.launchTicksRemaining() <= 0) {
            return flightData;
        }

        // A short, controlled takeoff clears the ground before the forward burst.
        applyLaunchAscentVelocity(player);

        int remainingTicks = flightData.launchTicksRemaining() - 1;

        PlayerFlightData updatedData = flightData.withLaunchTicksRemaining(
                remainingTicks
        );

        player.setData(
                ModDataAttachments.PLAYER_FLIGHT.get(),
                updatedData
        );

        if (remainingTicks == 0) {
            applyForwardLaunchVelocity(player);
        }

        return updatedData;
    }

    private static void applyForwardFlightMovement(
            ServerPlayer player,
            PlayerFlightData flightData
    ) {
        if (!flightData.visualPoseActive() || FlightDynamicsService.isBursting(player)) {
            return;
        }

        Vec3 lookDirection = player.getLookAngle().normalize();
        Vec3 currentVelocity = player.getDeltaMovement();
        double desiredCruiseSpeed = FlightSpeedControl.speed(flightData.speedStep(),
                HeroesEvolvedConfig.COMMON.flightCruiseSpeed.get());
        // Approach the selected velocity in either direction, so scrolling down brakes
        // rather than retaining the old minimum-speed boost indefinitely.
        setPlayerVelocity(player, currentVelocity.lerp(lookDirection.scale(desiredCruiseSpeed), .22D));
    }

    private static boolean tickCyclone(ServerPlayer player) {
        CycloneState cyclone = ACTIVE_CYCLONES.get(player.getUUID());
        if (cyclone == null) return false;
        double radius = HeroesEvolvedConfig.COMMON.flightCycloneRadius.get();
        long time = player.serverLevel().getGameTime();
        if (cyclone.windupTicks > 0) {
            setPlayerVelocity(player, Vec3.ZERO);
            player.resetFallDistance();
            if (cyclone.windupTicks % 4 == 0)
                AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_TEMPEST_CHARGE,
                        cyclone.center, cyclone.center, radius);
            cyclone.windupTicks--;
            return true;
        }
        // Looking steers the eye; forward input and wheel speed never override its orbit.
        Vec3 steering = horizontalLookDirection(player).scale(0.10D);
        Vec3 candidate = cyclone.center.add(steering);
        if (player.serverLevel().noCollision(player,
                new AABB(candidate.add(-0.5D, 0.5D, -0.5D), candidate.add(0.5D, 2.5D, 0.5D))))
            cyclone.center = candidate;
        if (cyclone.ascentTicksRemaining > 0) {
            double ascent = cyclone.center.y + CYCLONE_ORBIT_HEIGHT - player.getY();
            setPlayerVelocity(player, new Vec3(0, Math.clamp(ascent, -0.25D, 0.25D), 0));
            cyclone.ascentTicksRemaining--;
        } else {
            double orbitRadius = HeroesEvolvedConfig.COMMON.flightCycloneOrbitRadius.get();
            Vec3 offset = player.position().subtract(cyclone.center).multiply(1, 0, 1);
            Vec3 radial = offset.lengthSqr() < 0.01D
                    ? new Vec3(Math.cos(cyclone.angleRadians), 0, Math.sin(cyclone.angleRadians))
                    : offset.normalize();
            Vec3 travel = new Vec3(-radial.z, 0, radial.x);
            double correction = Math.clamp((orbitRadius - offset.length()) * 0.12D, -0.35D, 0.35D);
            double speed = Math.min(orbitRadius * HeroesEvolvedConfig.COMMON.flightCycloneOrbitSpeed.get(), 2.4D);
            double rise = Math.clamp((cyclone.center.y + CYCLONE_ORBIT_HEIGHT - player.getY()) * 0.20D, -0.25D, 0.25D);
            setPlayerVelocity(player, travel.scale(speed).add(radial.scale(correction)).add(0, rise, 0));
            cyclone.angleRadians = Math.atan2(radial.z, radial.x);
        }
        PlayerFlightData flightData = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        if (!flightData.visualPoseActive()) {
            player.setData(ModDataAttachments.PLAYER_FLIGHT.get(), flightData.withVisualPoseActive(true));
            FlightVisualSyncService.syncToTrackingPlayers(player);
        }
        pullEntitiesIntoCyclone(player, cyclone);
        if (time % 5L == 0L)
            AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_CYCLONE, cyclone.center, cyclone.center, radius);
        if (time % 20L == 0L)
            player.serverLevel().playSound(null, cyclone.center.x, cyclone.center.y, cyclone.center.z,
                    SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.2F, 0.8F);
        player.resetFallDistance();
        if (--cyclone.remainingTicks > 0) return true;
        launchCycloneTargets(player, cyclone);
        ACTIVE_CYCLONES.remove(player.getUUID());
        player.setData(ModDataAttachments.PLAYER_FLIGHT.get(),
                player.getData(ModDataAttachments.PLAYER_FLIGHT.get()).withVisualPoseActive(false));
        FlightVisualSyncService.syncToTrackingPlayers(player);
        // This tick belongs to the finale; normal flight resumes on the next.
        return true;
    }

    private static boolean cycloneTarget(ServerPlayer player, Entity target, Vec3 center, double radius) {
        if (!(target instanceof net.minecraft.world.entity.LivingEntity) || target == player
                || !target.isAlive() || target.isSpectator() || player.isAlliedTo(target)
                || target.isAlliedTo(player) || target.getRootVehicle() == player.getRootVehicle()) return false;
        Vec3 delta = target.position().subtract(center);
        if (delta.x * delta.x + delta.z * delta.z > radius * radius || Math.abs(delta.y) > 16) return false;
        Vec3 from = center.add(0, Math.clamp(target.getY() - center.y + 0.8D, 1.0D, 14.0D), 0);
        return player.serverLevel().clip(new net.minecraft.world.level.ClipContext(from,
                target.getBoundingBox().getCenter(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType()
                == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static void pullEntitiesIntoCyclone(ServerPlayer player, CycloneState cyclone) {
        double radius = HeroesEvolvedConfig.COMMON.flightCycloneRadius.get();
        for (Entity target : player.serverLevel().getEntities(player,
                new AABB(cyclone.center, cyclone.center).inflate(radius, 16, radius),
                entity -> cycloneTarget(player, entity, cyclone.center, radius))) {
            Vec3 offset = target.position().subtract(cyclone.center).multiply(1, 0, 1);
            Vec3 radial = offset.lengthSqr() < 0.001D ? horizontalLookDirection(player) : offset.normalize();
            Vec3 inward = radial.scale(-0.20D * cyclone.strength);
            Vec3 spiral = new Vec3(-radial.z, 0, radial.x).scale(0.22D * cyclone.strength);
            double rise = Math.clamp((cyclone.center.y + 8.0D - target.getY()) * 0.12D, 0.05D, 0.38D);
            pushEntity(target, inward.add(spiral).add(0, rise, 0));
            if (offset.lengthSqr() < 4.0D && player.serverLevel().getGameTime() % 20L == 0L)
                target.hurt(player.damageSources().playerAttack(player), (float) cyclone.strength);
        }
    }

    private static void launchCycloneTargets(ServerPlayer player, CycloneState cyclone) {
        double radius = HeroesEvolvedConfig.COMMON.flightCycloneRadius.get();
        double speed = Math.min(3.2D, HeroesEvolvedConfig.COMMON.flightCycloneLaunchSpeed.get() * cyclone.strength);
        for (Entity target : player.serverLevel().getEntities(player,
                new AABB(cyclone.center, cyclone.center).inflate(radius, 16, radius),
                entity -> cycloneTarget(player, entity, cyclone.center, radius))) {
            Vec3 offset = target.position().subtract(cyclone.center).multiply(1, 0, 1);
            Vec3 outward = offset.lengthSqr() < 0.001D ? horizontalLookDirection(player) : offset.normalize();
            target.hurt(player.damageSources().playerAttack(player), (float) (6.0D * cyclone.strength));
            pushEntity(target, outward.scale(speed).add(0, 0.8D, 0));
        }
        AbilityEffects.send(player.serverLevel(), Kind.DIVE_IMPACT,
                cyclone.center, cyclone.center.add(0, 1, 0), radius);
        player.serverLevel().playSound(null, cyclone.center.x, cyclone.center.y, cyclone.center.z,
                SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.5F, 0.7F);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(player.serverLevel(),
                cyclone.center, radius * 3, 0.7F, 10);
        setPlayerVelocity(player, player.getDeltaMovement().scale(0.3D));
    }

    private static void pushEntity(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        if (entity instanceof ServerPlayer player)
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        else entity.hurtMarked = true;
    }

    private static Vec3 horizontalLookDirection(ServerPlayer player) {
        Vec3 horizontalLook = player.getLookAngle()
                .multiply(1.0D, 0.0D, 1.0D);

        if (horizontalLook.lengthSqr() < 0.0001D) {
            return Vec3.directionFromRotation(0.0F, player.getYRot());
        }

        return horizontalLook.normalize();
    }

    private static void applyLaunchAscentVelocity(ServerPlayer player) {
        Vec3 lookDirection = player.getLookAngle();
        Vec3 horizontalDirection = new Vec3(
                lookDirection.x,
                0.0D,
                lookDirection.z
        ).normalize();

        // Looking straight up or down has no horizontal direction.
        if (horizontalDirection.lengthSqr() < 0.001D) {
            horizontalDirection = new Vec3(0.0D, 0.0D, 1.0D);
        }

        double ascentSpeedPerTick = HeroesEvolvedConfig.COMMON
                .flightAscentSpeed
                .get();

        Vec3 launchVelocity = horizontalDirection
                .scale(Math.cos(LAUNCH_ANGLE_RADIANS) * ascentSpeedPerTick)
                .add(0.0D,
                        Math.sin(LAUNCH_ANGLE_RADIANS) * ascentSpeedPerTick,
                        0.0D
                );

        setPlayerVelocity(player, launchVelocity);
    }

    private static void applyForwardLaunchVelocity(ServerPlayer player) {
        Vec3 lookDirection = player.getLookAngle().normalize();

        double forwardSpeed = HeroesEvolvedConfig.COMMON
                .flightForwardSpeed
                .get();

        // The second burst follows the player's current look direction.
        Vec3 forwardVelocity = lookDirection.scale(forwardSpeed);

        setPlayerVelocity(player, forwardVelocity.scale(
                Math.min(3.6D, forwardVelocity.length()) / Math.max(0.001D, forwardVelocity.length())));
        player.resetFallDistance();
        FlightDynamicsService.beginBurst(player, 12);

        spawnForwardShockwave(player, lookDirection);
    }

    private static void setPlayerVelocity(
            ServerPlayer player,
            Vec3 velocity
    ) {
        boolean changed = player.getDeltaMovement().distanceToSqr(velocity) > 0.0001D;
        player.setDeltaMovement(velocity);
        // Moving impulses need continual synchronization; idle wind-ups only need a heartbeat.
        if (changed || velocity.lengthSqr() > 0.0001D || player.tickCount % 4 == 0)
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static boolean grantFlightPermission(ServerPlayer player) {
        if (player.mayFly()) {
            return false;
        }

        AttributeInstance flightAttribute = player.getAttribute(
                NeoForgeMod.CREATIVE_FLIGHT
        );

        if (flightAttribute == null) {
            throw new IllegalStateException(
                    "Player is missing the creative flight attribute."
            );
        }

        if (flightAttribute.getModifier(
                FLIGHT_PERMISSION_MODIFIER_ID
        ) == null) {
            flightAttribute.addTransientModifier(
                    FLIGHT_PERMISSION_MODIFIER
            );
        }

        return true;
    }

    private static void endSession(
            ServerPlayer player,
            boolean applySafeLanding
    ) {
        PlayerFlightData flightData = player.getData(
                ModDataAttachments.PLAYER_FLIGHT.get()
        );

        if (flightData.grantedMayfly()) {
            removeFlightPermission(player);

            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        player.setData(
                ModDataAttachments.PLAYER_FLIGHT.get(),
                PlayerFlightData.empty()
        );
        ACTIVE_CYCLONES.remove(player.getUUID());
        FlightDynamicsService.forget(player);
        player.resetFallDistance();

        FlightVisualSyncService.syncToTrackingPlayers(player);

        if (applySafeLanding) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.SLOW_FALLING,
                    HeroesEvolvedConfig.COMMON
                            .flightSafeLandingSeconds
                            .get() * 20,
                    0
            ));
        }
    }

    private static void removeFlightPermission(ServerPlayer player) {
        AttributeInstance flightAttribute = player.getAttribute(
                NeoForgeMod.CREATIVE_FLIGHT
        );

        if (flightAttribute != null) {
            flightAttribute.removeModifier(
                    FLIGHT_PERMISSION_MODIFIER_ID
            );
        }
    }

    private static void spawnForwardShockwave(
            ServerPlayer player,
            Vec3 direction
    ) {
        Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_BURST,
                center, center.add(direction), 3.0D);
    }

    private static void triggerFlightBoostSonicBoom(ServerPlayer player) {
        var cooldown = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "flight_sonic_boom");
        if (!com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(player)
                || com.jayptucker.heroesevolved.cooldown.CooldownService.isOnCooldown(player, cooldown)
                || !PlayerEnergyService.tryConsume(player, 5)) return;
        com.jayptucker.heroesevolved.cooldown.CooldownService.startCooldown(player, cooldown, 40);
        MasteryService.awardEnergySpent(player, 5);
        
        Vec3 explosionPosition = player.position().add(0.0D, 0.9D, 0.0D);

        // Damage and knockback are separate explosion paths. Protect the owner,
        // allies and carried passengers from both, including rescue takeoffs.
        java.util.function.Predicate<Entity> protectedEntity = entity -> entity == player
                || player.isAlliedTo(entity) || entity.isAlliedTo(player)
                || com.jayptucker.heroesevolved.entity.EntityCarryService.isBeingCarried(entity);
        ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return !protectedEntity.test(entity);
            }
            @Override public float getKnockbackMultiplier(Entity entity) {
                return protectedEntity.test(entity) ? 0.0F : super.getKnockbackMultiplier(entity);
            }
        };
        // NONE keeps terrain intact and prevents fire.
        player.serverLevel().explode(
                player,
                null,
                calculator,
                explosionPosition.x,
                explosionPosition.y,
                explosionPosition.z,
                2.0F,
                false,
                Level.ExplosionInteraction.NONE,
                ParticleTypes.CLOUD,
                ParticleTypes.CLOUD,
                ModSounds.FLIGHT_BOOST
        );

        spawnForwardShockwave(player, player.getLookAngle());
    }

    private static final class CycloneState {
        Vec3 center;
        double angleRadians;
        int remainingTicks;
        int ascentTicksRemaining;
        int windupTicks;
        final double strength;
        CycloneState(Vec3 center, double angleRadians, int remainingTicks,
                int ascentTicksRemaining, int windupTicks, double strength) {
            this.center = center;
            this.angleRadians = angleRadians;
            this.remainingTicks = remainingTicks;
            this.ascentTicksRemaining = ascentTicksRemaining;
            this.windupTicks = windupTicks;
            this.strength = strength;
        }
    }
}
