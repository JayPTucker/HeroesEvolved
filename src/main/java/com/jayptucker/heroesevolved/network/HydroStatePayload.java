package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A hydrokinetic, for themselves and everyone watching: how much water they carry (and can), and what they are doing
 * with it ({@code HydroService} flags: Octopus Form, Wave Rider, a leap off the crest, a Water Spout, a Maelstrom).
 * Sent when the water moves a few points or a flag changes; the client draws the rest every frame.
 */
public record HydroStatePayload(int entity, int water, int capacity, int flags) implements CustomPacketPayload {
    public static final Type<HydroStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "hydro_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HydroStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.entity); b.writeVarInt(p.water); b.writeVarInt(p.capacity); b.writeVarInt(p.flags); },
            b -> new HydroStatePayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HydroStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientHydroState.update(payload));
    }
}
