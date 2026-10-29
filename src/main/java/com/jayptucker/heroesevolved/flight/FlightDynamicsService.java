package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.FlightVisualSyncService;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Flight's transient Momentum and tap-or-hold Skyburst. No state survives a flight session. */
public final class FlightDynamicsService {
    public static final double MAX_MOMENTUM = 100.0D;
    public static final int SKYBURST_TAP_TICKS = 6;
    public static final int SKYBURST_FULL_CHARGE_TICKS = 30;
    public static final int SKYBURST_AUTO_RELEASE_TICKS = 40;
    private static final Map<UUID, Dynamics> STATES = new HashMap<>();

    private FlightDynamicsService() {}

    public static double momentum(ServerPlayer player) {
        Dynamics dynamics = STATES.get(player.getUUID());
        return dynamics == null ? 0 : dynamics.momentum;
    }

    /** Learned techniques can bank airspeed, but cannot create a Flight session or exceed its reserve. */
    public static void addMomentum(ServerPlayer player, double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || !eligible(player) || !FlightService.hasActiveSession(player)) return;
        Dynamics dynamics = state(player);
        dynamics.momentum = Math.clamp(dynamics.momentum + amount, 0, MAX_MOMENTUM);
        announce(player, dynamics);
        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    public static int skyburstChargeTicks(ServerPlayer player) {
        return com.jayptucker.heroesevolved.skills.SkillTreeService.has(player, "flight_1_2") ? 18 : SKYBURST_FULL_CHARGE_TICKS;
    }

    /** Spending is shared by Skyburst, Dive Bomb and Tempest. */
    public static double consumeMomentum(ServerPlayer player) {
        Dynamics dynamics = STATES.get(player.getUUID());
        if (dynamics == null) return 0;
        double spent = dynamics.momentum;
        dynamics.momentum = 0;
        dynamics.announcedTier = 0;
        return spent;
    }

    public static boolean isCharging(ServerPlayer player) {
        Dynamics dynamics = STATES.get(player.getUUID());
        return dynamics != null && dynamics.charging;
    }

    public static boolean isBursting(ServerPlayer player) {
        Dynamics dynamics = STATES.get(player.getUUID());
        return dynamics != null && dynamics.burstTicks > 0;
    }

