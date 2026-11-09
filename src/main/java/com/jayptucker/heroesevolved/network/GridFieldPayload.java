package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells a client an electromagnetic field opened nearby, so Create's shafts stop turning on its side too. */
public record GridFieldPayload(BlockPos center, int radius, int ticks) implements CustomPacketPayload {
    public static final Type<GridFieldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "grid_field"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GridFieldPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, GridFieldPayload::center,
            ByteBufCodecs.VAR_INT, GridFieldPayload::radius,
            ByteBufCodecs.VAR_INT, GridFieldPayload::ticks,
            GridFieldPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GridFieldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null)
                GridDisruption.acceptClient(context.player().level(), payload.center, Math.clamp(payload.radius, 0, 64),
                        Math.clamp(payload.ticks, 0, 20 * 60));
        });
    }
}
