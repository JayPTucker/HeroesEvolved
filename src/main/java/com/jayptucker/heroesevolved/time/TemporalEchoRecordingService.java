package com.jayptucker.heroesevolved.time;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Records the most recent minute of player movement and block actions. */
public final class TemporalEchoRecordingService {
    private static final int RECORDING_LENGTH_TICKS = 20 * 60;
    private static final int SAMPLE_INTERVAL_TICKS = 4;
    private static final Map<UUID, PlayerRecording> RECORDINGS = new HashMap<>();

    private TemporalEchoRecordingService() {
    }

    public static int getRecordingLengthTicks() {
        return RECORDING_LENGTH_TICKS;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long gameTime = player.serverLevel().getGameTime();
            PlayerRecording recording = RECORDINGS.computeIfAbsent(
                    player.getUUID(), ignored -> new PlayerRecording()
            );
            if (gameTime % SAMPLE_INTERVAL_TICKS == 0) {
                recording.frames().addLast(new RecordedFrame(
                        gameTime,
                        player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot()
                ));
            }
            recording.discardOlderThan(gameTime - RECORDING_LENGTH_TICKS);
        }
    }

    public static void recordBlockAction(
            ServerPlayer player,
            BlockPos position,
            BlockState state,
            boolean placement
    ) {
        PlayerRecording recording = RECORDINGS.computeIfAbsent(
                player.getUUID(), ignored -> new PlayerRecording()
        );
        recording.actions().addLast(new RecordedAction(
                player.serverLevel().getGameTime(),
                position.immutable(),
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                placement
        ));
    }

    public static List<TemporalEchoData> capturePlayersInside(
            ServerPlayer owner,
            int sourceMinX,
            int sourceMinZ,
            int widthBlocks
    ) {
        long snapshotTime = owner.serverLevel().getGameTime();
        int sourceMaxX = sourceMinX + widthBlocks;
        int sourceMaxZ = sourceMinZ + widthBlocks;

        return owner.serverLevel().players().stream()
                .filter(player -> player.getX() >= sourceMinX
                        && player.getX() < sourceMaxX
                        && player.getZ() >= sourceMinZ
                        && player.getZ() < sourceMaxZ)
                .map(player -> capturePlayer(
                        player,
                        snapshotTime,
                        sourceMinX,
                        sourceMinZ,
                        sourceMaxX,
                        sourceMaxZ
                ))
                .toList();
    }

    private static TemporalEchoData capturePlayer(
            ServerPlayer player,
            long snapshotTime,
            int sourceMinX,
            int sourceMinZ,
            int sourceMaxX,
            int sourceMaxZ
    ) {
        PlayerRecording recording = RECORDINGS.get(player.getUUID());
        long firstPlaybackTime = snapshotTime - RECORDING_LENGTH_TICKS;
        List<TemporalEchoFrame> frames = recording == null
                ? List.of()
                : recording.frames().stream()
                        .filter(frame -> frame.gameTime() >= firstPlaybackTime)
                        .map(frame -> new TemporalEchoFrame(
                                (int) (frame.gameTime() - firstPlaybackTime),
                                frame.x(), frame.y(), frame.z(),
                                frame.yaw(), frame.pitch()
                        ))
                        .toList();
        List<TemporalEchoAction> actions = recording == null
                ? List.of()
                : recording.actions().stream()
                        .filter(action -> action.gameTime() >= firstPlaybackTime
                                && action.position().getX() >= sourceMinX
                                && action.position().getX() < sourceMaxX
                                && action.position().getZ() >= sourceMinZ
                                && action.position().getZ() < sourceMaxZ)
                        .map(action -> new TemporalEchoAction(
                                (int) (action.gameTime() - firstPlaybackTime),
                                action.position(),
                                action.blockId(),
                                action.placement()
                        ))
                        .toList();

        return new TemporalEchoData(
                player.getUUID(),
                player.getName().getString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                frames,
                actions
        );
    }

    private static final class PlayerRecording {
        private final Deque<RecordedFrame> frames = new ArrayDeque<>();
        private final Deque<RecordedAction> actions = new ArrayDeque<>();

        private Deque<RecordedFrame> frames() {
            return frames;
        }

        private Deque<RecordedAction> actions() {
            return actions;
        }

        private void discardOlderThan(long oldestAllowedTime) {
            while (!frames.isEmpty()
                    && frames.getFirst().gameTime() < oldestAllowedTime) {
                frames.removeFirst();
            }
            while (!actions.isEmpty()
                    && actions.getFirst().gameTime() < oldestAllowedTime) {
                actions.removeFirst();
            }
        }
    }

    private record RecordedFrame(
            long gameTime,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
    }

    private record RecordedAction(
            long gameTime,
            BlockPos position,
            net.minecraft.resources.ResourceLocation blockId,
            boolean placement
    ) {
    }
}
