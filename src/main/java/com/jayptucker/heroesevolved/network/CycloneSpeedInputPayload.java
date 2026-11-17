package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedKit;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One scroll while running a Cyclone: faster or slower. The server checks the runner and caps the speed. */
public record CycloneSpeedInputPayload(int direction) implements CustomPacketPayload {
    public static final Type<CycloneSpeedInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "cyclone_speed_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CycloneSpeedInputPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CycloneSpeedInputPayload::direction, CycloneSpeedInputPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CycloneSpeedInputPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sender) {
            ServerPlayer runner = PossessionService.actionActor(sender);
            if (runner != null) SpeedKit.cycloneSpeed(runner, payload.direction());
        }
    }
}
