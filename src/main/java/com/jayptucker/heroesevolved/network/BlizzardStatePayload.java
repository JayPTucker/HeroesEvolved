package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.BlizzardFog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** Short-lived storm volumes: a viewer computes fog locally when entering or leaving one. */
public record BlizzardStatePayload(UUID owner, Vec3 center, float radius, int ticks) implements CustomPacketPayload {
    public static final Type<BlizzardStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "blizzard_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlizzardStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUUID(v.owner); b.writeVec3(v.center); b.writeFloat(v.radius); b.writeVarInt(v.ticks); },
            b -> new BlizzardStatePayload(b.readUUID(), b.readVec3(), b.readFloat(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BlizzardStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BlizzardFog.update(payload));
    }
}
