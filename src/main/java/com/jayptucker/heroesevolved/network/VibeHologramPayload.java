package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Where a viber's hologram stands ({@code vibe/VibeVisions}), sent every other tick to the players near it, or that it
 * is gone. The viber is a spectator during a vision, so other clients don't know their entity: the hologram is drawn
 * from this alone, with the skin the client's player list knows for {@code owner}.
 */
public record VibeHologramPayload(UUID owner, boolean shown, Vec3 at, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<VibeHologramPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "vibe_hologram"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VibeHologramPayload> STREAM_CODEC = StreamCodec.of((b, v) -> {
        UUIDUtil.STREAM_CODEC.encode(b, v.owner);
        b.writeBoolean(v.shown);
        b.writeDouble(v.at.x); b.writeDouble(v.at.y); b.writeDouble(v.at.z);
        b.writeFloat(v.yaw); b.writeFloat(v.pitch);
    }, b -> new VibeHologramPayload(UUIDUtil.STREAM_CODEC.decode(b), b.readBoolean(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readFloat()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(VibeHologramPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.VibeHologramClient.receive(payload));
    }
}
