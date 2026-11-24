package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.guidance.GuidanceService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 0: displayed hint, 1: disable tips, 2: enable tips. No gameplay state is changed. */
public record GuidanceActionPayload(int operation, int hint) implements CustomPacketPayload {
    public static final Type<GuidanceActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "guidance_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuidanceActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GuidanceActionPayload::operation, ByteBufCodecs.VAR_INT, GuidanceActionPayload::hint, GuidanceActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(GuidanceActionPayload p, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) GuidanceService.apply(player, p.operation, p.hint);
    }
}
