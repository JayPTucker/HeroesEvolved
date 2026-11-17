package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * A village's distress flare ({@code radio/EmergencyBand}): where it burns, what kind of trouble (its colour), and whether
 * it still does. Sent to everyone within the call's reach when it starts, again every few seconds while it lasts (so a
 * player who rides into range sees it), and once more when it ends.
 */
public record DistressPayload(UUID id, Vec3 at, int kind, boolean live) implements CustomPacketPayload {
    public static final Type<DistressPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "distress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DistressPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUUID(p.id);
        buf.writeDouble(p.at.x); buf.writeDouble(p.at.y); buf.writeDouble(p.at.z);
        buf.writeVarInt(p.kind);
        buf.writeBoolean(p.live);
    }, buf -> new DistressPayload(buf.readUUID(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DistressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientDistress.accept(payload));
    }
}
