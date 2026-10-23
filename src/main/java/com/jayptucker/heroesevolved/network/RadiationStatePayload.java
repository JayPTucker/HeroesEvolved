package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.RadiationVisuals;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** Tracking-scoped body heat and positional charging sound. Zero values clear both. */
public record RadiationStatePayload(int entityId, UUID uuid, int stress, int remaining, int duration)
        implements CustomPacketPayload {
    public static final Type<RadiationStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "radiation_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.entityId); b.writeUUID(v.uuid); b.writeVarInt(v.stress);
                b.writeVarInt(v.remaining); b.writeVarInt(v.duration); },
            b -> new RadiationStatePayload(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(RadiationStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RadiationVisuals.update(payload));
    }
}
