package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Records the most recent minute of player movement and block actions, and
 * the movement of mobs near anyone able to create a Temporal Snapshot.
 */
public final class TemporalEchoRecordingService {
    private static final int RECORDING_LENGTH_TICKS = 20 * 60;
    // A one-tick sample gives the playback enough detail to mirror normal
    // walking and sprinting smoothly instead of approximating four-tick hops.
    private static final int SAMPLE_INTERVAL_TICKS = 1;
    // Mobs move far more slowly than sprinting players, and a snapshot can
    // hold dozens of them, so they are sampled less often and interpolated.
    private static final int ENTITY_SAMPLE_INTERVAL_TICKS = 3;
    private static final int MAX_RECORDED_ENTITIES_PER_PLAYER = 64;
    private static final int MAX_CAPTURED_ENTITIES = 48;
    // Temporal Snapshot unlocks at Level 5; recording mobs for anyone below
    // that would spend server time on history nobody can visit.
    private static final int SNAPSHOT_UNLOCK_LEVEL = 5;
    private static final Map<UUID, PlayerRecording> RECORDINGS = new HashMap<>();
    private static final Map<UUID, EntityRecording> ENTITY_RECORDINGS =
            new HashMap<>();

    private TemporalEchoRecordingService() {
    }

    public static int getRecordingLengthTicks() {
        return RECORDING_LENGTH_TICKS;
    }

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            // Time spent in the Past is not part of the Present's history.
            if (TemporalSnapshotService.isTemporalDimension(level)) {
                continue;
            }

