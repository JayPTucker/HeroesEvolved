package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * How closely the Company watches this player ({@code company/CompanyAttention}, 0 to 100) and what it is doing about it
 * ({@code CompanyOps.Kind} by ordinal), for the HUD's ring. Sent when either changes.
 */
public record CompanyAttentionPayload(float attention, int op) implements CustomPacketPayload {
    public static final Type<CompanyAttentionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "company_attention"));
    public static final StreamCodec<ByteBuf, CompanyAttentionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CompanyAttentionPayload::attention,
            ByteBufCodecs.VAR_INT, CompanyAttentionPayload::op,
            CompanyAttentionPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CompanyAttentionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientCompany.accept(payload));
    }
}
