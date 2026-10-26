package com.jayptucker.heroesevolved.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Checks the destination world, including when the actor currently stands in another dimension. */
public final class WorldMutationPolicy {
    private WorldMutationPolicy() {}
    public static boolean canEdit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.mayBuild() && level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos)
                && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos)
                && !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), level.getBlockState(pos), player)).isCanceled();
    }
    public static boolean place(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!canEdit(player, level, pos)) return false;
        BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, 3)) return false;
        if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(before, before.getState(), player)).isCanceled()) {
            before.restore(); return false;
        }
        return true;
    }
}
