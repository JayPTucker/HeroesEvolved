package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** An earthbender's own Root (0 to 100), whether they hold Horse Stance, and Breath underground (-1 above ground), for the HUD. */
public record DensityPayload(int root, boolean stance, int breath) implements CustomPacketPayload {
    public static final Type<DensityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "density"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DensityPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.root); b.writeBoolean(v.stance); b.writeVarInt(v.breath + 1); },
            b -> new DensityPayload(b.readVarInt(), b.readBoolean(), b.readVarInt() - 1));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(net.minecraft.server.level.ServerPlayer player, int root, boolean stance, int breath) {
        if (player.connection == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new DensityPayload(root, stance, breath));
    }

    public static void handle(DensityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDensity.set(payload.root(), payload.stance(), payload.breath()));
    }
}
