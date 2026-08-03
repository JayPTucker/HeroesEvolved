package com.jayptucker.heroesevolved.flight;

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
import com.jayptucker.heroesevolved.particles.ModParticles;
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
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FlightService {
    private static final double LAUNCH_ANGLE_RADIANS =
            Math.toRadians(75.0D);
    private static final double CYCLONE_ORBIT_HEIGHT = 3.0D;
    private static final int CYCLONE_ASCENT_TICKS = 15;
    private static final double TRAIL_PARTICLE_SPACING = 0.35D;
    private static final double TRAIL_RESET_DISTANCE = 12.0D;
    private static final int MAX_TRAIL_PARTICLES_PER_EMISSION = 16;

    // Cyclones are short-lived combat effects. Keeping their active state in
    // memory avoids persisting an incomplete tornado across a server restart.
    private static final Map<UUID, CycloneState> ACTIVE_CYCLONES =
            new HashMap<>();

    // The previous emission point lets the server fill the distance travelled
    // in one tick, keeping a fast Flight Boost contrail continuous.
    private static final Map<UUID, Vec3> LAST_CONTRAIL_POSITIONS =
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

    public static boolean isCycloneActive(ServerPlayer player) {
        return ACTIVE_CYCLONES.containsKey(player.getUUID());
    }

    public static void stopForPowerRemoval(ServerPlayer player) {
        if (hasActiveSession(player)) {
            endSession(player, false);
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

        int ascentDurationTicks = HeroesEvolvedConfig.COMMON
                .flightLaunchAscentDurationSeconds
                .get() * 20;

        startFlightSession(player, ascentDurationTicks);
        MasteryService.awardPowerUse(player, ModAbilities.FLIGHT_ID);
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

    public static void startCyclone(ServerPlayer player) {
        Vec3 horizontalLook = horizontalLookDirection(player);
        double orbitRadius = HeroesEvolvedConfig.COMMON
                .flightCycloneOrbitRadius
                .get();

        // The player begins on the outside of the circle, while the tornado
        // forms ahead of them. Its base stays at ground height throughout.
        Vec3 center = player.position()
                .add(horizontalLook.scale(orbitRadius));
        Vec3 fromCenter = player.position().subtract(center);
        double startingAngle = Math.atan2(fromCenter.z, fromCenter.x);

        startFlightSession(player, 0);
        player.setData(
                ModDataAttachments.PLAYER_FLIGHT.get(),
                player.getData(ModDataAttachments.PLAYER_FLIGHT.get())
                        .withVisualPoseActive(true)
        );
        ACTIVE_CYCLONES.put(
                player.getUUID(),
                new CycloneState(
                        center,
                        startingAngle,
                        HeroesEvolvedConfig.COMMON
                                .flightCycloneDurationSeconds
                                .get() * 20,
                        CYCLONE_ASCENT_TICKS
                )
        );

        MasteryService.awardPowerUse(player, ModAbilities.FLIGHT_ID);
        FlightVisualSyncService.syncToTrackingPlayers(player);
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

        // Cyclone controls its own server-side path. Boost input must not
        // re-enable the horizontal Flight pose during that sequence.
        if (ACTIVE_CYCLONES.containsKey(player.getUUID())) {
            return;
        }

        boolean boostUnlocked = PlayerProgressionService.getLevel(player) >= 2;
        boolean boostActive = movingForward && boostUnlocked;
        boolean startingBoost = boostActive
                && !flightData.visualPoseActive()
                && !player.onGround();
        PlayerFlightData updatedData = flightData.withVisualPoseActive(
                boostActive && !player.onGround()
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

        boolean cycloneActive = tickCyclone(player);
        flightData = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());

        if (!cycloneActive) {
            applyForwardFlightMovement(player, flightData);
        }

        if (flightData.visualPoseActive()) {
            MasteryService.awardPowerUse(player, ModAbilities.FLIGHT_ID);
        }

        long gameTime = player.serverLevel().getGameTime();

        // Cyclone uses the Flight Boost pose again, so its contrail uses the
        // same animated-foot position as high-speed forward flight.
        boolean contrailActive = flightData.visualPoseActive() || cycloneActive;

        if (contrailActive
                && gameTime % HeroesEvolvedConfig.COMMON
                .flightTrailIntervalTicks.get() == 0) {
            spawnContrail(player, false);
        } else if (!contrailActive) {
            LAST_CONTRAIL_POSITIONS.remove(player.getUUID());
        }

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

    private static boolean tickCyclone(ServerPlayer player) {
        CycloneState cyclone = ACTIVE_CYCLONES.get(player.getUUID());

        if (cyclone == null) {
            return false;
        }

        double nextAngle = cyclone.angleRadians();

        if (cyclone.ascentTicksRemaining() > 0) {
            // Rise cleanly from the ground before moving sideways. Keeping
            // the orbit at a fixed height prevents the former up/down wobble.
            double remainingAscent = cyclone.center().y + CYCLONE_ORBIT_HEIGHT
                    - player.getY();
            setPlayerVelocity(
                    player,
                    new Vec3(0.0D, Math.clamp(remainingAscent, 0.0D, 0.25D), 0.0D)
            );
        } else {
            double orbitRadius = HeroesEvolvedConfig.COMMON
                    .flightCycloneOrbitRadius
                    .get();
            Vec3 offset = player.position().subtract(cyclone.center());
            Vec3 horizontalOffset = new Vec3(offset.x, 0.0D, offset.z);

            // The orbit is velocity-driven rather than snapping the player to
            // a mathematical point every tick. That prevents high orbit-speed
            // settings from producing client/server position corrections.
            Vec3 radialDirection = horizontalOffset.lengthSqr() < 0.01D
                    ? new Vec3(
                            Math.cos(cyclone.angleRadians()),
                            0.0D,
                            Math.sin(cyclone.angleRadians())
                    )
                    : horizontalOffset.normalize();
            Vec3 travelDirection = new Vec3(
                    -radialDirection.z,
                    0.0D,
                    radialDirection.x
            );
            double radialError = orbitRadius - horizontalOffset.length();
            double radialCorrection = Math.clamp(
                    radialError * 0.12D,
                    -0.45D,
                    0.45D
            );
            double tangentialSpeed = Math.min(
                    orbitRadius * HeroesEvolvedConfig.COMMON
                            .flightCycloneOrbitSpeed
                            .get(),
                    6.5D
            );
            double verticalCorrection = Math.clamp(
                    (cyclone.center().y + CYCLONE_ORBIT_HEIGHT - player.getY())
                            * 0.20D,
                    -0.25D,
                    0.25D
            );

            setPlayerVelocity(
                    player,
                    travelDirection.scale(tangentialSpeed)
                            .add(radialDirection.scale(radialCorrection))
                            .add(0.0D, verticalCorrection, 0.0D)
            );
            faceTravelDirection(player, travelDirection);
            nextAngle = Math.atan2(radialDirection.z, radialDirection.x);
        }
        pullEntitiesIntoCyclone(player, cyclone.center());
        spawnCycloneParticles(player.serverLevel(), cyclone.center());

        int remainingTicks = cyclone.remainingTicks() - 1;

        if (remainingTicks <= 0) {
            launchCycloneTargets(player, cyclone.center());
            ACTIVE_CYCLONES.remove(player.getUUID());
            player.setData(
                    ModDataAttachments.PLAYER_FLIGHT.get(),
                    player.getData(ModDataAttachments.PLAYER_FLIGHT.get())
                            .withVisualPoseActive(false)
            );
            FlightVisualSyncService.syncToTrackingPlayers(player);
            return false;
        }

        ACTIVE_CYCLONES.put(
                player.getUUID(),
                new CycloneState(
                        cyclone.center(),
                        nextAngle,
                        remainingTicks,
                        Math.max(0, cyclone.ascentTicksRemaining() - 1)
                )
        );
        return true;
    }

    private static void pullEntitiesIntoCyclone(
            ServerPlayer player,
            Vec3 center
    ) {
        double radius = HeroesEvolvedConfig.COMMON.flightCycloneRadius.get();
        List<Entity> targets = player.serverLevel().getEntities(
                player,
                new AABB(center, center).inflate(radius, 20.0D, radius),
                entity -> entity.isAlive() && !entity.isSpectator()
        );

        for (Entity target : targets) {
            Vec3 offset = target.position().subtract(center);
            Vec3 horizontalOffset = new Vec3(offset.x, 0.0D, offset.z);
            double horizontalDistance = horizontalOffset.length();

            if (horizontalDistance > radius || Math.abs(offset.y) > 20.0D) {
                continue;
            }

            Vec3 horizontalDirection = horizontalDistance < 0.001D
                    ? horizontalLookDirection(player)
                    : horizontalOffset.scale(1.0D / horizontalDistance);
            Vec3 inwardVelocity = horizontalDirection.scale(-0.28D);
            Vec3 spiralVelocity = new Vec3(
                    -horizontalDirection.z,
                    0.0D,
                    horizontalDirection.x
            ).scale(0.24D);
            double verticalVelocity = Math.clamp(
                    (center.y + 13.0D - target.getY()) * 0.12D,
                    0.08D,
                    0.45D
            );

            target.setDeltaMovement(
                    inwardVelocity.add(spiralVelocity)
                            .add(0.0D, verticalVelocity, 0.0D)
            );

            // Targets caught close to the center take steady damage while
            // trapped, then are thrown away when the tornado dissipates.
            if (horizontalDistance < 1.75D
                    && player.serverLevel().getGameTime() % 20L == 0L) {
                target.hurt(player.damageSources().playerAttack(player), 1.0F);
            }
        }
    }

    private static void launchCycloneTargets(
            ServerPlayer player,
            Vec3 center
    ) {
        double radius = HeroesEvolvedConfig.COMMON.flightCycloneRadius.get();
        double launchSpeed = HeroesEvolvedConfig.COMMON
                .flightCycloneLaunchSpeed
                .get();

        for (Entity target : player.serverLevel().getEntities(
                player,
                new AABB(center, center).inflate(radius, 22.0D, radius),
                entity -> entity.isAlive() && !entity.isSpectator()
        )) {
            Vec3 offset = target.position().subtract(center);
            Vec3 horizontalOffset = new Vec3(offset.x, 0.0D, offset.z);

            if (horizontalOffset.lengthSqr() > radius * radius) {
                continue;
            }

            Vec3 outwardDirection = horizontalOffset.lengthSqr() < 0.001D
                    ? horizontalLookDirection(player)
                    : horizontalOffset.normalize();
            target.setDeltaMovement(
                    outwardDirection.scale(launchSpeed).add(0.0D, 1.1D, 0.0D)
            );
        }
    }

    private static void spawnCycloneParticles(ServerLevel level, Vec3 center) {
        long gameTime = level.getGameTime();

        if (gameTime % 2L != 0L) {
            return;
        }

        for (int layer = 0; layer < 18; layer++) {
            double height = layer;
            // A tornado is narrow near the ground and broadens toward its top.
            double radius = 0.6D + layer * 0.30D;
            double angle = gameTime * 0.35D + layer * 1.2D;

            for (int side = 0; side < 2; side++) {
                double particleAngle = angle + side * Math.PI;

                level.sendParticles(
                        ParticleTypes.CLOUD,
                        center.x + Math.cos(particleAngle) * radius,
                        center.y + height,
                        center.z + Math.sin(particleAngle) * radius,
                        1,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.015D
                );
            }
        }
    }

    private static Vec3 horizontalLookDirection(ServerPlayer player) {
        Vec3 horizontalLook = player.getLookAngle()
                .multiply(1.0D, 0.0D, 1.0D);

        if (horizontalLook.lengthSqr() < 0.0001D) {
            return Vec3.directionFromRotation(0.0F, player.getYRot());
        }

        return horizontalLook.normalize();
    }

    private static void faceTravelDirection(
            ServerPlayer player,
            Vec3 direction
    ) {
        float yaw = (float) Math.toDegrees(
                Math.atan2(-direction.x, direction.z)
        );

        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
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
        ACTIVE_CYCLONES.remove(player.getUUID());
        LAST_CONTRAIL_POSITIONS.remove(player.getUUID());

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
                    // Reuse the registered white contrail particle so the
                    // sonic boom reads as a bright, lingering vapor ring.
                    ModParticles.WHITE_CONTRAIL_SMOKE.get(),
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
                2.0F,
                false,
                Level.ExplosionInteraction.NONE,
                ParticleTypes.EXPLOSION,
                ParticleTypes.EXPLOSION_EMITTER,
                ModSounds.FLIGHT_BOOST
        );

        spawnForwardShockwave(player, player.getLookAngle());
    }

    private static void spawnContrail(
            ServerPlayer player,
            boolean cycloneActive
    ) {
        Vec3 particlePosition;

        if (cycloneActive) {
            // Cyclone uses the normal upright model, so its contrail belongs
            // directly beneath the player's ordinary feet.
            particlePosition = player.position()
                    .add(horizontalLookDirection(player).scale(-0.35D))
                    .add(0.0D, 0.15D, 0.0D);
        } else {
            // Entity position is the player's feet. Keep the server-side
            // trail near that point so it follows the animated feet instead
            // of cutting through the middle of the flying body.
            particlePosition = player.position()
                    .add(horizontalLookDirection(player).scale(-0.85D))
                    .add(0.0D, 0.15D, 0.0D);
        }

        Vec3 previousPosition = LAST_CONTRAIL_POSITIONS.put(
                player.getUUID(),
                particlePosition
        );

        if (previousPosition == null
                || previousPosition.distanceToSqr(particlePosition)
                > TRAIL_RESET_DISTANCE * TRAIL_RESET_DISTANCE) {
            sendContrailParticle(player.serverLevel(), particlePosition);
            return;
        }

        double distance = previousPosition.distanceTo(particlePosition);
        int particleCount = Math.clamp(
                (int) Math.ceil(distance / TRAIL_PARTICLE_SPACING),
                1,
                MAX_TRAIL_PARTICLES_PER_EMISSION
        );

        // Each particle is placed along the travelled path instead of all
        // spawning at the player. This removes visible gaps at high speed.
        for (int index = 1; index <= particleCount; index++) {
            Vec3 interpolatedPosition = previousPosition.lerp(
                    particlePosition,
                    (double) index / particleCount
            );
            sendContrailParticle(player.serverLevel(), interpolatedPosition);
        }
    }

    private static void sendContrailParticle(
            ServerLevel level,
            Vec3 position
    ) {
        // Normal server particle broadcasts stop at a short distance. A
        // contrail is a long-lived world effect, so force-send it to every
        // player in this dimension; each client can still apply its own
        // particle settings when rendering it.
        for (ServerPlayer viewer : level.players()) {
            level.sendParticles(
                    viewer,
                    ModParticles.WHITE_CONTRAIL_SMOKE.get(),
                    true,
                    position.x,
                    position.y,
                    position.z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private record CycloneState(
            Vec3 center,
            double angleRadians,
            int remainingTicks,
            int ascentTicksRemaining
    ) {
    }
}
