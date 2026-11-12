package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells the local player when a time power enables the use-key catch. */
public record TemporalCatchStatePayload(boolean active) implements CustomPacketPayload {
    public static final Type<TemporalCatchStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "temporal_catch_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TemporalCatchStatePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, TemporalCatchStatePayload::active, TemporalCatchStatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TemporalCatchStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientProjectileTime.setCatchEnabled(payload.active));
    }
}
