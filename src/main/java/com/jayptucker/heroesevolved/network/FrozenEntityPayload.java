package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.FrozenVisuals;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** Tracking-scoped ice-shell state, separate from vanilla powder-snow chill. Zero duration thaws. */
public record FrozenEntityPayload(int entityId, UUID entityUuid, int remainingTicks) implements CustomPacketPayload {
    public static final Type<FrozenEntityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "frozen_entity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FrozenEntityPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.entityId); buf.writeUUID(value.entityUuid); buf.writeVarInt(value.remainingTicks); },
            buf -> new FrozenEntityPayload(buf.readVarInt(), buf.readUUID(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(FrozenEntityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FrozenVisuals.update(payload));
    }
}
