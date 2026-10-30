package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A speedster's own Momentum, for the HUD. */
public record MomentumPayload(int momentum) implements CustomPacketPayload {
    public static final Type<MomentumPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "momentum"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MomentumPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> b.writeVarInt(v.momentum), b -> new MomentumPayload(b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(net.minecraft.server.level.ServerPlayer player, int momentum) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new MomentumPayload(momentum));
    }

    public static void handle(MomentumPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMomentum.set(payload.momentum()));
    }
}
