package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A singer's own Resonance (Sonic Resonance), for the HUD. */
public record ResonancePayload(int resonance) implements CustomPacketPayload {
    public static final Type<ResonancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "resonance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResonancePayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> b.writeVarInt(v.resonance), b -> new ResonancePayload(b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ResonancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientResonance.set(payload.resonance()));
    }
}
