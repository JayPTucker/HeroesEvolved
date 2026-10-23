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

/** One wheel direction per notch; the server chooses the permitted speed. */
public record FlightSpeedInputPayload(int direction) implements CustomPacketPayload {
    public static final Type<FlightSpeedInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "flight_speed_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightSpeedInputPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, FlightSpeedInputPayload::direction, FlightSpeedInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(FlightSpeedInputPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) FlightService.changeSpeed(player, payload.direction());
    }
}
