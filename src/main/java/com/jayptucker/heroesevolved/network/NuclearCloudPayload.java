package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.NuclearCloudClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** An independent world-space cloud, visible without tracking its creator or receiving particles. */
public record NuclearCloudPayload(UUID id, ResourceLocation dimension, Vec3 center, float scale, int radius, int age)
        implements CustomPacketPayload {
    public static final Type<NuclearCloudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "nuclear_cloud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NuclearCloudPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUUID(v.id); b.writeResourceLocation(v.dimension); b.writeVec3(v.center);
                b.writeFloat(v.scale); b.writeVarInt(v.radius); b.writeVarInt(v.age); },
            b -> new NuclearCloudPayload(b.readUUID(), b.readResourceLocation(), b.readVec3(), b.readFloat(), b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(NuclearCloudPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NuclearCloudClient.update(payload));
    }
}
