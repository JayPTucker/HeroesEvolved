package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A speedster's friction heat (0 to 100), sent to them alone when it moves, for the heat gauge (gui/HudGauges). */
public record FrictionPayload(float heat) implements CustomPacketPayload {
    public static final Type<FrictionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "friction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FrictionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, FrictionPayload::heat, FrictionPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FrictionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.gui.HudGauges.friction(payload.heat));
    }
}
