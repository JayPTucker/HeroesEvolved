package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FlightVisualStatePayload(
        int entityId,
        boolean flightActive,
        boolean cycloneActive
) implements CustomPacketPayload {
    public static final Type<FlightVisualStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "flight_visual_state"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            FlightVisualStatePayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FlightVisualStatePayload::entityId,
            ByteBufCodecs.BOOL,
            FlightVisualStatePayload::flightActive,
            ByteBufCodecs.BOOL,
            FlightVisualStatePayload::cycloneActive,
            FlightVisualStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            FlightVisualStatePayload payload,
            IPayloadContext context
    ) {
        // This data only controls client rendering. Flight mechanics remain
        // fully server-authoritative in FlightService.
        ClientFlightVisualState.update(
                payload.entityId(),
                payload.flightActive(),
                payload.cycloneActive()
        );
    }
}
