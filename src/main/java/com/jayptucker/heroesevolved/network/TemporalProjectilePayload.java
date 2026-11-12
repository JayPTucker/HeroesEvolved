package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Position and full-precision motion for shots that stop, slow, or resume. */
public record TemporalProjectilePayload(int entityId, int interval, Vec3 position, Vec3 motion) implements CustomPacketPayload {
    public static final Type<TemporalProjectilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "temporal_projectile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TemporalProjectilePayload> STREAM_CODEC = StreamCodec.of(
            (buf, shot) -> {
                buf.writeVarInt(shot.entityId);
                buf.writeVarInt(shot.interval);
                buf.writeVec3(shot.position);
                buf.writeVec3(shot.motion);
            }, buf -> new TemporalProjectilePayload(buf.readVarInt(), buf.readVarInt(), buf.readVec3(), buf.readVec3()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TemporalProjectilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientProjectileTime.apply(payload));
    }
}
