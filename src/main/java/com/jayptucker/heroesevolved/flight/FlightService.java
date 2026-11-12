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
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
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
    /** How high up the funnel the flyer circles their Tempest, and how far it draws things in. */
    private static final double CYCLONE_ORBIT_HEIGHT = 8.0D;
    public static final double TEMPEST_PULL = 20.0D;
    /** Fastest the flyer whips round their Tempest, in blocks a tick. */
    private static final double TEMPEST_ORBIT_SPEED = 3.6D;
    private static final int CYCLONE_ASCENT_TICKS = 15;
    public static final int TEMPEST_WINDUP_TICKS = 20;
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
        startFlightSession(player, grounded ? SKYBURST_ASCENT_TICKS : 0, true);
        player.resetFallDistance();
        if (grounded) {
            applyLaunchAscentVelocity(player);
            showRelease(player.serverLevel(), player.position().add(0, .12, 0),
                    player.getDeltaMovement().normalize(), 1.5F);
        } else {
            Vec3 direction = player.getLookAngle().normalize();
            setPlayerVelocity(player, direction.scale(Math.min(3.6D,
                    HeroesEvolvedConfig.COMMON.flightForwardSpeed.get())));
            FlightDynamicsService.beginBurst(player, 12);
            Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
            showRelease(player.serverLevel(), center, direction, 1.6F);
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
        Vec3 at = player.position().add(0, .15, 0);
        SetPiecePayload.send(player.serverLevel(), Piece.FLIGHT_INTERCEPT, at, at, .85F, null);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, .35F, 1.45F);
        return true;
    }

    /**
     * Tempest: a tornado touches down on the ground ahead (however high you are flying), rises over its wind-up,
     * and you circle it partway up its funnel. Look to steer it across the ground; it draws in everything for
     * twenty blocks, carries it up its wall, then throws its captives clear. It wears the sky's colours.
     */
    public static void startCyclone(ServerPlayer player) {
        FlightDynamicsService.cancelSkyburst(player);
        double strength = 1.0D + FlightDynamicsService.consumeMomentum(player) / 200.0D;
        Vec3 horizontalLook = horizontalLookDirection(player);
        double orbitRadius = HeroesEvolvedConfig.COMMON.flightCycloneOrbitRadius.get();
        Vec3 ahead = player.position().add(horizontalLook.scale(orbitRadius));
        Vec3 center = ground(player.serverLevel(), ahead, 4,
                Math.max(0, BlockPos.containing(ahead).getY() - player.level().getMinBuildHeight()));
        if (center == null) center = ahead;
        Vec3 fromCenter = player.position().subtract(center);
        startFlightSession(player, 0);
        ACTIVE_CYCLONES.put(player.getUUID(), new CycloneState(center,
                Math.atan2(fromCenter.z, fromCenter.x),
                HeroesEvolvedConfig.COMMON.flightCycloneDurationSeconds.get() * 20,
                CYCLONE_ASCENT_TICKS, TEMPEST_WINDUP_TICKS, strength));
        setPlayerVelocity(player, Vec3.ZERO);
        syncTempest(player, ACTIVE_CYCLONES.get(player.getUUID()), false);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.0F, 0.75F);
        player.serverLevel().playSound(null, center.x, center.y, center.z,
                SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.4F, 0.6F);
        MasteryService.markPowerUse(player, ModAbilities.FLIGHT_ID);
        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    /** Shift + V always cancels for free, including during the wind-up. */
    public static boolean cancelCyclone(ServerPlayer player) {
        CycloneState cyclone = ACTIVE_CYCLONES.remove(player.getUUID());
        if (cyclone == null) return false;
        syncTempest(player, cyclone, true);
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
        if (FlightDynamicsService.isCharging(player) || FlightDynamicsService.isBursting(player)
                || DiveBombService.isDiving(player)
                || FlightSkillService.isRolling(player) || FlightSkillService.isChanneling(player)) return;
        if (!movingForward && data.visualPoseActive())
            setPlayerVelocity(player, player.getDeltaMovement().scale(.45));
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
        if (!FlightDynamicsService.isBursting(player)) {
            if (step == 0 && data.speedStep() > 0) setPlayerVelocity(player, player.getDeltaMovement().scale(.25D));
            refreshSpeedState(player);
        }
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
        startFlightSession(player, launchDelayTicks, false);
    }

    private static void startFlightSession(ServerPlayer player, int launchDelayTicks, boolean preserveCruise) {
        PlayerFlightData current = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        boolean grantedMayfly = current.grantedMayfly() || grantFlightPermission(player);

        player.getAbilities().flying = true;
        player.onUpdateAbilities();

        player.setData(
                ModDataAttachments.PLAYER_FLIGHT.get(),
                preserveCruise && current.flightEnabled() ? current.enableFlight(grantedMayfly)
                        .withLaunchTicksRemaining(launchDelayTicks).withVisualPoseActive(false)
                        : PlayerFlightData.empty().startLaunch(grantedMayfly, launchDelayTicks)
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

        if (!cycloneActive && !FlightDynamicsService.isCharging(player) && !FlightDynamicsService.isBursting(player)
                && !DiveBombService.isDiving(player)
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
                    player, 0x25F0EFE7, 14,
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
        double alignment = currentVelocity.lengthSqr() < .01 ? 1 : currentVelocity.normalize().dot(lookDirection);
        double steering = .20 + .16 * (1 - Math.max(0, alignment));
        setPlayerVelocity(player, currentVelocity.lerp(lookDirection.scale(desiredCruiseSpeed), steering));
    }

    private static boolean tickCyclone(ServerPlayer player) {
        CycloneState cyclone = ACTIVE_CYCLONES.get(player.getUUID());
        if (cyclone == null) return false;
        double radius = HeroesEvolvedConfig.COMMON.flightCycloneRadius.get();
        long time = player.serverLevel().getGameTime();
        cyclone.age++;
        if (cyclone.age % 2 == 0) syncTempest(player, cyclone, false);
        if (cyclone.windupTicks > 0) {
            // The funnel touches down and starts to rise while the flyer gathers the wind.
            setPlayerVelocity(player, Vec3.ZERO);
            player.resetFallDistance();
            cyclone.windupTicks--;
            return true;
        }
        // Looking steers it across the ground; forward input and wheel speed never override its orbit.
        Vec3 candidate = cyclone.center.add(horizontalLookDirection(player).scale(0.16D));
        Vec3 landed = ground(player.serverLevel(), candidate, 3, 12);
        if (landed != null) cyclone.center = landed;
        if (cyclone.ascentTicksRemaining > 0 || !cyclone.orbiting && Math.abs(cyclone.center.y + CYCLONE_ORBIT_HEIGHT - player.getY()) > 1.5) {
            double ascent = cyclone.center.y + CYCLONE_ORBIT_HEIGHT - player.getY();
            Vec3 approach = new Vec3(0, Math.clamp(ascent * .35, -3, 1.4), 0);
            setPlayerVelocity(player, SpeedService.swept(player, approach).subtract(player.position()));
            if (cyclone.ascentTicksRemaining > 0) cyclone.ascentTicksRemaining--;
        } else {
            // Whip round the funnel partway up, just outside its wall there, as fast as the wind itself: about three
            // and a half blocks a tick, a lap in well under a second. An ideal point runs round the circle and the
            // flyer is carried along with it, nudged gently back toward it: aiming straight at it from a position the
            // client reported a tick ago overshoots at this speed.
            float height = com.jayptucker.heroesevolved.speed.TornadoShape.height(cyclone.age);
            double orbitRadius = Math.max(HeroesEvolvedConfig.COMMON.flightCycloneOrbitRadius.get(),
                    com.jayptucker.heroesevolved.speed.TornadoShape.radius((float) CYCLONE_ORBIT_HEIGHT, height, (float) radius) + 4.0D);
            if (!cyclone.orbiting) {
                Vec3 offset = player.position().subtract(cyclone.center).multiply(1, 0, 1);
                if (offset.lengthSqr() > 0.01D) cyclone.angleRadians = Math.atan2(offset.z, offset.x);
                cyclone.orbiting = true;
            }
            double speed = Math.min(orbitRadius * HeroesEvolvedConfig.COMMON.flightCycloneOrbitSpeed.get() * 3.0D, TEMPEST_ORBIT_SPEED);
            double angle = cyclone.angleRadians, next = angle + speed / orbitRadius;
            Vec3 ideal = cyclone.center.add(Math.cos(angle) * orbitRadius, CYCLONE_ORBIT_HEIGHT, Math.sin(angle) * orbitRadius);
            Vec3 ahead = cyclone.center.add(Math.cos(next) * orbitRadius, CYCLONE_ORBIT_HEIGHT, Math.sin(next) * orbitRadius);
            Vec3 correction = ideal.subtract(player.position()).scale(0.3D);
            if (correction.length() > 1.2D) correction = correction.normalize().scale(1.2D);
            Vec3 orbit = ahead.subtract(ideal).add(correction);
            setPlayerVelocity(player, SpeedService.swept(player, orbit).subtract(player.position()));
            cyclone.angleRadians = next;
        }
        PlayerFlightData flightData = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        if (!flightData.visualPoseActive()) {
            player.setData(ModDataAttachments.PLAYER_FLIGHT.get(), flightData.withVisualPoseActive(true));
            FlightVisualSyncService.syncToTrackingPlayers(player);
        }
        com.jayptucker.heroesevolved.speed.TornadoWind.blowTempest(player, player.serverLevel(), cyclone.center, cyclone.age,
                (float) radius, TEMPEST_PULL, (float) cyclone.strength * 1.5F, false, cyclone.caught, time);
        if (time % 20L == 0L)
            player.serverLevel().playSound(null, cyclone.center.x, cyclone.center.y, cyclone.center.z,
                    SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.2F, 0.8F);
        player.resetFallDistance();
        if (--cyclone.remainingTicks > 0) return true;
        launchCycloneTargets(player, cyclone);
        ACTIVE_CYCLONES.remove(player.getUUID());
        syncTempest(player, cyclone, true);
        player.setData(ModDataAttachments.PLAYER_FLIGHT.get(),
                player.getData(ModDataAttachments.PLAYER_FLIGHT.get()).withVisualPoseActive(false));
        FlightVisualSyncService.syncToTrackingPlayers(player);
        // This tick belongs to the finale; normal flight resumes on the next.
        return true;
    }

    private static boolean cycloneTarget(ServerPlayer player, Entity target, CycloneState cyclone, double radius) {
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living)
                || !FlightSkillService.hostile(player, living)) return false;
        Vec3 center = cyclone.center;
        Vec3 delta = target.position().subtract(center);
        return (cyclone.caught.contains(target.getUUID()) || delta.horizontalDistanceSqr() <= radius * radius)
                && com.jayptucker.heroesevolved.speed.TornadoWind.exposed(player, center, target);
    }

    /** Tells everyone near enough what the Tempest's tornado is doing: where it stands, how far it has risen. */
    private static void syncTempest(ServerPlayer player, CycloneState cyclone, boolean ended) {
        if (cyclone == null) return;
        com.jayptucker.heroesevolved.network.SpeedCyclonePayload.send(player.serverLevel(), player, cyclone.center,
                HeroesEvolvedConfig.COMMON.flightCycloneRadius.get().floatValue(), cyclone.age, cyclone.age, ended, true,
                com.jayptucker.heroesevolved.network.SpeedCyclonePayload.FLIGHT);
    }

    /**
     * The ground under {@code at}: the top of the first solid block (or water) found from {@code up} blocks above
     * to {@code down} below, or null if there is none.
     */
    public static Vec3 ground(ServerLevel level, Vec3 at, int up, int down) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + up, at.z).mutable();
        if (!level.hasChunkAt(pos)) return null;
        for (int i = 0; i <= up + down; i++, pos.move(net.minecraft.core.Direction.DOWN)) {
            if (pos.getY() <= level.getMinBuildHeight()) return null;
            BlockPos below = pos.below();
            boolean floor = !level.getBlockState(below).getCollisionShape(level, below).isEmpty() || !level.getFluidState(below).isEmpty();
            boolean open = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
            if (floor && open) return new Vec3(at.x, pos.getY(), at.z);
        }
        return null;
    }

    private static void launchCycloneTargets(ServerPlayer player, CycloneState cyclone) {
        double radius = HeroesEvolvedConfig.COMMON.flightCycloneRadius.get();
        double speed = Math.min(3.2D, HeroesEvolvedConfig.COMMON.flightCycloneLaunchSpeed.get() * cyclone.strength);
        for (Entity target : player.serverLevel().getEntities(player,
                new AABB(cyclone.center, cyclone.center).inflate(Math.max(radius, TEMPEST_PULL), 30, Math.max(radius, TEMPEST_PULL)),
                entity -> cycloneTarget(player, entity, cyclone, radius))) {
            Vec3 offset = target.position().subtract(cyclone.center).multiply(1, 0, 1);
            Vec3 outward = offset.lengthSqr() < 0.001D ? horizontalLookDirection(player) : offset.normalize();
            target.hurt(player.damageSources().playerAttack(player), (float) (6.0D * cyclone.strength));
            pushEntity(target, outward.scale(speed).add(0, 0.8D, 0));
        }
        Vec3 center = cyclone.center.add(0, .12, 0);
        SetPiecePayload.send(player.serverLevel(), Piece.FLIGHT_IMPACT, center, center.add(0, 1, 0), (float) radius, null);
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
        syncTempest(player, ACTIVE_CYCLONES.remove(player.getUUID()), true);
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
        showRelease(player.serverLevel(), center, direction, 2.1F);
    }

    public static void showRelease(ServerLevel level, Vec3 center, Vec3 direction, float strength) {
        SetPiecePayload.send(level, Piece.FLIGHT_RELEASE, center, center.add(direction), strength, null);
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
        /** Ticks since it touched down (its funnel rises with this), and what its wind has caught. */
        int age;
        /** The flyer has started circling (the ideal point round the funnel is now theirs to follow). */
        boolean orbiting;
        final java.util.Set<UUID> caught = new java.util.HashSet<>();
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
