package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.flight.FlightService;
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
                        visualPoseActive,
                        FlightService.isCycloneActive(player)
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
                        visualPoseActive,
                        FlightService.isCycloneActive(flyingPlayer)
                )
        );
    }
}
