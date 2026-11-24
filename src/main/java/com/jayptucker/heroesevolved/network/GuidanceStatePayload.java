package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.PlayerGuidance;
import com.jayptucker.heroesevolved.guidance.GuidanceData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GuidanceStatePayload(boolean enabled, long seen) implements CustomPacketPayload {
    public static final Type<GuidanceStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "guidance_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuidanceStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GuidanceStatePayload::enabled, ByteBufCodecs.VAR_LONG, GuidanceStatePayload::seen, GuidanceStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(GuidanceStatePayload p, IPayloadContext context) { PlayerGuidance.accept(new GuidanceData(p.enabled, p.seen)); }
}
