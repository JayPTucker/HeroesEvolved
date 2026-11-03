package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** role: 0 released, 1 entering, 2 controlling, 3 resisting, 4 shared sight. */
public record MindStatePayload(int role, int entity, int ticks, int resistance, int cue, int nonce, int focus, int strain) implements CustomPacketPayload {
    public static final Type<MindStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mind_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MindStatePayload> STREAM_CODEC = StreamCodec.of(
        (b,p) -> { b.writeVarInt(p.role); b.writeVarInt(p.entity); b.writeVarInt(p.ticks); b.writeVarInt(p.resistance); b.writeVarInt(p.cue); b.writeVarInt(p.nonce); b.writeVarInt(p.focus); b.writeVarInt(p.strain); },
        b -> new MindStatePayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(MindStatePayload p, IPayloadContext c) { c.enqueueWork(() -> com.jayptucker.heroesevolved.client.TelepathyClient.update(p)); }
}