            PlayerRecording recording = RECORDINGS.computeIfAbsent(
                    player.getUUID(), ignored -> new PlayerRecording()
            );
            // Frames from another dimension use unrelated coordinates and
            // would teleport the echo far outside its snapshot.
            recording.resetIfDimensionChanged(level.dimension());
            if (gameTime % SAMPLE_INTERVAL_TICKS == 0) {
                recording.frames().addLast(new RecordedFrame(
                        gameTime,
                        player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot(),
                        player.getYHeadRot()
                ));
            }
            recording.discardOlderThan(gameTime - RECORDING_LENGTH_TICKS);
        }

        if (gameTime % ENTITY_SAMPLE_INTERVAL_TICKS == 0) {
            recordNearbyEntities(server, gameTime);
        }
    }

    public static void forgetPlayer(UUID playerId) {
        RECORDINGS.remove(playerId);
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
                .filter(player -> !player.isSpectator()
                        && player.getX() >= sourceMinX
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

    /**
     * Captures the mobs and animals inside a new snapshot: their full saved
     * state, so the Past can recreate them exactly, and the movement they
     * made during the minute before the snapshot.
     */
    public static List<TemporalEntityEchoData> captureEntitiesInside(
            ServerPlayer owner,
            int sourceMinX,
            int sourceMinZ,
            int widthBlocks
    ) {
        ServerLevel level = owner.serverLevel();
        long snapshotTime = level.getGameTime();
        long firstPlaybackTime = snapshotTime - RECORDING_LENGTH_TICKS;
        AABB region = new AABB(
                sourceMinX, level.getMinBuildHeight(), sourceMinZ,
                sourceMinX + widthBlocks, level.getMaxBuildHeight(),
                sourceMinZ + widthBlocks
        );

        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        region,
                        TemporalEchoRecordingService::isRecordableEntity
                ).stream()
                .sorted(Comparator.comparingDouble(owner::distanceToSqr))
                .limit(MAX_CAPTURED_ENTITIES)
                .map(entity -> captureEntity(
                        entity, level.dimension(), firstPlaybackTime
                ))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static TemporalEntityEchoData captureEntity(
            LivingEntity entity,
            ResourceKey<Level> dimension,
            long firstPlaybackTime
    ) {
        CompoundTag tag = new CompoundTag();
        if (!entity.saveAsPassenger(tag)) {
            return null;
        }
        // Each captured entity is recreated on its own; its passengers are
        // captured separately. The Past copy also needs a fresh identity.
        tag.remove("Passengers");
        tag.remove("UUID");

        EntityRecording recording = ENTITY_RECORDINGS.get(entity.getUUID());
        List<RecordedFrame> frames = recording == null
                || !recording.dimension().equals(dimension)
                ? List.of()
                : recording.frames().stream()
                        .filter(frame -> frame.gameTime() >= firstPlaybackTime)
                        .toList();

        int[] packed = new int[frames.size() * TemporalEntityEchoData.FRAME_STRIDE];
        for (int index = 0; index < frames.size(); index++) {
            RecordedFrame frame = frames.get(index);
            TemporalEntityEchoData.packFrame(
                    packed, index,
                    (int) (frame.gameTime() - firstPlaybackTime),
                    frame.x(), frame.y(), frame.z(),
                    frame.yaw(), frame.pitch(), frame.headYaw()
            );
        }
        return new TemporalEntityEchoData(entity.getUUID(), tag, packed);
    }

    private static boolean isRecordableEntity(LivingEntity entity) {
        return entity.isAlive()
                && !(entity instanceof Player)
                && !(entity instanceof TemporalEchoEntity)
                && !TemporalEntityEchoService.isPastCopy(entity);
    }

    private static void recordNearbyEntities(
            MinecraftServer server,
            long gameTime
    ) {
        double radius = HeroesEvolvedConfig.COMMON.timeSnapshotWidthChunks.get()
                * 8.0D + 16.0D;
        Set<UUID> sampledThisTick = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            if (TemporalSnapshotService.isTemporalDimension(level)
                    || !canCreateSnapshots(player)) {
                continue;
            }

            AABB area = new AABB(
                    player.getX() - radius, level.getMinBuildHeight(),
                    player.getZ() - radius,
                    player.getX() + radius, level.getMaxBuildHeight(),
                    player.getZ() + radius
            );
            List<LivingEntity> nearby = level.getEntitiesOfClass(
                            LivingEntity.class,
                            area,
                            TemporalEchoRecordingService::isRecordableEntity
                    ).stream()
                    .sorted(Comparator.comparingDouble(player::distanceToSqr))
                    .limit(MAX_RECORDED_ENTITIES_PER_PLAYER)
                    .toList();

            for (LivingEntity entity : nearby) {
                // Two snapshot users standing together share one recording.
                if (!sampledThisTick.add(entity.getUUID())) {
                    continue;
                }

                EntityRecording recording = ENTITY_RECORDINGS.computeIfAbsent(
                        entity.getUUID(),
                        ignored -> new EntityRecording(level.dimension())
                );
                if (!recording.dimension().equals(level.dimension())) {
                    recording = new EntityRecording(level.dimension());
                    ENTITY_RECORDINGS.put(entity.getUUID(), recording);
                }
                recording.frames().addLast(new RecordedFrame(
                        gameTime,
                        entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYRot(), entity.getXRot(),
                        entity.getYHeadRot()
                ));
                recording.lastSampledAt = gameTime;
            }
        }

        long oldestAllowed = gameTime - RECORDING_LENGTH_TICKS;
        Iterator<EntityRecording> iterator =
                ENTITY_RECORDINGS.values().iterator();
        while (iterator.hasNext()) {
            EntityRecording recording = iterator.next();
            recording.discardOlderThan(oldestAllowed);
            if (recording.lastSampledAt < oldestAllowed) {
                iterator.remove();
            }
        }
    }

    private static boolean canCreateSnapshots(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey()
                        .equals(ModAbilities.TIME_MANIPULATION_ID)
                        && entry.getValue().isUnlocked())
                .isPresent()
                && PlayerProgressionService.getLevel(player)
                >= SNAPSHOT_UNLOCK_LEVEL;
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
                actions,
                captureInventory(player)
        );
    }

    private static List<TemporalEchoInventoryEntry> captureInventory(
            ServerPlayer player
    ) {
        List<TemporalEchoInventoryEntry> entries = new java.util.ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory()
                    .getItem(slot);
            if (!stack.isEmpty()) {
                entries.add(new TemporalEchoInventoryEntry(slot, stack.copy()));
            }
        }
        return List.copyOf(entries);
    }

    private static final class PlayerRecording {
        private final Deque<RecordedFrame> frames = new ArrayDeque<>();
        private final Deque<RecordedAction> actions = new ArrayDeque<>();
        private ResourceKey<Level> dimension;

        private Deque<RecordedFrame> frames() {
            return frames;
        }

        private Deque<RecordedAction> actions() {
            return actions;
        }

        private void resetIfDimensionChanged(ResourceKey<Level> current) {
            if (!current.equals(dimension)) {
                frames.clear();
                actions.clear();
                dimension = current;
            }
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

    private static final class EntityRecording {
        private final ResourceKey<Level> dimension;
        private final Deque<RecordedFrame> frames = new ArrayDeque<>();
        private long lastSampledAt;

        private EntityRecording(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private Deque<RecordedFrame> frames() {
            return frames;
        }

        private void discardOlderThan(long oldestAllowedTime) {
            while (!frames.isEmpty()
                    && frames.getFirst().gameTime() < oldestAllowedTime) {
                frames.removeFirst();
            }
        }
    }

    private record RecordedFrame(
            long gameTime,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            float headYaw
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
