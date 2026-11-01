package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Quake's Strain (0 to 100) and whether her arms are fractured, for the HUD. */
public record StrainPayload(int strain, boolean fractured) implements CustomPacketPayload {
    public static final Type<StrainPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "quake_strain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StrainPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.strain); b.writeBoolean(v.fractured); },
            b -> new StrainPayload(b.readVarInt(), b.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(net.minecraft.server.level.ServerPlayer player, int strain, boolean fractured) {
        if (player.connection == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new StrainPayload(strain, fractured));
    }

    public static void handle(StrainPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientStrain.set(payload.strain(), payload.fractured()));
    }
}
