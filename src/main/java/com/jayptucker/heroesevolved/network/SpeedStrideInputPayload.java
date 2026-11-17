package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One scroll direction. The server validates the runner and caps their speed. */
public record SpeedStrideInputPayload(int direction) implements CustomPacketPayload {
    public static final Type<SpeedStrideInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "speed_stride_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeedStrideInputPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SpeedStrideInputPayload::direction, SpeedStrideInputPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpeedStrideInputPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sender) {
            ServerPlayer runner = PossessionService.actionActor(sender);
            if (runner != null) SpeedService.changeSpeed(runner, payload.direction());
        }
    }
}
