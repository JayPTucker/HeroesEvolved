package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * An amplifier ({@code amplify/AmpService}), for themselves and everyone watching: how much Charge they hold, and what
 * their hands are doing ({@code AmpService} flags: a ball growing between them, sparking on their own, showing off, a
 * Thousand Times gathering). Sent when the Charge moves a few points or a flag changes.
 */
public record AmpStatePayload(int entity, int charge, int flags) implements CustomPacketPayload {
    public static final Type<AmpStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "amp_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AmpStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.entity); b.writeVarInt(p.charge); b.writeVarInt(p.flags); },
            b -> new AmpStatePayload(b.readVarInt(), b.readVarInt(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AmpStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientAmp.state(payload.entity, payload.charge, payload.flags));
    }
}
