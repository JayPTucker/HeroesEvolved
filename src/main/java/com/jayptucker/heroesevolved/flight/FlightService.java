package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.OverexertionService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class FlightService {
    private static final double LAUNCH_ANGLE_RADIANS =
            Math.toRadians(75.0D);

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

    // Flight Boost is an offensive shockwave, but its owner is excluded so
    // activating the power cannot damage the flying player.
    private static final ExplosionDamageCalculator
            FLIGHT_BOOST_EXPLOSION_DAMAGE_CALCULATOR =
            new ExplosionDamageCalculator() {
                @Override
                public boolean shouldDamageEntity(
                        Explosion explosion,
                        Entity entity
                ) {
                    return entity != explosion.getDirectSourceEntity();
                }
            };

    private FlightService() {
    }

    public static boolean hasActiveSession(ServerPlayer player) {
        return player.getData(
                ModDataAttachments.PLAYER_FLIGHT.get()
        ).sessionActive();
    }

    public static void stopForPowerRemoval(ServerPlayer player) {
        if (hasActiveSession(player)) {
            endSession(player, false);
        }
    }

    public static void launch(ServerPlayer player) {
        int ascentDurationTicks = HeroesEvolvedConfig.COMMON
                .flightLaunchAscentDurationSeconds
                .get() * 20;

        startFlightSession(player, ascentDurationTicks);
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

    public static void setForwardInput(
            ServerPlayer player,
            boolean movingForward
    ) {
        PlayerFlightData flightData = player.getData(
                ModDataAttachments.PLAYER_FLIGHT.get()
        );

        if (!flightData.flightEnabled()) {
            return;
        }

        boolean startingBoost = movingForward
                && !flightData.visualPoseActive()
                && !player.onGround();
        PlayerFlightData updatedData = flightData.withVisualPoseActive(
                movingForward && !player.onGround()
        );

        if (updatedData == flightData) {
            return;
        }

        player.setData(
                ModDataAttachments.PLAYER_FLIGHT.get(),
                updatedData
        );

        if (startingBoost) {
            triggerFlightBoostSonicBoom(player);
        }

        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    private static void startFlightSession(
            ServerPlayer player,
            int launchDelayTicks
    ) {
        boolean grantedMayfly = grantFlightPermission(player);

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

        // Flight is an active power. End its session safely as soon as the
        // Eclipse suppresses powers, including applying no fall-damage risk.
        if (EclipseService.arePowersSuppressed(player)) {
            if (flightData.sessionActive()) {
                endSession(player, false);
            }
            return;
        }

        if (!flightData.sessionActive()) {
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
        } else if (flightData.hasBeenAirborne()) {
            endSession(player, false);
            return;
        }

        flightData = handlePendingForwardLaunch(player, flightData);

        // Launch has its own one-time cost. Sustained-flight drain begins
        // only after its upward/forward launch movement has finished.
        if (flightData.launchTicksRemaining() > 0) {
            return;
        }

        applyForwardFlightMovement(player, flightData);

        long gameTime = player.serverLevel().getGameTime();

        // A contrail belongs to the forward Flight pose, not to hovering.
        if (flightData.visualPoseActive()
                && gameTime % HeroesEvolvedConfig.COMMON
                .flightTrailIntervalTicks.get() == 0) {
            spawnContrail(player);
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

        PlayerEnergyService.tryConsume(player, energyDrain);
    }

    private static PlayerFlightData handlePendingForwardLaunch(
            ServerPlayer player,
            PlayerFlightData flightData
    ) {
        if (flightData.launchTicksRemaining() <= 0) {
            return flightData;
        }

        // This is a sustained ascent, not a one-tick impulse. Reapplying the
        // controlled velocity each tick creates a smooth six-second climb.
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
        if (!flightData.visualPoseActive()) {
            return;
        }

        Vec3 lookDirection = player.getLookAngle().normalize();
        Vec3 currentVelocity = player.getDeltaMovement();
        double desiredCruiseSpeed = HeroesEvolvedConfig.COMMON
                .flightCruiseSpeed
                .get();
        double currentForwardSpeed = currentVelocity.dot(lookDirection);

        // Launch momentum is preserved. Once it slows below cruising speed,
        // holding forward keeps the player moving exactly where they look.
        if (currentForwardSpeed < desiredCruiseSpeed) {
            setPlayerVelocity(
                    player,
                    currentVelocity.add(lookDirection.scale(
                            desiredCruiseSpeed - currentForwardSpeed
                    ))
            );
        }
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

        setPlayerVelocity(
                player,
                player.getDeltaMovement().add(forwardVelocity)
        );

        spawnForwardShockwave(player, lookDirection);
    }

    private static void setPlayerVelocity(
            ServerPlayer player,
            Vec3 velocity
    ) {
        player.setDeltaMovement(velocity);

        // Explicitly synchronizes the launch movement to the controlling client.
        player.connection.send(
                new ClientboundSetEntityMotionPacket(player)
        );
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
        ServerLevel level = player.serverLevel();
        Vec3 normalizedDirection = direction.normalize();

        // This basis creates a ring perpendicular to travel direction.
        // For horizontal flight, the result is a vertical smoke ring.
        Vec3 referenceAxis = Math.abs(normalizedDirection.y) > 0.9D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);

        Vec3 right = normalizedDirection.cross(referenceAxis).normalize();
        Vec3 up = right.cross(normalizedDirection).normalize();
        Vec3 ringCenter = player.position().add(0.0D, 1.0D, 0.0D);

        for (int index = 0; index < 32; index++) {
            double angle = (Math.PI * 2.0D * index) / 32.0D;
            double radius = 2.0D;
            Vec3 particlePosition = ringCenter
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));

            level.sendParticles(
                    ParticleTypes.CLOUD,
                    particlePosition.x,
                    particlePosition.y,
                    particlePosition.z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.04D
            );
        }
    }

    private static void triggerFlightBoostSonicBoom(ServerPlayer player) {
        Vec3 explosionPosition = player.position().add(0.0D, 0.9D, 0.0D);

        // NONE keeps terrain intact and prevents fire, while the custom
        // calculator still allows the blast to hurt nearby entities.
        player.serverLevel().explode(
                player,
                null,
                FLIGHT_BOOST_EXPLOSION_DAMAGE_CALCULATOR,
                explosionPosition.x,
                explosionPosition.y,
                explosionPosition.z,
                5.0F,
                false,
                Level.ExplosionInteraction.NONE,
                ParticleTypes.EXPLOSION,
                ParticleTypes.EXPLOSION_EMITTER,
                SoundEvents.GENERIC_EXPLODE
        );

        spawnForwardShockwave(player, player.getLookAngle());
    }

    private static void spawnContrail(ServerPlayer player) {
        // The rendered Flight pose places the player's feet behind and above
        // their normal standing position. Match that visual position so the
        // server-synchronized trail originates beneath the animated feet.
        Vec3 horizontalLook = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);

        if (horizontalLook.lengthSqr() < 0.0001D) {
            horizontalLook = Vec3.directionFromRotation(0.0F, player.getYRot());
        }

        Vec3 particlePosition = player.position()
                .add(horizontalLook.normalize().scale(-0.85D))
                .add(0.0D, 0.80D, 0.0D);

        player.serverLevel().sendParticles(
                ParticleTypes.CLOUD,
                particlePosition.x,
                particlePosition.y,
                particlePosition.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
    }
}
