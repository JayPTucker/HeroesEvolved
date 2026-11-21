package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Whether it is a starfall night where this player is ({@code meteor/Starfall}): the sky's streaks are drawn while it is. */
public record StarfallPayload(boolean on) implements CustomPacketPayload {
    public static final Type<StarfallPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "starfall"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StarfallPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast().map(StarfallPayload::new, StarfallPayload::on);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(StarfallPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientMeteors.starfall(payload.on()));
    }
}
