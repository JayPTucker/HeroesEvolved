package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.flight.FlightService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FlightForwardInputPayload(
        boolean movingForward
) implements CustomPacketPayload {
    public static final Type<FlightForwardInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "flight_forward_input"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            FlightForwardInputPayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            FlightForwardInputPayload::movingForward,
            FlightForwardInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            FlightForwardInputPayload payload,
            IPayloadContext context
    ) {
        if (context.player() instanceof ServerPlayer player) {
            FlightService.setForwardInput(player, payload.movingForward());
        }
    }
}
