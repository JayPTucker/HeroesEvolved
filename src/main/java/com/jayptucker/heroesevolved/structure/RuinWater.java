package com.jayptucker.heroesevolved.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Generation-time flooding and buoyancy. No ticking entities or respawning salvage. */
public final class RuinWater {
    private RuinWater() {}

    public static boolean water(LevelReader level, BlockPos p) {
        return level.getFluidState(p).is(FluidTags.WATER);
    }

    /** First air cell over this water column; never punch through ice, a roof or the build limit. */
    public static BlockPos surface(LevelReader level, BlockPos submerged) {
        if (!water(level, submerged)) return submerged;
        BlockPos p = submerged;
        while (p.getY() < level.getMaxBuildHeight() - 1 && water(level, p)) p = p.above();
        return level.getBlockState(p).isAir() ? p : submerged;
    }

    public static BlockState flooded(BlockState state) {
        // A century underwater leaves open rooms, not air pockets or terrestrial foliage.
        if (state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced()
                || state.getBlock() instanceof CarpetBlock || state.getBlock() instanceof BedBlock
                || state.getBlock() instanceof FlowerPotBlock || state.is(Blocks.CAMPFIRE)) return Blocks.WATER.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) return state.setValue(BlockStateProperties.WATERLOGGED, true);
        return state;
    }

    /** A few broken boards from the original building, selected identically in every generation chunk. */
    public static boolean looseBoard(BlockState state, BlockPos local) {
        return state.is(BlockTags.WOODEN_SLABS) && Math.floorMod(local.getX() * 31 + local.getY() * 17 + local.getZ() * 13, 5) == 0;
    }

    public static BlockState floating(BlockState state) {
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) state = state.setValue(BlockStateProperties.WATERLOGGED, false);
        return state;
    }

    public static BlockPos cache(LevelReader level, BlockPos cupboard) {
        BlockPos wet = water(level, cupboard) ? cupboard : cupboard.above();
        BlockPos top = surface(level, wet);
        return !top.equals(wet) ? top : cupboard;
    }

    /** Barrels have already risen before being filled, so journals and lazy loot tables cannot be lost or copied. */
    public static void driftwood(ServerLevelAccessor level, BlockPos barrel) {
        for (int[] offset : new int[][]{{-3, 2}, {2, 4}, {4, -2}}) {
            BlockPos p = barrel.offset(offset[0], 0, offset[1]);
            if (!level.hasChunkAt(p) || !level.getBlockState(p).isAir() || !water(level, p.below())) continue;
            level.setBlock(p, Blocks.SPRUCE_SLAB.defaultBlockState(), 2 | 16);
        }
    }
}
