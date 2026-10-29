package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.TimeRewindService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Both directions of rewinding a death. To the client: "your death can be rewound for this many ticks"
 * (the death screen shows a Rewind button). To the server, with zero ticks: "rewind it once I respawn".
 */
public record DeathRewindPayload(int ticks) implements CustomPacketPayload {
    public static final Type<DeathRewindPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "death_rewind"));
    public static final StreamCodec<ByteBuf, DeathRewindPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(DeathRewindPayload::new, DeathRewindPayload::ticks);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(DeathRewindPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.DeathRewindButton.offer(payload.ticks()));
    }

    public static void handleServer(DeathRewindPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) TimeRewindService.requestDeathRewind(player); });
    }
}
