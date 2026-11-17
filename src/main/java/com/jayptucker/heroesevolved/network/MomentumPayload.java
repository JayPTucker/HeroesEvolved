package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A speedster's Momentum and selected running speed, for the HUD. */
public record MomentumPayload(int momentum, int speedStep, int maximumSpeedStep) implements CustomPacketPayload {
    static final MomentumPayload INACTIVE = new MomentumPayload(0, 0, 0);
    public static final Type<MomentumPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "momentum"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MomentumPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MomentumPayload::momentum,
            ByteBufCodecs.VAR_INT, MomentumPayload::speedStep,
            ByteBufCodecs.VAR_INT, MomentumPayload::maximumSpeedStep,
            MomentumPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(net.minecraft.server.level.ServerPlayer player, int momentum, int speedStep, int maximumSpeedStep) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new MomentumPayload(momentum, speedStep, maximumSpeedStep));
    }

    public static void clear(net.minecraft.server.level.ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, INACTIVE);
    }

    public static void handle(MomentumPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMomentum.set(payload));
    }
}
