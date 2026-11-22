package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A runner's client let go at speed and is skidding to a stop (client/SpeedSkid). The server checks they are really
 * running under Superspeed and shows everyone the skid.
 */
public record SpeedSkidPayload() implements CustomPacketPayload {
    public static final SpeedSkidPayload INSTANCE = new SpeedSkidPayload();
    public static final Type<SpeedSkidPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "speed_skid"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeedSkidPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpeedSkidPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sender) {
            // The body's controller skids it, as with every other speedster input; a controlled body can't.
            ServerPlayer runner = com.jayptucker.heroesevolved.telepathy.PossessionService.actionActor(sender);
            if (runner != null) SpeedService.skidStop(runner);
        }
    }
}