    /** A brief horizontal flight pose also preserves the released burst's direction. */
    public static void beginBurst(ServerPlayer player, int ticks) {
        if (ticks <= 0 || !FlightService.hasActiveSession(player)) return;
        Dynamics dynamics = state(player);
        dynamics.burstTicks = Math.clamp(ticks, 1, 40);
        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    public static void pressSkyburst(ServerPlayer player) {
        if (!eligible(player) || isCharging(player) || FlightService.isCycloneActive(player)
                || DiveBombService.isDiving(player) || FlightSkillService.isRolling(player)
                || FlightSkillService.isChanneling(player)) return;
        Dynamics dynamics = state(player);
        dynamics.charging = true;
        dynamics.heldTicks = 0;
        dynamics.burstTicks = 0;
        if (!FlightService.hasActiveSession(player)) FlightService.startFlightSession(player, 0);
        PlayerFlightData flight = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        player.setData(ModDataAttachments.PLAYER_FLIGHT.get(), flight
                .withLaunchTicksRemaining(0).withVisualPoseActive(false));
        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    public static void releaseSkyburst(ServerPlayer player) {
        Dynamics dynamics = STATES.get(player.getUUID());
        if (dynamics == null || !dynamics.charging) return;
        dynamics.charging = false;
        ArmPosePayload.clear(player);
        if (!eligible(player) || !FlightService.hasActiveSession(player)
                || !player.getData(ModDataAttachments.PLAYER_FLIGHT.get()).flightEnabled()
                || FlightService.isCycloneActive(player) || DiveBombService.isDiving(player)
                || !dynamics.dimension.equals(player.level().dimension())) {
            forget(player);
            return;
        }
        Vec3 burstStart = player.position();
        double spent = consumeMomentum(player);
        if (dynamics.heldTicks < SKYBURST_TAP_TICKS) {
            FlightService.launch(player);
            // A tap remains an immediate takeoff; banked speed adds punch to an aerial dash.
            if (!player.onGround() && spent > 0)
                push(player, player.getDeltaMovement().scale(1.0D + spent / 250.0D));
            FlightSkillService.onSkyburst(player, burstStart, player.getLookAngle(), spent);
            return;
        }
        double charge = Math.clamp(dynamics.heldTicks / (double) skyburstChargeTicks(player), 0, 1);
        Vec3 look = player.getLookAngle().normalize();
        // Clear the ground when released level or downward; airborne bursts follow the full gaze.
        Vec3 direction = player.onGround() ? new Vec3(look.x, Math.max(0.5D, look.y), look.z).normalize() : look;
        double speed = 1.4D + charge * 1.5D + spent * 0.007D;
        push(player, direction.scale(speed));
        beginBurst(player, 12);
        player.resetFallDistance();
        Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_SKYBURST,
                center, center.add(direction), 1.4D + charge + spent / 100.0D);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, 12);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.FLIGHT_BOOST.get(),
                SoundSource.PLAYERS, 0.9F, (float) (1.35D - charge * 0.35D));
        MasteryService.markPowerUse(player, ModAbilities.FLIGHT_ID);
        FlightSkillService.onSkyburst(player, burstStart, direction, spent);
    }

    /** Cancelling never releases a stored burst or spends Momentum. */
    public static boolean cancelSkyburst(ServerPlayer player) {
        Dynamics dynamics = STATES.get(player.getUUID());
        if (dynamics == null || !dynamics.charging) return false;
        dynamics.charging = false;
        dynamics.heldTicks = 0;
        ArmPosePayload.clear(player);
        player.resetFallDistance();
        FlightVisualSyncService.syncToTrackingPlayers(player);
        return true;
    }

    /** Run after FlightService.tick: charging has final say over this tick's movement. */
    public static void tick(ServerPlayer player) {
        if (!eligible(player) || !FlightService.hasActiveSession(player)) {
            forget(player);
            return;
        }
        Dynamics dynamics = state(player);
        if (!dynamics.dimension.equals(player.level().dimension())) {
            forget(player);
            return;
        }
        PlayerFlightData flight = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        if (DiveBombService.isDiving(player) || FlightService.isCycloneActive(player)
                || FlightSkillService.isRolling(player) || FlightSkillService.isChanneling(player)) {
            if (dynamics.burstTicks > 0) {
                dynamics.burstTicks = 0;
                FlightVisualSyncService.syncToTrackingPlayers(player);
            }
            dynamics.lastPosition = player.position();
            return;
        }
        if (dynamics.burstTicks > 0) {
            if (--dynamics.burstTicks == 0) FlightVisualSyncService.syncToTrackingPlayers(player);
            dynamics.lastPosition = player.position();
            return;
        }
        if (dynamics.charging) {
            dynamics.heldTicks++;
            push(player, Vec3.ZERO);
            player.resetFallDistance();
            if (dynamics.heldTicks >= SKYBURST_TAP_TICKS) {
                double charge = Math.clamp(dynamics.heldTicks / (double) skyburstChargeTicks(player), 0, 1);
                if (dynamics.heldTicks % 3 == 0) {
                    Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
                    AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_CHARGE,
                            center, center.add(player.getLookAngle()), charge);
                }
                if (dynamics.heldTicks == SKYBURST_TAP_TICKS) {
                    ArmPosePayload.show(player, ArmPose.TWO_HANDS, SKYBURST_AUTO_RELEASE_TICKS);
                    player.serverLevel().playSound(null, player.blockPosition(),
                            SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 0.7F, 1.5F);
                }
                if (dynamics.heldTicks == skyburstChargeTicks(player))
                    player.serverLevel().playSound(null, player.blockPosition(),
                            SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.7F, 1.8F);
            }
            if (dynamics.heldTicks >= SKYBURST_AUTO_RELEASE_TICKS) releaseSkyburst(player);
        } else {
            // Read real displacement as well as velocity: pressing Forward into a wall earns nothing.
            double distance = player.position().distanceTo(dynamics.lastPosition);
            boolean gaining = flight.flightEnabled() && flight.movingForward() && flight.speedStep() > 0
                    && !player.onGround() && flight.launchTicksRemaining() == 0
                    && player.getDeltaMovement().length() > 0.35D && distance > 0.12D && distance < 5;
            double gain = (0.40D + flight.speedStep() * 0.18D)
                    * (com.jayptucker.heroesevolved.skills.SkillTreeService.has(player, "flight_1_0") ? 1.3D : 1.0D);
            dynamics.momentum = Math.clamp(dynamics.momentum + (gaining ? gain : -0.20D), 0, MAX_MOMENTUM);
            announce(player, dynamics);
            if (dynamics.momentum >= 50 && player.tickCount % 10 == 0) {
                Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
                AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_MOMENTUM,
                        center, center.add(player.getLookAngle()), 1);
            }
        }
        dynamics.lastPosition = player.position();
    }

    public static void forget(ServerPlayer player) {
        Dynamics dynamics = STATES.remove(player.getUUID());
        if (dynamics != null && dynamics.charging) ArmPosePayload.clear(player);
    }

    public static void clear() { STATES.clear(); }

    private static boolean eligible(ServerPlayer player) {
        return PowerRules.canAct(player) && !player.isPassenger() && !player.isFallFlying()
                && !player.isInWaterOrBubble()
                && FlightService.isFlightAuthorized(player);
    }

    private static Dynamics state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), ignored -> new Dynamics(
                player.level().dimension(), player.position()));
    }

    private static void announce(ServerPlayer player, Dynamics dynamics) {
        int tier = dynamics.momentum >= MAX_MOMENTUM ? 2 : dynamics.momentum >= 50 ? 1 : 0;
        if (tier > dynamics.announcedTier) {
            player.displayClientMessage(Component.literal(tier == 2
                    ? "FULL MOMENTUM: your next burst, dive or Tempest is empowered."
                    : "Momentum building: hold Skyburst to turn speed into a slingshot.")
                    .withStyle(tier == 2 ? ChatFormatting.WHITE : ChatFormatting.AQUA), true);
            player.serverLevel().playSound(null, player.blockPosition(),
                    SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.35F, tier == 2 ? 1.8F : 1.3F);
        }
        dynamics.announcedTier = tier;
    }

    private static void push(ServerPlayer player, Vec3 velocity) {
        if (velocity.lengthSqr() > 3.6D * 3.6D) velocity = velocity.normalize().scale(3.6D);
        boolean changed = player.getDeltaMovement().distanceToSqr(velocity) > 0.0001D;
        player.setDeltaMovement(velocity);
        // Reassure the client periodically while hovering; avoid sending identical zero packets every tick.
        if (changed || velocity.lengthSqr() > 0.0001D || player.tickCount % 4 == 0)
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static final class Dynamics {
        final ResourceKey<Level> dimension;
        Vec3 lastPosition;
        double momentum;
        int announcedTier;
        boolean charging;
        int heldTicks;
        int burstTicks;
        Dynamics(ResourceKey<Level> dimension, Vec3 position) {
            this.dimension = dimension;
            this.lastPosition = position;
        }
    }
}
