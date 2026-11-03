package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Heat and flame-flight telemetry for the owner; nearby flame trails use normal effects. */
public record PyroStatePayload(boolean pyro, boolean flying, boolean diving, int heat, boolean blue,
                               int speedStep, int momentum, boolean boosting)
        implements CustomPacketPayload {
    public PyroStatePayload(boolean pyro, boolean flying, boolean diving, int heat, boolean blue) {
        this(pyro, flying, diving, heat, blue, 0, 0, false);
    }
    public static final Type<PyroStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "pyro_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PyroStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeBoolean(p.pyro); b.writeBoolean(p.flying); b.writeBoolean(p.diving); b.writeVarInt(p.heat); b.writeBoolean(p.blue); b.writeVarInt(p.speedStep); b.writeVarInt(p.momentum); b.writeBoolean(p.boosting); },
            b -> new PyroStatePayload(b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void send(ServerPlayer player, boolean pyro, boolean flying, boolean diving, int heat, boolean blue) {
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new PyroStatePayload(pyro, flying, diving, heat, blue,
                com.jayptucker.heroesevolved.pyro.PyroFlightService.speedStep(player),
                (int) Math.round(com.jayptucker.heroesevolved.pyro.PyroFlightService.momentum(player)),
                com.jayptucker.heroesevolved.pyro.PyroFlightService.isBoosting(player)));
    }
    public static void handle(PyroStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.PyroFlightClient.update(payload));
    }
}
