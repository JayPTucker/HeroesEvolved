package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.TimeGhosts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A ghost of a time manipulator: a translucent copy of their body somewhere they were, or will be.
 * An ECHO is left behind (where Fold Space started, along a Rewind's path) and fades on its own.
 * The MARKER is private: where Rewind would take you right now, refreshed while it stays true.
 */
public record TimeGhostPayload(int entityId, Vec3 position, float yaw, int mode, int life, int delay) implements CustomPacketPayload {
    public static final int ECHO = 0, MARKER = 1;
    public static final Type<TimeGhostPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_ghost"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeGhostPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.entityId); b.writeVec3(v.position); b.writeFloat(v.yaw); b.writeVarInt(v.mode);
                b.writeVarInt(v.life); b.writeVarInt(v.delay); },
            b -> new TimeGhostPayload(b.readVarInt(), b.readVec3(), b.readFloat(), b.readVarInt(),
                    Math.clamp(b.readVarInt(), 1, 200), Math.clamp(b.readVarInt(), 0, 60)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TimeGhostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TimeGhosts.receive(payload));
    }

    /** An echo of this player at a place they were, seen by everyone nearby. */
    public static void echo(ServerPlayer player, Vec3 at, float yaw, int life, int delay) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TimeGhostPayload(player.getId(), at, yaw, ECHO, life, delay));
    }

    /** The private marker: where Rewind would put this player right now. */
    public static void marker(ServerPlayer player, Vec3 at, float yaw) {
        PacketDistributor.sendToPlayer(player, new TimeGhostPayload(player.getId(), at, yaw, MARKER, 14, 0));
    }

}
