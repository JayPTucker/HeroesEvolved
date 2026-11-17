package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The runner's own Cyclone, for their HUD ring and their scroll wheel: its speed setting (0 when no Cyclone is running),
 * the fastest they can run it, and what it is doing ({@link #VACUUM}, {@link #RING}, {@link #TORNADO} or {@link #REACTOR}).
 */
public record CycloneStatusPayload(int speed, int maximum, int mode) implements CustomPacketPayload {
    public static final int VACUUM = 0, RING = 1, TORNADO = 2, REACTOR = 3;
    public static final Type<CycloneStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "cyclone_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CycloneStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CycloneStatusPayload::speed, ByteBufCodecs.VAR_INT, CycloneStatusPayload::maximum,
            ByteBufCodecs.VAR_INT, CycloneStatusPayload::mode, CycloneStatusPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CycloneStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientCyclone.set(payload));
    }
}
