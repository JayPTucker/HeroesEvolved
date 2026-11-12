package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A server-owned impulse that exceeds the vanilla motion packet's 3.9-block component limit. */
public record SkyburstMotionPayload(Vec3 velocity) implements CustomPacketPayload {
    public static final Type<SkyburstMotionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "skyburst_motion"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyburstMotionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.velocity.x);
                buf.writeDouble(payload.velocity.y);
                buf.writeDouble(payload.velocity.z);
            }, buf -> new SkyburstMotionPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkyburstMotionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> context.player().setDeltaMovement(payload.velocity));
    }
}
