package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A precognitive's own Clarity, for the HUD. */
public record ClarityPayload(int clarity) implements CustomPacketPayload {
    public static final Type<ClarityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "clarity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClarityPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> b.writeVarInt(v.clarity), b -> new ClarityPayload(b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ClarityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientClarity.set(payload.clarity()));
    }
}
