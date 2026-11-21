package com.jayptucker.heroesevolved.hazard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ground burnt by something that isn't a player's fire move (an overload's wildfire, a meteor's crater rim): grass burns
 * to dirt, which grows back, and plants burn away. Nothing built is touched. Callers decide whether {@code mobGriefing}
 * lets them.
 */
public final class Scorch {
    private Scorch() {}

    /** Scorches the top of the column at {@code column}. */
    public static void column(ServerLevel level, BlockPos column) {
        if (!level.hasChunkAt(column)) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos plant = new BlockPos(column.getX(), y, column.getZ()), ground = plant.below();
        if (burnable(level.getBlockState(plant))) level.setBlock(plant, Blocks.AIR.defaultBlockState(), 3);
        if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
    }

    /** Grass, ferns, flowers and saplings burn away; crops, leaves and anything built don't. */
    public static boolean burnable(BlockState s) {
        return !s.isAir() && !s.is(BlockTags.CROPS) && !s.is(BlockTags.LEAVES) && s.getFluidState().isEmpty()
                && (s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS));
    }
}
