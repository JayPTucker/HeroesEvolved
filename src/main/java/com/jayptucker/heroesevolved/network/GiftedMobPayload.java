package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A gifted mob as the client draws it ({@code gifted/GiftedMob}): which power's colour, its rarity, its state flags
 * ({@code GiftedMob.EVIL}, {@code CASTING}, {@code UNBOUND}, {@code SPENT}, {@code BOSS}, {@code STAGGERED}; -1 means it is
 * no longer gifted), the pose of the move it is winding up and that windup's length. Sent to everyone tracking the mob,
 * on change and when they start tracking it.
 */
public record GiftedMobPayload(int entity, int power, int rarity, int flags, int pose, int windup) implements CustomPacketPayload {
    public static final Type<GiftedMobPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "gifted_mob"));
    public static final StreamCodec<ByteBuf, GiftedMobPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        net.minecraft.network.VarInt.write(buf, p.entity);
        buf.writeByte(p.power);
        buf.writeByte(p.rarity);
        buf.writeByte(p.flags);
        buf.writeByte(p.pose);
        buf.writeShort(p.windup);
    }, buf -> new GiftedMobPayload(net.minecraft.network.VarInt.read(buf), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readShort()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GiftedMobPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientGifted.accept(payload));
    }
}
