package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.regeneration.BodyService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent each tick a downed regenerator holds the give-up key; three seconds of it wakes them at home. */
public record GiveUpPayload() implements CustomPacketPayload {
    public static final GiveUpPayload INSTANCE = new GiveUpPayload();
    public static final Type<GiveUpPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "give_up"));
    public static final StreamCodec<ByteBuf, GiveUpPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GiveUpPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) BodyService.giveUpHeld(player); });
    }
}
