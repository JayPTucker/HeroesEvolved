package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Claire's own HUD: stored Regrowth, ticks left on the ground (0 if standing), how long she would stay
 * down if killed now, and how far along holding the give-up key is (-1 when it is not available yet).
 */
public record RegenerationStatePayload(int regrowth, int downedTicks, int recoveryTicks, int giveUp) implements CustomPacketPayload {
    public static final Type<RegenerationStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "regeneration_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RegenerationStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.regrowth); b.writeVarInt(v.downedTicks); b.writeVarInt(v.recoveryTicks); b.writeVarInt(v.giveUp + 1); },
            b -> new RegenerationStatePayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt() - 1));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RegenerationStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRegenerationState.update(payload));
    }
}
