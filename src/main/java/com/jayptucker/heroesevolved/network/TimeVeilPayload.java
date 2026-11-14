package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.TimeVeil;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * A caster moving faster than time ({@link TimeVeil}): show their frozen stand-in, let it vanish (they are elsewhere),
 * or clear it (they are back on the spot, or this viewer sees them as they are).
 */
public record TimeVeilPayload(UUID owner, byte action, ResourceLocation dimension, Vec3 at, float yaw, float pitch, Vec3 to,
                              byte style) implements CustomPacketPayload {
    public static final byte SHOW = 0, VANISH = 1, CLEAR = 2;
    public static final Type<TimeVeilPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_veil"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeVeilPayload> STREAM_CODEC = StreamCodec.of((b, v) -> {
        UUIDUtil.STREAM_CODEC.encode(b, v.owner);
        b.writeByte(v.action);
        b.writeResourceLocation(v.dimension);
        b.writeDouble(v.at.x); b.writeDouble(v.at.y); b.writeDouble(v.at.z);
        b.writeFloat(v.yaw); b.writeFloat(v.pitch);
        b.writeDouble(v.to.x); b.writeDouble(v.to.y); b.writeDouble(v.to.z);
        b.writeByte(v.style);
    }, b -> new TimeVeilPayload(UUIDUtil.STREAM_CODEC.decode(b), b.readByte(), b.readResourceLocation(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readFloat(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readByte()));

    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("overworld");

    public static TimeVeilPayload show(UUID owner, ResourceLocation dimension, Vec3 at, float yaw, float pitch, TimeVeil.Style style) {
        return new TimeVeilPayload(owner, SHOW, dimension, at, yaw, pitch, at, (byte) style.ordinal());
    }

    public static TimeVeilPayload vanish(UUID owner, Vec3 at, Vec3 to, TimeVeil.Style style) {
        return new TimeVeilPayload(owner, VANISH, NONE, at, 0, 0, to, (byte) style.ordinal());
    }

    public static TimeVeilPayload clear(UUID owner) {
        return new TimeVeilPayload(owner, CLEAR, NONE, Vec3.ZERO, 0, 0, Vec3.ZERO, (byte) 0);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TimeVeilPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.TimeVeilClient.receive(payload));
    }
}
