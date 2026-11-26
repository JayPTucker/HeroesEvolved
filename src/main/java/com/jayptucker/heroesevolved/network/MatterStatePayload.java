package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A matter assembler's Matter, for themselves and everyone watching (assembly/Matter): how many crystal cells they carry
 * and can carry. Sent when it changes and every few seconds; the client draws the orbit every frame.
 */
public record MatterStatePayload(int entity, int matter, int capacity) implements CustomPacketPayload {
    public static final Type<MatterStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "matter_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatterStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.entity); b.writeVarInt(p.matter); b.writeVarInt(p.capacity); },
            b -> new MatterStatePayload(b.readVarInt(), b.readVarInt(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MatterStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientMatterState.update(payload));
    }
}
