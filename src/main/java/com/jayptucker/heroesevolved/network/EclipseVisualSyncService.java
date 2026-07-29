package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.events.EclipseService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends the current global Eclipse state to connected players. */
public final class EclipseVisualSyncService {
    private EclipseVisualSyncService() {
    }

    public static void syncToAll(MinecraftServer server) {
        boolean active = EclipseService.isActive(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(
                    player,
                    new EclipseStatePayload(active)
            );
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();

        if (server != null) {
            PacketDistributor.sendToPlayer(
                    player,
                    new EclipseStatePayload(EclipseService.isActive(server))
            );
        }
    }
}
