package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Synchronizes global Eclipse visuals from the server to each client. */
public record EclipseStatePayload(boolean active) implements CustomPacketPayload {
    public static final Type<EclipseStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "eclipse_state"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            EclipseStatePayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            EclipseStatePayload::active,
            EclipseStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            EclipseStatePayload payload,
            IPayloadContext context
    ) {
        ClientEclipseState.setEclipseActive(payload.active());
    }
}
