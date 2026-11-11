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
 * Where a speedster's Overrun stands, for their HUD: {@link #HIDDEN}; {@link #LOCKED} (below Power Level 15);
 * {@link #GATHERING} its minute of history (value: seconds left); {@link #COOLDOWN} (value: seconds left);
 * {@link #READY} (value: the charge, in thousandths); {@link #BREACH} open (value: thousandths of the way through).
 */
public record OverrunStatusPayload(int state, int value) implements CustomPacketPayload {
    public static final int HIDDEN = 0, LOCKED = 1, GATHERING = 2, COOLDOWN = 3, READY = 4, BREACH = 5;
    public static final Type<OverrunStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "overrun_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OverrunStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.state); buf.writeVarInt(p.value); },
            buf -> new OverrunStatusPayload(buf.readVarInt(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OverrunStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientOverrun.set(payload.state, payload.value));
    }

    public static void send(ServerPlayer player, int state, int value) {
        PacketDistributor.sendToPlayer(player, new OverrunStatusPayload(state, value));
    }
}
