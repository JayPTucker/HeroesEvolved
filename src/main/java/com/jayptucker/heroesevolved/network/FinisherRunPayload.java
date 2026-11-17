package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Your finisher began ({@code ticks} long, on {@code target}), or ended ({@code ticks} 0): the client frames it with
 * the finisher camera. Sent to the finishing player only.
 */
public record FinisherRunPayload(int player, int target, int ticks) implements CustomPacketPayload {
    public static final Type<FinisherRunPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "finisher_run"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinisherRunPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.player); b.writeVarInt(p.target); b.writeVarInt(p.ticks); },
            b -> new FinisherRunPayload(b.readVarInt(), b.readVarInt(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FinisherRunPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientFinisher.run(payload));
    }
}
