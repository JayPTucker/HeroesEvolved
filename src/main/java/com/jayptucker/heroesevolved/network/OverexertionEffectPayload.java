package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OverexertionEffectPayload(
    int durationTicks
) implements CustomPacketPayload {
    public static final Type<OverexertionEffectPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID,
            "overexertion_effect"
        ));

    public static final StreamCodec<
        RegistryFriendlyByteBuf,
        OverexertionEffectPayload
    > STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        OverexertionEffectPayload::durationTicks,
        OverexertionEffectPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

        public static void handle(
            OverexertionEffectPayload payload,
            IPayloadContext context
    ) {
        // The client stores only the visual timer.
        // The server remains responsible for damage and potion effects.
        ClientOverexertionState.start(payload.durationTicks());
    }
}
