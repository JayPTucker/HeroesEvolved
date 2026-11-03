package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tracking-scoped skating state drives observer animation as well as the rider's controls. */
public record CryoRideStatePayload(int entityId, boolean active, int speedStep, float speed) implements CustomPacketPayload {
    public static final Type<CryoRideStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "cryo_ride_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CryoRideStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.entityId); b.writeBoolean(p.active); b.writeVarInt(p.speedStep); b.writeFloat(p.speed); },
            b -> new CryoRideStatePayload(b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void send(ServerPlayer player, boolean active, int step, float speed) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new CryoRideStatePayload(player.getId(), active, step, speed));
    }
    public static void handle(CryoRideStatePayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.CryoRideClient.update(p));
    }
}
