package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * How much Regrowth a regenerator carries, for everyone who can see them: it glows in their veins
 * ({@code client/RegrowthLayer}). Sent to the player and their trackers when it changes by a step, and when someone
 * starts tracking them.
 */
public record RegrowthPayload(int entity, int regrowth) implements CustomPacketPayload {
    public static final Type<RegrowthPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "regrowth"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RegrowthPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.entity); b.writeVarInt(v.regrowth); },
            b -> new RegrowthPayload(b.readVarInt(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RegrowthPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientRegrowth.set(payload.entity(), payload.regrowth()));
    }
}
