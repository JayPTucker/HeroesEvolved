package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightDynamicsService;
import com.jayptucker.heroesevolved.flight.DiveBombService;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FlightVisualSyncService {
    private FlightVisualSyncService() {
    }

    public static void syncToTrackingPlayers(ServerPlayer player) {
        boolean visualPoseActive = player.getData(
                ModDataAttachments.PLAYER_FLIGHT.get()
        ).visualPoseActive();

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new FlightVisualStatePayload(
                        player.getId(),
                        visualPoseActive || DiveBombService.isDiving(player) || FlightDynamicsService.isBursting(player) || FlightSkillService.isRolling(player),
                        FlightService.isCycloneActive(player),
                        player.getData(ModDataAttachments.PLAYER_FLIGHT.get()).flightEnabled(),
                        player.getData(ModDataAttachments.PLAYER_FLIGHT.get()).speedStep(),
                        telemetry(player)
                )
        );
    }

    public static void syncToPlayer(
            ServerPlayer flyingPlayer,
            ServerPlayer receivingPlayer
    ) {
        boolean visualPoseActive = flyingPlayer.getData(
                ModDataAttachments.PLAYER_FLIGHT.get()
        ).visualPoseActive();

        PacketDistributor.sendToPlayer(
                receivingPlayer,
                new FlightVisualStatePayload(
                        flyingPlayer.getId(),
                        visualPoseActive || DiveBombService.isDiving(flyingPlayer) || FlightDynamicsService.isBursting(flyingPlayer) || FlightSkillService.isRolling(flyingPlayer),
                        FlightService.isCycloneActive(flyingPlayer),
                        flyingPlayer.getData(ModDataAttachments.PLAYER_FLIGHT.get()).flightEnabled(),
                        flyingPlayer.getData(ModDataAttachments.PLAYER_FLIGHT.get()).speedStep(),
                        telemetry(flyingPlayer)
                )
        );
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
