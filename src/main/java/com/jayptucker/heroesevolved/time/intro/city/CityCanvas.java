package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the city is drawn: every write is clipped to one rectangle of columns, so the same plan can
 * build a chunk at a time or put back any patch of street exactly as it was. Panes, bars, fences,
 * walls and stairs are joined to their neighbours once a patch is done; signs get their words then.
 */
public final class CityCanvas {
    /** No neighbour updates while drawing: nothing falls, flows or breaks halfway through a building. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    final ServerLevel level;
    final int x0, z0, x1, z1;
    private final List<BlockPos> shaped = new ArrayList<>();
    private final Map<BlockPos, SignText> signs = new LinkedHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public CityCanvas(ServerLevel level, int x0, int z0, int x1, int z1) {
        this.level = level;
        this.x0 = Math.min(x0, x1); this.z0 = Math.min(z0, z1);
        this.x1 = Math.max(x0, x1); this.z1 = Math.max(z0, z1);
    }

    public boolean in(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    /** Whether any of the rectangle lies on this canvas. */
    public boolean touches(int ax, int az, int bx, int bz) {
        return Math.max(ax, bx) >= x0 && Math.min(ax, bx) <= x1 && Math.max(az, bz) >= z0 && Math.min(az, bz) <= z1;
    }

    public void set(int x, int y, int z, BlockState state) {
        if (!in(x, z) || level.isOutsideBuildHeight(y)) return;
        cursor.set(x, y, z);
        level.setBlock(cursor, state, FLAGS);
        Block block = state.getBlock();
        if (block instanceof IronBarsBlock || block instanceof FenceBlock || block instanceof WallBlock || block instanceof StairBlock)
            shaped.add(cursor.immutable());
    }
    public void set(int x, int y, int z, Block block) { set(x, y, z, block.defaultBlockState()); }

    public void fill(int ax, int ay, int az, int bx, int by, int bz, BlockState state) {
        for (int x = Math.max(Math.min(ax, bx), x0); x <= Math.min(Math.max(ax, bx), x1); x++)
            for (int z = Math.max(Math.min(az, bz), z0); z <= Math.min(Math.max(az, bz), z1); z++)
                for (int y = Math.min(ay, by); y <= Math.max(ay, by); y++) set(x, y, z, state);
    }
    public void fill(int ax, int ay, int az, int bx, int by, int bz, Block block) { fill(ax, ay, az, bx, by, bz, block.defaultBlockState()); }

    /** Empties a column from {@code from} up to whatever stands there now. */
    public void clearColumn(int x, int z, int from) {
        if (!in(x, z)) return;
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = from; y < top; y++) {
            cursor.set(x, y, z);
            if (!level.getBlockState(cursor).isAir()) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** A wall sign on the face of the block behind it, facing out, with up to four lines. */
    public void sign(int x, int y, int z, Direction facing, Block wallSign, DyeColor color, boolean glow, String... lines) {
        if (!in(x, z)) return;
        set(x, y, z, wallSign.defaultBlockState().setValue(WallSignBlock.FACING, facing));
        SignText text = new SignText().setColor(color).setHasGlowingText(glow);
        int first = lines.length >= 3 ? 0 : 1;
        for (int i = 0; i < lines.length && first + i < 4; i++) text = text.setMessage(first + i, Component.literal(lines[i]));
        signs.put(new BlockPos(x, y, z), text);
    }

    /** Joins panes, bars, fences, walls and stairs to what now stands around them, and writes the signs. */
    public void finish() {
        BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
        for (BlockPos pos : shaped) {
            reshape(pos);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                near.setWithOffset(pos, side);
                // A neighbour across the edge of this patch was drawn earlier: join it back to us.
                if (!in(near.getX(), near.getZ())) reshape(near.immutable());
            }
        }
        signs.forEach((pos, text) -> {
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                sign.setText(text, true);
                sign.setWaxed(true);
                sign.setChanged();
            }
        });
        shaped.clear();
        signs.clear();
    }

    private void reshape(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof IronBarsBlock || block instanceof FenceBlock || block instanceof WallBlock || block instanceof StairBlock)) return;
        BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
        if (joined != state) level.setBlock(pos, joined, FLAGS);
    }

    // ------------------------------------------------------------------
    // Block states
    // ------------------------------------------------------------------

    public static BlockState stairs(Block block, Direction facing, boolean upsideDown) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
    }
    public static BlockState slab(Block block, boolean top) {
        return block.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }
    public static BlockState trapdoor(Block block, Direction facing, boolean top, boolean open) {
        return block.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, top ? Half.TOP : Half.BOTTOM)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, open);
    }
    public static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    }
    public static BlockState log(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axis);
    }
    public static BlockState hangingLantern(Block lantern) {
        return lantern.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
    }

    /** A closed door, both halves, opening outward along {@code facing}. */
    public void door(int x, int y, int z, Block door, Direction facing) {
        BlockState lower = door.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        set(x, y, z, lower);
        set(x, y + 1, z, lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }
}
