package com.jayptucker.heroesevolved.time;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;

/** Records ticking block-entity data even when its block state does not change. */
public final class TimeRewindContainers {
    public record Cell(BlockState state, CompoundTag data) {}
    public record Frame(Map<BlockPos, Cell> cells, boolean complete) {}
    private TimeRewindContainers() {}
    public static Frame capture(ServerPlayer player) {
        var level = player.serverLevel(); double radius = TimeRewindBlocks.RECORDING_RADIUS;
        int chunks = (int) Math.ceil(radius / 16) + 1;
        Map<BlockPos, Cell> cells = new HashMap<>();
        for (int x = -chunks; x <= chunks; x++) for (int z = -chunks; z <= chunks; z++) {
            var chunk = level.getChunkSource().getChunkNow(player.chunkPosition().x + x, player.chunkPosition().z + z);
            if (chunk == null) continue;
            for (var entry : chunk.getBlockEntities().entrySet()) {
                if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(entry.getKey())) > radius * radius) continue;
                var block = entry.getValue();
                cells.put(entry.getKey().immutable(), new Cell(block.getBlockState(), block.saveWithFullMetadata(level.registryAccess())));
                if (cells.size() > 2048) return new Frame(Map.of(), false);
            }
        }
        return new Frame(Map.copyOf(cells), true);
    }
    public static void restore(ServerPlayer player, Frame frame) {
        var level = player.serverLevel();
        for (var entry : frame.cells.entrySet()) {
            var entity = level.getBlockEntity(entry.getKey());
            if (entity != null && level.getBlockState(entry.getKey()).getBlock() == entry.getValue().state.getBlock()
                    && WorldMutationPolicy.canEdit(player, level, entry.getKey())) {
                entity.loadWithComponents(entry.getValue().data.copy(), level.registryAccess()); entity.setChanged();
                level.sendBlockUpdated(entry.getKey(), entity.getBlockState(), entity.getBlockState(), 3);
            }
        }
    }
}
