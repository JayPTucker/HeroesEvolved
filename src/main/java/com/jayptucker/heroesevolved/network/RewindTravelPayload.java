package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.RewindTravel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * One stretch of a player's trip back through their past, in the world they are in: whose trip, which tick of
 * it the stretch starts at, and where they are at each tick of it (x, y, z, yaw, pitch). Sent when a trip
 * starts and each time it crosses into another world.
 */
public record RewindTravelPayload(int entity, int from, double[] stops) implements CustomPacketPayload {
    public static final Type<RewindTravelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "rewind_travel"));
    private static final int MAX_STOPS = 256;
    public static final StreamCodec<RegistryFriendlyByteBuf, RewindTravelPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entity);
        buf.writeVarInt(p.from);
        buf.writeVarInt(p.stops.length / 5);
        for (double d : p.stops) buf.writeDouble(d);
    }, buf -> {
        int entity = buf.readVarInt(), from = buf.readVarInt(), count = Math.min(buf.readVarInt(), MAX_STOPS);
        double[] stops = new double[count * 5];
        for (int i = 0; i < stops.length; i++) stops[i] = buf.readDouble();
        return new RewindTravelPayload(entity, from, stops);
    });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RewindTravelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.RewindTravelClient.receive(payload));
    }

    /** To the traveller and everyone who can see them. */
    public static void send(ServerPlayer player, int from, List<RewindTravel.Stop> run) {
        int count = Math.min(run.size(), MAX_STOPS);
        double[] stops = new double[count * 5];
        for (int i = 0; i < count; i++) {
            RewindTravel.Stop stop = run.get(i);
            stops[i * 5] = stop.at().x; stops[i * 5 + 1] = stop.at().y; stops[i * 5 + 2] = stop.at().z;
            stops[i * 5 + 3] = stop.yaw(); stops[i * 5 + 4] = stop.pitch();
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new RewindTravelPayload(player.getId(), from, stops));
    }
}
