package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/** Shared footprint, half-block stairs and placement for ice paths. */
final class CryoBridge {
    private CryoBridge() {}

    static BlockState state(FrostConstructBlock.Form form) {
        return ModBlocks.FROST_CONSTRUCT.get().defaultBlockState().setValue(FrostConstructBlock.FORM, form);
    }

    static BlockPos atHeight(BlockPos flat, int topHalf) {
        return flat.atY(Math.floorDiv(topHalf - 1, 2));
    }

    static FrostConstructBlock.Form form(int topHalf) {
        return Math.floorMod(topHalf, 2) == 0 ? FrostConstructBlock.Form.DECK : FrostConstructBlock.Form.LOW;
    }

    static Set<BlockPos> route(Vec3 from, Vec3 to) {
        Set<BlockPos> path = new LinkedHashSet<>();
        BlockPos previous = BlockPos.containing(from).atY(0);
        path.add(previous);
        int samples = Math.max(1, (int) Math.ceil(to.subtract(from).horizontalDistance() * 5));
        for (int i = 1; i <= samples; i++) {
            BlockPos next = BlockPos.containing(from.lerp(to, (double) i / samples)).atY(0);
            if (next.getX() != previous.getX() && next.getZ() != previous.getZ()) {
                // Join at a face when a diagonal crosses a grid corner.
                path.add(new BlockPos(next.getX(), 0, previous.getZ()));
            }
            path.add(next);
            previous = next;
        }
        return path;
    }

    static Direction side(Vec3 direction) {
        return Math.abs(direction.x) > Math.abs(direction.z) ? Direction.NORTH : Direction.EAST;
    }

    static boolean lay(ServerPlayer player, BlockPos pos, int topHalf, int ticks) {
        return lay(player, pos, topHalf, ticks, false);
    }

    static boolean lay(ServerPlayer player, BlockPos pos, int topHalf, int ticks, boolean reshapeOwned) {
        var level = player.serverLevel();
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return false;
        BlockState current = level.getBlockState(pos);
        var form = form(topHalf);
        if (FrostConstructBlock.isFrost(current)) {
            var currentForm = current.getValue(FrostConstructBlock.FORM);
            if (currentForm.top == form.top) return TemporaryBlocks.isTemporary(level, pos)
                    ? TemporaryBlocks.refresh(level, pos, ticks, player) : WorldMutationPolicy.canEdit(player, level, pos);
            return reshapeOwned && currentForm == FrostConstructBlock.Form.LOW && form == FrostConstructBlock.Form.DECK
                    && TemporaryBlocks.place(level, pos, state(FrostConstructBlock.Form.SURFACE), ticks, player);
        }
        if (current.is(BlockTags.ICE)) {
            if (form != FrostConstructBlock.Form.DECK) return false;
            return TemporaryBlocks.isTemporary(level, pos) ? TemporaryBlocks.refresh(level, pos, ticks, player)
                    : WorldMutationPolicy.canEdit(player, level, pos);
        }
        if (!current.is(Blocks.WATER) && current.isFaceSturdy(level, pos, Direction.UP)) {
            if (form != FrostConstructBlock.Form.DECK) return false;
            if (!level.getBlockState(pos.above()).isAir())
                return WorldMutationPolicy.canEdit(player, level, pos);
            return TemporaryBlocks.frostSurface(level, pos, ticks, player, state(FrostConstructBlock.Form.SURFACE));
        }
        if (current.is(Blocks.WATER) && form != FrostConstructBlock.Form.DECK) return false;
        return TemporaryBlocks.place(level, pos, state(current.is(Blocks.WATER) ? FrostConstructBlock.Form.SURFACE : form), ticks, player);
    }
}
