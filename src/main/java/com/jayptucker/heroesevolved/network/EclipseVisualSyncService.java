package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.data.EclipseCalendar;
import com.jayptucker.heroesevolved.events.EclipseService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends the current global Eclipse timeline to connected players. */
public final class EclipseVisualSyncService {
    private EclipseVisualSyncService() {
    }

    public static void syncToAll(MinecraftServer server) {
        EclipseStatePayload payload = payload(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();

        if (server != null) {
            PacketDistributor.sendToPlayer(player, payload(server));
        }
    }

    private static EclipseStatePayload payload(MinecraftServer server) {
        return EclipseStatePayload.of(EclipseService.timeline(server), EclipseCalendar.intervalDays());
    }
}
