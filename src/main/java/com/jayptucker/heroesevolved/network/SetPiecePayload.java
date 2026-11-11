package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Starts a set piece on the client: which one, where (and toward what), how big, the entity it follows
 * (-1 for none), a seed for its random details, and how many ticks to wait before it begins.
 */
public record SetPiecePayload(int piece, Vec3 origin, Vec3 target, float scale, int entity, int seed, int delay)
        implements CustomPacketPayload {
    public static final Type<SetPiecePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "set_piece"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetPiecePayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.piece);
        buf.writeDouble(p.origin.x); buf.writeDouble(p.origin.y); buf.writeDouble(p.origin.z);
        buf.writeDouble(p.target.x); buf.writeDouble(p.target.y); buf.writeDouble(p.target.z);
        buf.writeFloat(p.scale);
        buf.writeVarInt(p.entity + 1);
        buf.writeInt(p.seed);
        buf.writeVarInt(p.delay);
    }, buf -> new SetPiecePayload(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readVarInt() - 1, buf.readInt(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetPiecePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.add(payload));
    }

    /** Shows {@code piece} to everyone close enough to see it. */
    public static void send(ServerLevel level, Piece piece, Vec3 origin, Vec3 target, float scale, Entity bound, int delay) {
        int seed = level.random.nextInt();
        SetPiecePayload payload = new SetPiecePayload(piece.ordinal(), origin, target, scale, bound == null ? -1 : bound.getId(), seed, Math.max(0, delay));
        double range = 128 + scale * 4;
        for (ServerPlayer viewer : level.players())
            if (viewer.position().distanceToSqr(origin) <= range * range || viewer.position().distanceToSqr(target) <= range * range)
                PacketDistributor.sendToPlayer(viewer, payload);
    }

    public static void send(ServerLevel level, Piece piece, Vec3 origin, Vec3 target, float scale, Entity bound) {
        send(level, piece, origin, target, scale, bound, 0);
    }
}
