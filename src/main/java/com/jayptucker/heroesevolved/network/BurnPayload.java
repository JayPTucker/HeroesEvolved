package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * How far a player's skin has burnt away (0 whole, 1 gone), sent to them and everyone tracking them when it moves. The
 * client peels their skin away pixel by pixel to show the charred body underneath (BurnClient).
 */
public record BurnPayload(int entity, float level) implements CustomPacketPayload {
    public static final Type<BurnPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "burn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BurnPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BurnPayload::entity, ByteBufCodecs.FLOAT, BurnPayload::level, BurnPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BurnPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.BurnClient.set(payload.entity, payload.level));
    }
}
