package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Owner-only exposure telemetry. Concealment of other players still uses UnseenPayload. */
public record OpticalStatePayload(boolean faded, boolean unseen, int exposure, boolean veil, boolean ghost)
        implements CustomPacketPayload {
    public static final Type<OpticalStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "optical_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpticalStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeBoolean(p.faded); b.writeBoolean(p.unseen); b.writeVarInt(p.exposure); b.writeBoolean(p.veil); b.writeBoolean(p.ghost); },
            b -> new OpticalStatePayload(b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void send(ServerPlayer player, boolean faded, boolean unseen, int exposure, boolean veil, boolean ghost) {
        if (player.connection != null) PacketDistributor.sendToPlayer(player,
                new OpticalStatePayload(faded, unseen, Math.clamp(exposure, 0, 100), veil, ghost));
    }
    public static void handle(OpticalStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.OpticalClient.update(payload));
    }
}
