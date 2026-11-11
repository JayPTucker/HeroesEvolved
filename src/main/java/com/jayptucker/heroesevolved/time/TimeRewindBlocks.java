package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.util.BlockSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Bounded before-images of real block mutations, shared by nearby time users. A change is journaled for a
 * time user if it happens within {@link #RECORDING_RADIUS} of where they are, or of anywhere they have been
 * within their journal's retention (its anchors): a block placed where you stood a minute ago, after you left,
 * is still undone when you go back to that minute.
 */
public final class TimeRewindBlocks {
    public static final double RECORDING_RADIUS = 64.0D;
    private static final int RETENTION_TICKS = (TimeRewindService.REWIND_SECONDS + 1) * 20;
    /** The longest any journal keeps: a speedster outrunning the last minute needs a minute and a little more. */
    public static final int LONGEST_RETENTION_TICKS = 64 * 20;
    private static final int MAX_CHANGES = 524_288;
    private static final Map<UUID, Journal> JOURNALS = new HashMap<>();
    private static final Map<UUID, LooseDrop> DROPS = new HashMap<>();
    private static final Deque<PendingChange> MUTATIONS = new ArrayDeque<>();
    private static long revision;
    private static boolean restoring;

    private TimeRewindBlocks() {
    }

    public static long track(ServerPlayer player) {
        return track(player, RETENTION_TICKS);
    }

    /** Keeps (or starts) this player's journal, holding changes for {@code retentionTicks}. */
    public static long track(ServerPlayer player, int retentionTicks) {
        JOURNALS.computeIfAbsent(player.getUUID(), ignored -> new Journal()).retention = Math.min(retentionTicks, LONGEST_RETENTION_TICKS);
        return revision;
    }

    /**
     * Marks where this player is at {@code time}: changes near it are journaled until it is older than the
     * journal's retention, wherever the player goes meanwhile. A spot within a third of the recording radius of
     * the last one only refreshes its time.
     */
    public static void anchor(ServerPlayer player, long time) {
        Journal journal = JOURNALS.get(player.getUUID());
        if (journal == null) return;
        Anchor last = journal.anchors.peekLast();
        Anchor here = new Anchor(player.level().dimension(), player.position(), time);
        if (last != null && last.dimension.equals(here.dimension) && last.at.distanceToSqr(here.at) < (RECORDING_RADIUS / 3) * (RECORDING_RADIUS / 3))
            journal.anchors.pollLast();
        journal.anchors.addLast(here);
        while (journal.anchors.size() > 256) journal.anchors.pollFirst();
    }

    /** Does this player's journal cover {@code at} through somewhere they have been recently? */
    public static boolean covers(UUID player, ResourceKey<Level> dimension, Vec3 at) {
        Journal journal = JOURNALS.get(player);
        return journal != null && journal.covers(dimension, at);
    }

    /** Is this player's journal running? If it was dropped, history recorded against it is incomplete. */
    public static boolean isTracking(UUID player) {
        return JOURNALS.containsKey(player);
    }

    public static PendingChange begin(ServerLevel level, BlockPos position, BlockState replacement) {
        if (restoring || !level.getServer().isSameThread()
                || level.isOutsideBuildHeight(position) || JOURNALS.isEmpty()) {
            return null;
        }
        List<UUID> viewers = viewers(level, position);
        if (viewers.isEmpty() || level.getBlockState(position) == replacement) {
            return null;
        }
        PendingChange change = new PendingChange(++revision,
                level.getServer().overworld().getGameTime(),
                BlockSnapshot.create(level.dimension(), level, position), viewers);
        MUTATIONS.push(change);
        return change;
    }

    public static void finish(PendingChange change, boolean changed) {
        if (change == null) {
            return;
        }
        MUTATIONS.removeFirstOccurrence(change);
        if (!changed) {
            return;
        }
        for (UUID viewer : change.viewers) {
            Journal journal = JOURNALS.get(viewer);
            if (journal != null) {
                journal.changes.put(change.revision, change);
                while (journal.changes.size() > MAX_CHANGES) {
                    journal.minimumRevision = Math.max(journal.minimumRevision,
                            journal.changes.pollFirstEntry().getKey());
                }
            }
        }
    }

    public static void recordDrops(ServerLevel level, BlockPos position, List<ItemEntity> items) {
        if (restoring || items.isEmpty() || JOURNALS.isEmpty()) {
            return;
        }
        List<UUID> viewers = viewers(level, position);
        if (viewers.isEmpty()) {
            return;
        }
        long dropRevision = ++revision;
        long time = level.getServer().overworld().getGameTime();
        for (ItemEntity item : items) {
            DROPS.putIfAbsent(item.getUUID(), new LooseDrop(level, position.immutable(),
                    dropRevision, time, List.copyOf(viewers), item));
        }
    }

    /** Container contents can be spawned directly during a block's onRemove. */
    public static void recordContainerDrop(ServerLevel level, ItemEntity item) {
        PendingChange change = MUTATIONS.peek();
        if (change != null && change.before.getDimension().equals(level.dimension())
                && change.before.getTag() != null) {
            recordDrops(level, change.before.getPos(), List.of(item));
        }
    }

    public static boolean isRestoring() {
        return restoring;
    }

    public static Plan plan(ServerPlayer player, long targetRevision) {
        return plan(player, targetRevision, player.level().dimension());
    }

    /** What putting {@code dimension} back as it was at {@code targetRevision} takes, as this player's journal saw it. */
    public static Plan plan(ServerPlayer player, long targetRevision, ResourceKey<Level> dimension) {
        Journal journal = JOURNALS.get(player.getUUID());
        if (journal == null || targetRevision < journal.minimumRevision) {
            return null;
        }
        Map<BlockPos, BlockSnapshot> blocks = new LinkedHashMap<>();
        for (PendingChange change : journal.changes.tailMap(targetRevision, false).values()) {
            if (change.before.getDimension().equals(dimension)) {
                // Earliest before-image wins, even if a block changed repeatedly.
                blocks.putIfAbsent(change.before.getPos(), change.before);
            }
        }
        return new Plan(Map.copyOf(blocks), targetRevision, dimension);
    }

    private static ServerLevel level(ServerPlayer player, Plan plan) {
        ServerLevel level = player.getServer().getLevel(plan.dimension);
        return level == null ? player.serverLevel() : level;
    }

    /** Is anyone recording (a time user or a fast enough speedster)? */
    public static boolean recording() {
        return !JOURNALS.isEmpty();
    }

    /** Test the destination against the terrain that will exist after restoration. */
    public static boolean canOccupy(ServerPlayer player, AABB destination, Plan plan) {
        ServerLevel level = level(player, plan);
        if (!level.getWorldBorder().isWithinBounds(destination)
                || !level.getEntityCollisions(player, destination).isEmpty()) {
            return false;
        }
        var playerShape = Shapes.create(destination);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(destination.minX - 1, destination.minY - 1, destination.minZ - 1),
                BlockPos.containing(destination.maxX + 1, destination.maxY + 1, destination.maxZ + 1))) {
            BlockSnapshot snapshot = plan.blocks.get(pos);
            BlockState state = snapshot == null ? level.getBlockState(pos) : snapshot.getState();
            var shape = state.getCollisionShape(level, pos, CollisionContext.of(player))
                    .move(pos.getX(), pos.getY(), pos.getZ());
            if (Shapes.joinIsNotEmpty(playerShape, shape, BooleanOp.AND)) {
                return false;
            }
        }
        return true;
    }

    public static void restore(ServerPlayer player, Plan plan) {
        ServerLevel level = level(player, plan);
        List<PendingChange> inverses = new ArrayList<>();
        for (BlockSnapshot snapshot : plan.blocks.values()) {
            // Preserve the restoration in other players' timelines as well.
            PendingChange inverse = begin(level, snapshot.getPos(), snapshot.getState());
            if (inverse != null) {
                inverses.add(inverse);
            }
        }
        restoring = true;
        boolean oldRestoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
            for (BlockSnapshot snapshot : plan.blocks.values()) {
                BlockPos pos = snapshot.getPos();
                if (!com.jayptucker.heroesevolved.effects.WorldMutationPolicy.canEdit(player, level, pos)) continue;
                if (level.getBlockState(pos) != snapshot.getState()) {
                    // Avoid spilling a container a second time during rollback.
                    level.removeBlockEntity(pos);
                    snapshot.restore(Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                } else {
                    snapshot.restoreBlockEntity(level, pos);
                }
            }
            // Restore supporting blocks and both halves of structures before physics.
            for (BlockSnapshot snapshot : plan.blocks.values()) {
                level.updateNeighborsAt(snapshot.getPos(), snapshot.getState().getBlock());
                level.sendBlockUpdated(snapshot.getPos(), snapshot.getState(),
                        level.getBlockState(snapshot.getPos()), Block.UPDATE_CLIENTS);
            }
        } finally {
            level.restoringBlockSnapshots = oldRestoring;
            restoring = false;
            for (PendingChange inverse : inverses) {
                finish(inverse, true);
            }
        }
        DROPS.values().removeIf(drop -> {
            if (drop.level == level && drop.revision > plan.targetRevision
                    && drop.viewers.contains(player.getUUID()) && plan.blocks.containsKey(drop.position)) {
                if (!drop.entity.isRemoved()) {
                    drop.entity.discard();
                }
                return true;
            }
            return false;
        });
        int effects = 0;
        for (BlockSnapshot snapshot : plan.blocks.values()) {
            if (effects++ >= 24) {
                break;
            }
            Vec3 center = Vec3.atCenterOf(snapshot.getPos());
            AbilityEffects.send(level, Kind.TEMPORAL_BURST, center, center.add(0, 1, 0), 0.6D);
        }
    }

    public static void prune(long gameTime) {
        for (Journal journal : JOURNALS.values()) {
            while (!journal.anchors.isEmpty() && journal.anchors.peekFirst().time < gameTime - journal.retention) journal.anchors.pollFirst();
            while (!journal.changes.isEmpty()
                    && journal.changes.firstEntry().getValue().gameTime < gameTime - journal.retention) {
                journal.minimumRevision = Math.max(journal.minimumRevision,
                        journal.changes.pollFirstEntry().getKey());
            }
        }
        DROPS.values().removeIf(drop -> drop.gameTime < gameTime - LONGEST_RETENTION_TICKS);
    }

    public static void forget(UUID player) {
        JOURNALS.remove(player);
    }

    public static void clear() {
        JOURNALS.clear();
        DROPS.clear();
        MUTATIONS.clear();
        revision = 0;
        restoring = false;
    }

    private static List<UUID> viewers(ServerLevel level, BlockPos position) {
        List<UUID> result = new ArrayList<>();
        Vec3 at = Vec3.atCenterOf(position);
        for (ServerPlayer player : level.players()) {
            if (JOURNALS.containsKey(player.getUUID()) && player.isAlive()
                    && player.distanceToSqr(at) <= RECORDING_RADIUS * RECORDING_RADIUS) {
                result.add(player.getUUID());
            }
        }
        for (var entry : JOURNALS.entrySet())
            if (!result.contains(entry.getKey()) && entry.getValue().covers(level.dimension(), at)) result.add(entry.getKey());
        return result;
    }

    private record Anchor(ResourceKey<Level> dimension, Vec3 at, long time) {}

    private static final class Journal {
        private final NavigableMap<Long, PendingChange> changes = new TreeMap<>();
        private final Deque<Anchor> anchors = new ArrayDeque<>();
        private long minimumRevision;
        private int retention = RETENTION_TICKS;

        boolean covers(ResourceKey<Level> dimension, Vec3 at) {
            for (Anchor anchor : anchors)
                if (anchor.dimension.equals(dimension) && anchor.at.distanceToSqr(at) <= RECORDING_RADIUS * RECORDING_RADIUS) return true;
            return false;
        }
    }

    public record PendingChange(long revision, long gameTime, BlockSnapshot before, List<UUID> viewers) {
    }

    public record Plan(Map<BlockPos, BlockSnapshot> blocks, long targetRevision, ResourceKey<Level> dimension) {
    }

    private record LooseDrop(ServerLevel level, BlockPos position, long revision, long gameTime,
                             List<UUID> viewers, ItemEntity entity) {
    }
}
