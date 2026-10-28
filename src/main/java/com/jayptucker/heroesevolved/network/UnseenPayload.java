package com.jayptucker.heroesevolved.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells clients that a player is completely unseen: body, armor, held items and name tag. */
public record UnseenPayload(int entity, boolean hidden) implements CustomPacketPayload {
    public static final Type<UnseenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("heroesevolved", "unseen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnseenPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.entity); b.writeBoolean(v.hidden); },
            b -> new UnseenPayload(b.readVarInt(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(UnseenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.UnseenClient.update(payload.entity, payload.hidden));
    }
}
