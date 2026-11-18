package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A body supercharged by red lightning ({@code amplify/Amplification}), for everyone watching it: level 1 a Surge, 2 an
 * Overcharge, 0 over; and how many ticks are left. The client draws the crackle on it for as long as it lasts.
 */
public record SuperchargedPayload(int entity, int level, int ticks) implements CustomPacketPayload {
    public static final Type<SuperchargedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "supercharged"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuperchargedPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.entity); b.writeVarInt(p.level); b.writeVarInt(p.ticks); },
            b -> new SuperchargedPayload(b.readVarInt(), b.readVarInt(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SuperchargedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientAmp.supercharged(payload.entity, payload.level, payload.ticks));
    }
}
