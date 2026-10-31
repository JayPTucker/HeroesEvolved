package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A phaser's own Density dial (-100 Full Ghost to 100 Full Dense) and Breath underground (-1 above ground), for the HUD. */
public record DensityPayload(int density, int breath) implements CustomPacketPayload {
    public static final Type<DensityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "density"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DensityPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.density + 100); b.writeVarInt(v.breath + 1); },
            b -> new DensityPayload(b.readVarInt() - 100, b.readVarInt() - 1));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(net.minecraft.server.level.ServerPlayer player, int density, int breath) {
        if (player.connection == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new DensityPayload(density, breath));
    }

    public static void handle(DensityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDensity.set(payload.density(), payload.breath()));
    }
}
