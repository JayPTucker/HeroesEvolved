package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightDynamicsService;
import com.jayptucker.heroesevolved.flight.DiveBombService;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.pyro.PyroFlightService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FlightVisualSyncService {
    private FlightVisualSyncService() {
    }

    public static void syncToTrackingPlayers(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, state(player));
    }

    public static void syncToPlayer(ServerPlayer flyingPlayer, ServerPlayer receivingPlayer) {
        PacketDistributor.sendToPlayer(receivingPlayer, state(flyingPlayer));
    }

    private static FlightVisualStatePayload state(ServerPlayer player) {
        if (PyroFlightService.isFlying(player)) {
            int fireTelemetry = Math.clamp((int) Math.round(PyroFlightService.momentum(player)), 0, 100)
                    | (PyroFlightService.isChargingBoost(player) ? 128 : 0)
                    | (PyroFlightService.isDiving(player) ? 256 : 0)
                    | 32768
                    | (PyroService.isBlue(player) ? 65536 : 0)
                    | (PyroFlightService.isBoosting(player) ? 131072 : 0);
            return new FlightVisualStatePayload(player.getId(), PyroFlightService.visualPoseActive(player),
                    false, true, PyroFlightService.speedStep(player), fireTelemetry);
        }
        var flight = player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
        return new FlightVisualStatePayload(player.getId(),
                flight.visualPoseActive() || DiveBombService.isDiving(player)
                        || FlightDynamicsService.isBursting(player) || FlightSkillService.isRolling(player),
                FlightService.isCycloneActive(player), flight.flightEnabled(), flight.speedStep(), telemetry(player));
    }

    // Seven bits of Momentum, charge/dive/roll flags, then four bits of roll age (0..12).
    private static int telemetry(ServerPlayer player) {
        return Math.clamp((int) Math.round(FlightDynamicsService.momentum(player)), 0, 100)
                | (FlightDynamicsService.isCharging(player) ? 128 : 0)
                | (DiveBombService.isDiving(player) ? 256 : 0)
                | (FlightSkillService.isRolling(player) ? 512 : 0)
                | (Math.clamp((int) Math.round(FlightSkillService.rollProgress(player) * 12), 0, 12) << 10)
                | (FlightSkillService.isRolling(player) && FlightSkillService.rollDirection(player) < 0 ? 16384 : 0);
    }
}
