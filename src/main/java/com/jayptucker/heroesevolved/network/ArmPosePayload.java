package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells clients how a player is holding their arms for a power. A zero
 * duration clears the pose. Sent to everyone tracking the player, and the
 * player themself for third-person and first-person views.
 */
public record ArmPosePayload(
        int entityId,
        ArmPose pose,
        int durationTicks
) implements CustomPacketPayload {
    public static final Type<ArmPosePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "arm_pose"
            ));

    private static final StreamCodec<RegistryFriendlyByteBuf, ArmPose> POSE_CODEC =
            ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(
                    index -> ArmPose.values()[Math.floorMod(index, ArmPose.values().length)],
                    ArmPose::ordinal
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ArmPosePayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ArmPosePayload::entityId,
            POSE_CODEC,
            ArmPosePayload::pose,
            ByteBufCodecs.VAR_INT,
            ArmPosePayload::durationTicks,
            ArmPosePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArmPosePayload payload, IPayloadContext context) {
        ClientArmPoseState.set(payload.entityId(), payload.pose(),
                payload.durationTicks());
    }

    /** Holds a pose for a while, e.g. the moment a blast leaves the hand. */
    /** The gesture the server last sent for each player, and the game tick it ends. */
    private record Shown(ArmPose pose, long until) {}
    private static final java.util.Map<ServerPlayer, Shown> SHOWN = new java.util.WeakHashMap<>();

    /**
     * The gesture still playing for {@code player}, or null. A held pose re-sent on a timer (a stance) asks this first,
     * so it takes over only once a move's own gesture has finished, instead of cutting it off half-way.
     */
    public static ArmPose playing(ServerPlayer player) {
        Shown shown = SHOWN.get(player);
        return shown == null || player.getServer() == null || shown.until <= player.getServer().overworld().getGameTime() ? null : shown.pose;
    }

    public static void show(ServerPlayer player, ArmPose pose, int durationTicks) {
        if (player.getServer() != null)
            SHOWN.put(player, new Shown(pose, player.getServer().overworld().getGameTime() + Math.max(0, durationTicks)));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new ArmPosePayload(player.getId(), pose, durationTicks)
        );
    }

    /** Drops any pose at once, e.g. when a held power is released. */
    public static void clear(ServerPlayer player) {
        show(player, ArmPose.ONE_HAND, 0);
    }

    /** Ends one gesture without dropping a newer pose started by a different action. */
    public static void clearIf(ServerPlayer player, ArmPose pose) {
        show(player, pose, -1);
    }
}
