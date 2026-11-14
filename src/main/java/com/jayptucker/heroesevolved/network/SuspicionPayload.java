package com.jayptucker.heroesevolved.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells a shapeshifter how close their disguise is to being seen through (0 to 100), for the eye on their HUD. */
public record SuspicionPayload(float value) implements CustomPacketPayload {
    public static final Type<SuspicionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("heroesevolved", "suspicion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuspicionPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, SuspicionPayload::value, SuspicionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SuspicionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.gui.SuspicionHudOverlay.set(payload.value()));
    }
}
