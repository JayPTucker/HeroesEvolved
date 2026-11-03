package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Snapshot of a public optical double. No authoritative entity or inventory is created. */
public record OpticalDecoyPayload(int id, int operation, int source, UUID owner, String name,
                                 Vec3 position, Vec3 velocity, float yaw, int remaining, boolean swing)
        implements CustomPacketPayload {
    public static final Type<OpticalDecoyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "optical_decoy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpticalDecoyPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.id); b.writeByte(p.operation); b.writeVarInt(p.source); b.writeUUID(p.owner);
                b.writeUtf(p.name, 16); b.writeVec3(p.position); b.writeVec3(p.velocity); b.writeFloat(p.yaw);
                b.writeVarInt(p.remaining); b.writeBoolean(p.swing); },
            b -> new OpticalDecoyPayload(b.readVarInt(), b.readByte(), b.readVarInt(), b.readUUID(), b.readUtf(16),
                    b.readVec3(), b.readVec3(), b.readFloat(), b.readVarInt(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(OpticalDecoyPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.OpticalDecoyClient.accept(packet));
    }
}
