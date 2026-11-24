package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A short turn of the player's own head; zero ticks releases it immediately. */
public record FutureLookPayload(Vec3 at, int ticks) implements CustomPacketPayload {
    public static final Type<FutureLookPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "future_look"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FutureLookPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeDouble(p.at.x); b.writeDouble(p.at.y); b.writeDouble(p.at.z); b.writeVarInt(p.ticks);
    }, b -> new FutureLookPayload(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(FutureLookPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.intro.FutureLook.start(p.at, p.ticks));
    }
}
