package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MobTimePayload(int entity, int interval) implements CustomPacketPayload {
    public static final Type<MobTimePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mob_time"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MobTimePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.entity); b.writeVarInt(p.interval); }, b -> new MobTimePayload(b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(MobTimePayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientMobTime.accept(p));
    }
}
