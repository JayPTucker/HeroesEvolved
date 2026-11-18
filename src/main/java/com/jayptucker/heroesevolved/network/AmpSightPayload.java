package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * An overcharged telepath's thousand voices: every mind they can hear, outlined through walls for them alone, for
 * {@code ticks}. Re-sent while the Overcharge lasts.
 */
public record AmpSightPayload(List<Integer> entities, int ticks) implements CustomPacketPayload {
    public static final Type<AmpSightPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "amp_sight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AmpSightPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(128)), AmpSightPayload::entities,
            ByteBufCodecs.VAR_INT, AmpSightPayload::ticks, AmpSightPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AmpSightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientAmp.sight(payload.entities, payload.ticks));
    }
}
