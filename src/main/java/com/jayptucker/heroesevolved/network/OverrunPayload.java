package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * How close a speedster is to outrunning the last minute, for everyone who can see them: stage 0 nothing,
 * 1 the wake (value: charge in thousandths), 2 the breach open ahead of them (value: ticks it has been open).
 */
public record OverrunPayload(int entity, int stage, int value) implements CustomPacketPayload {
    public static final int NONE = 0, WAKE = 1, BREACH = 2;
    public static final Type<OverrunPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "speed_overrun"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OverrunPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entity); buf.writeVarInt(p.stage); buf.writeVarInt(p.value);
    }, buf -> new OverrunPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OverrunPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.SpeedOverrunClient.update(payload));
    }

    /** To the runner and everyone within 160 blocks of them. */
    public static void send(ServerPlayer runner, int stage, int value) {
        OverrunPayload payload = new OverrunPayload(runner.getId(), stage, value);
        for (ServerPlayer viewer : runner.serverLevel().players())
            if (viewer == runner || viewer.distanceToSqr(runner) <= 160 * 160) PacketDistributor.sendToPlayer(viewer, payload);
    }
}
