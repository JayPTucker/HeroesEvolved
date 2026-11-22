package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.time.intro.city.CityCanvas;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import com.jayptucker.heroesevolved.time.intro.city.Landmarks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What is left of Primatech Paper, a hundred and three years after the Last Day. It is the building Odessa draws
 * ({@link Landmarks#primatechPlan}), put down over the bunker exactly where it stood over the vault, then brought down: the
 * light came along the avenue and hit the front doors, so the middle of the block fell in on itself and left two broken
 * wings, the west one the taller, with the front sheared off lower than the back so the floors show like a cut-away. The
 * lobby's doors, canopy and name still stand between them, and the way from the stair out through those doors is kept clear.
 * <p>
 * The middle stays low on purpose: the world's spawn is at the origin and a respawn lands on the top of whatever is within
 * ten blocks of it, so nobody should wake on a thirty-block wall.
 */
final class PrimatechRuin {
    private PrimatechRuin() {}

    /** From Odessa's coordinates to the bunker's: the stair's slot in the lobby floor lands on the bunker's top flight. */
    private static final int DX = 3 - CityGrid.STAIR_X0, DZ = -7 - CityGrid.STAIR_Z0;
    static final int LOT_X0 = CityGrid.PRIMATECH_LOT_X0 + DX, LOT_X1 = CityGrid.PRIMATECH_LOT_X1 + DX,
            LOT_Z0 = CityGrid.PRIMATECH_LOT_Z0 + DZ, LOT_Z1 = CityGrid.PRIMATECH_LOT_Z1 + DZ;
    static final int WALL_X0 = CityGrid.PRIMATECH_X0 + DX, WALL_X1 = CityGrid.PRIMATECH_X1 + DX,
            WALL_Z0 = CityGrid.PRIMATECH_Z0 + DZ, WALL_Z1 = CityGrid.PRIMATECH_Z1 + DZ;
    /** The double doors, in the middle of the north wall. */
    static final int DOOR_X0 = WALL_X0 + 24, DOOR_X1 = WALL_X0 + 25;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** How high the ruin still stands along the building, west to east, before the front is sheared and the edges broken. */
    private static final double[][] SKYLINE = {{0, 30}, {4, 35}, {12, 35}, {14, 24}, {16, 12}, {18, 7}, {24, 6}, {30, 6},
            {35, 7}, {37, 13}, {39, 26}, {46, 30}, {49, 23}};
    /** The storeys: the ground floor's ceiling, then one every four blocks. Odessa's {@code Building} numbers. */
    private static final int GROUND = 6, STOREY = 4, SETBACK = GROUND + STOREY * 6;

    /** The stair's place in the floor, and the walk from its head out through the doors, which nothing may block. */
    private record Way(int slotX0, int slotX1, int laneZ0, int laneZ1, int headX) {
        boolean slot(int x, int z) { return x >= slotX0 && x <= slotX1 && z >= laneZ0 && z <= laneZ1; }
        boolean open(int x, int z) {
            if (x >= slotX0 - 1 && x <= slotX1 + 1 && z >= laneZ0 && z <= laneZ1 + 1) return true;
            // Along the inside of the front wall, from the doors to the head of the stair...
            if (z >= WALL_Z0 + 1 && z <= WALL_Z0 + 3 && x >= DOOR_X0 - 1 && x <= Math.max(headX, DOOR_X1) + 1) return true;
            // ...round to the head, wherever the top flight lies...
            if (x >= headX - 1 && x <= headX + 1 && z >= WALL_Z0 + 1 && z <= laneZ1) return true;
            // ...and out over the forecourt.
            return x >= DOOR_X0 - 1 && x <= DOOR_X1 + 1 && z < WALL_Z0;
        }
    }

    /** Puts the ruin on a cleared lot whose floor is {@code floor} above the bunker's origin. */
    static void raise(ServerLevel level, BlockPos origin, int floor, int laneZ0, int laneZ1, int slotX0, int slotX1, int headX) {
        Way way = new Way(slotX0, slotX1, laneZ0, laneZ1, headX);
        CityCanvas plan = Landmarks.primatechPlan(level);
        Map<BlockPos, BlockState> kept = collapse(plan, way);
        Writer out = new Writer(level, origin, floor);
        kept.forEach((cell, state) -> out.set(cell.getX(), cell.getY(), cell.getZ(), state));
        plan.plannedSigns().forEach((pos, text) -> {
            BlockPos cell = new BlockPos(pos.getX() + DX, pos.getY() - CityGrid.STREET_Y, pos.getZ() + DZ);
            if (!kept.containsKey(cell)) return;
            if (level.getBlockEntity(out.at(cell.getX(), cell.getY(), cell.getZ())) instanceof SignBlockEntity sign) {
                sign.setText(text, true);
                sign.setWaxed(true);
                sign.setChanged();
            }
        });
        out.rubble(way);
        out.fallenMast(way);
        out.fallenPenthouse(way);
        out.growth(way);
        out.settle();
    }

    // ------------------------------------------------------------------
    // Bringing it down
    // ------------------------------------------------------------------

    /** The building's cells that survive, in bunker x and z and height above the floor, aged. */
    private static Map<BlockPos, BlockState> collapse(CityCanvas plan, Way way) {
        Map<BlockPos, BlockState> standing = new HashMap<>();
        plan.planned().forEach((pos, state) -> {
            int x = pos.getX() + DX, y = pos.getY() - CityGrid.STREET_Y, z = pos.getZ() + DZ;
            if (y < 0 || state.isAir() || way.slot(x, z)) return;
            // The Last Day's own stair head is redrawn for the stair that is really here.
            if (pos.getX() >= CityGrid.STAIR_X0 - 2 && pos.getX() <= CityGrid.STAIR_X1 + 1
                    && pos.getZ() >= CityGrid.STAIR_Z0 && pos.getZ() <= CityGrid.STAIR_Z1 + 1) {
                if (y == 0) standing.put(new BlockPos(x, 0, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
                return;
            }
            if (!survives(x, y, z, way)) return;
            standing.put(new BlockPos(x, y, z), state);
        });
        columns(standing, way);
        breakGlass(standing);
        holdUp(standing);
        Map<BlockPos, BlockState> aged = new HashMap<>();
        standing.forEach((cell, state) -> aged.put(cell, age(cell, state, standing)));
        return aged;
    }

    private static boolean survives(int x, int y, int z, Way way) {
        boolean building = x >= WALL_X0 && x <= WALL_X1 && z >= WALL_Z0 && z <= WALL_Z1;
        if (!building) {
            if (y == 0) return true;
            // The name over the doors, and the two ends of the canopy: its middle fell, or it would hide the name from the street.
            if (z >= WALL_Z0 - 2 && z < WALL_Z0 && x >= DOOR_X0 - 2 && x <= DOOR_X1 + 2 && y <= 5)
                return y == 5 || x < DOOR_X0 || x > DOOR_X1;
            // The two flagpoles out front, snapped: the one by the doors' west side still taller.
            int west = CityGrid.PRIMATECH_LOT_X0 + 22 + DX, east = CityGrid.PRIMATECH_LOT_X0 + 34 + DX;
            return z == LOT_Z0 + 1 && (x == west && y <= 5 || x == east && y <= 2);
        }
        if (way.open(x, z) && z != WALL_Z0) return y == 0;
        int u = x - WALL_X0, v = z - WALL_Z0;
        if (y > standing(u, v)) return false;
        // The outer walls and the floors are what is left. The partitions inside went, or the wings read as solid lumps.
        int depth = Math.min(Math.min(u, WALL_X1 - WALL_X0 - u), Math.min(v, WALL_Z1 - WALL_Z0 - v));
        return floorLine(y) || depth < (y >= SETBACK ? 4 : 2);
    }

    private static boolean floorLine(int y) { return y == 0 || y >= GROUND && (y - GROUND) % STOREY == 0; }

    /** The columns that carried the floors, still standing in the wings and as stumps in the heap. */
    private static void columns(Map<BlockPos, BlockState> cells, Way way) {
        for (int u = 4; u < WALL_X1 - WALL_X0; u += 8)
            for (int v : new int[] {6, 10}) {
                int x = WALL_X0 + u, z = WALL_Z0 + v;
                if (way.open(x, z)) continue;
                for (int y = 1; y <= standing(u, v); y++) cells.putIfAbsent(new BlockPos(x, y, z), Blocks.WHITE_CONCRETE.defaultBlockState());
            }
    }

    /** How many blocks above the floor the building still stands at this cell of its footprint. */
    static int standing(int u, int v) {
        double h = skyline(u);
        // The front took the blow: it stands a little lower than the back.
        double back = Math.min(1, v / 5.0);
        h *= 0.84 + 0.16 * back * back * (3 - 2 * back);
        // Whole bays lost a storey or kept one, so the top is a broken building's and not a slope...
        if (h > 12) h += (hash((u + 1) / 4, v / 4, 37) % 3 - 1) * STOREY;
        // ...and every break is ragged, in clumps of a couple of blocks.
        h += hash(u / 2, v / 2, 31) % 4 - 1.5;
        // The doors, their canopy and the name over them are still there, and the wall along the stair still guards it.
        if (v == 0 && u >= 21 && u <= 28) h = Math.max(h, 5);
        if (v == 0 && u > 28 && u <= 40) h = Math.max(h, 2);
        return Math.max(1, (int) h);
    }

    private static double skyline(int u) {
        for (int i = 1; i < SKYLINE.length; i++)
            if (u <= SKYLINE[i][0]) {
                double t = (u - SKYLINE[i - 1][0]) / (SKYLINE[i][0] - SKYLINE[i - 1][0]);
                return SKYLINE[i - 1][1] + (SKYLINE[i][1] - SKYLINE[i - 1][1]) * t;
            }
        return SKYLINE[SKYLINE.length - 1][1];
    }

    /** Most of the glass went; what is left is held in its frame at both ends. Over the doors it stayed, under the canopy. */
    private static void breakGlass(Map<BlockPos, BlockState> cells) {
        cells.entrySet().removeIf(entry -> {
            BlockState state = entry.getValue();
            BlockPos cell = entry.getKey();
            if (!glass(state)) return false;
            int n = hash(cell.getX(), cell.getY(), cell.getZ(), 41) % 100;
            if (state.is(Blocks.GLASS)) {
                // The lobby's tall panes: the row that holds the canopy up, and shards hanging from the head of each frame.
                boolean canopy = cell.getY() == 4 && cell.getX() >= DOOR_X0 - 1 && cell.getX() <= DOOR_X1 + 1;
                return !canopy && n >= (cell.getY() == 4 ? 45 : cell.getY() == 3 ? 16 : 0);
            }
            return n >= 28;
        });
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BlockPos cell : java.util.List.copyOf(cells.keySet())) {
                BlockState state = cells.get(cell);
                if (!(state.getBlock() instanceof IronBarsBlock) || state.is(Blocks.IRON_BARS)) continue;
                boolean eastWest = holds(cells, cell.east()) && holds(cells, cell.west());
                boolean northSouth = holds(cells, cell.north()) && holds(cells, cell.south());
                if (!eastWest && !northSouth) { cells.remove(cell); changed = true; }
            }
        }
    }

    private static boolean holds(Map<BlockPos, BlockState> cells, BlockPos cell) {
        BlockState state = cells.get(cell);
        return state != null && (state.getBlock() instanceof IronBarsBlock || state.canOcclude());
    }

    private static boolean glass(BlockState state) {
        return state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.BLACK_STAINED_GLASS_PANE);
    }

    /** Anything no longer joined to the ground, face to face, came down with the rest. */
    private static void holdUp(Map<BlockPos, BlockState> cells) {
        Set<BlockPos> held = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos cell : cells.keySet()) if (cell.getY() == 0) { held.add(cell); queue.add(cell); }
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = cell.relative(side);
                if (cells.containsKey(next) && held.add(next)) queue.add(next);
            }
        }
        cells.keySet().retainAll(held);
    }

    /** A hundred years of weather, worst on what faces the sky. */
    private static BlockState age(BlockPos cell, BlockState state, Map<BlockPos, BlockState> cells) {
        int n = hash(cell.getX(), cell.getY(), cell.getZ(), 43) % 100;
        boolean open = !cells.containsKey(cell.above());
        if (cell.getZ() == WALL_Z0 && cell.getY() == 5 && cell.getX() >= DOOR_X0 - 2 && cell.getX() <= DOOR_X1 + 2) return state;
        if (state.getBlock() instanceof DoorBlock) return Blocks.WAXED_OXIDIZED_COPPER_DOOR.withPropertiesOf(state);
        if (state.getLightEmission() > 0) return Blocks.SMOOTH_STONE.defaultBlockState();
        if (state.is(Blocks.WHITE_CONCRETE)) {
            if (open && n < 18) return n < 7 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            return n < 14 ? Blocks.CALCITE.defaultBlockState() : n < 22 ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : state;
        }
        if (state.is(Blocks.LIGHT_GRAY_CONCRETE)) {
            if (open && n < 18) return n < 7 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            return n < 12 ? Blocks.ANDESITE.defaultBlockState() : n < 17 ? Blocks.GRAY_CONCRETE.defaultBlockState() : state;
        }
        if (state.is(Blocks.POLISHED_ANDESITE) && open) {
            if (n < 8) return Blocks.MOSS_BLOCK.defaultBlockState();
            if (n < 11) return Blocks.COARSE_DIRT.defaultBlockState();
            if (n < 24) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (state.is(Blocks.POLISHED_BLACKSTONE) && n < 22) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        return state;
    }

    private static int hash(int... values) { return CityGrid.hash(values); }

    // ------------------------------------------------------------------
    // Writing it, and what fell and grew
    // ------------------------------------------------------------------

    private static final class Writer {
        final ServerLevel level;
        final BlockPos origin;
        final int floor;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Writer(ServerLevel level, BlockPos origin, int floor) { this.level = level; this.origin = origin; this.floor = floor; }

        /** The world position of a ruin cell: x and z from the origin, y above the lobby floor. */
        BlockPos at(int x, int y, int z) { return cursor.set(origin.getX() + x, origin.getY() + floor + y, origin.getZ() + z); }
        void set(int x, int y, int z, BlockState state) {
            BlockPos pos = at(x, y, z);
            if (level.isOutsideBuildHeight(pos) || level.getBlockState(pos).is(Blocks.BEDROCK)) return;
            level.setBlock(pos, state, FLAGS);
        }
        void set(int x, int y, int z, Block block) { set(x, y, z, block.defaultBlockState()); }
        BlockState get(int x, int y, int z) { return level.getBlockState(at(x, y, z)); }
        boolean sturdy(int x, int y, int z, Direction face) {
            BlockPos pos = at(x, y, z).immutable();
            return level.getBlockState(pos).isFaceSturdy(level, pos, face);
        }

        /** The height above the floor of the first air over whatever is standing in this column. */
        int top(int x, int z) {
            int y = 48;
            while (y > 0 && get(x, y - 1, z).isAir()) y--;
            return y;
        }

        /** How deep the fallen floors lie here: one great heap in the gap, with a shoulder at the foot of each wing. */
        double heap(int x, int z) {
            double u = x - WALL_X0, v = z - WALL_Z0;
            return 8.5 * bump(u - 27, 11, v - 11, 5) + 5 * bump(u - 14, 4, v - 12, 4) + 5 * bump(u - 39, 4, v - 11, 4)
                    + 3 * bump(u - 27, 14, v - 19, 3);
        }

        private static double bump(double dx, double sx, double dz, double sz) {
            return Math.exp(-(dx * dx) / (2 * sx * sx) - (dz * dz) / (2 * sz * sz));
        }

        void rubble(Way way) {
            for (int x = LOT_X0; x <= LOT_X1; x++)
                for (int z = LOT_Z0; z <= LOT_Z1; z++) {
                    if (way.open(x, z)) continue;
                    double depth = heap(x, z) + (hash(x, z, 51) % 100) / 100.0 - 0.5;
                    if (depth < 0.45) continue;
                    int whole = (int) depth;
                    for (int y = 1; y <= whole; y++)
                        if (get(x, y, z).isAir()) set(x, y, z, debris(x, y, z, y == whole));
                    // A half block on top where the heap runs out, so its edge slopes instead of stepping.
                    if (depth - whole >= 0.45 && get(x, whole + 1, z).isAir() && sturdy(x, whole, z, Direction.UP))
                        set(x, whole + 1, z, slab(x, z));
                }
        }

        BlockState debris(int x, int y, int z, boolean top) {
            int n = hash(x, y, z, 53) % 100;
            if (top && n < 16) return Blocks.MOSS_BLOCK.defaultBlockState();
            if (top && n < 20) return Blocks.COARSE_DIRT.defaultBlockState();
            return (n < 44 ? Blocks.WHITE_CONCRETE : n < 62 ? Blocks.LIGHT_GRAY_CONCRETE : n < 74 ? Blocks.COBBLESTONE
                    : n < 84 ? Blocks.ANDESITE : n < 93 ? Blocks.CRACKED_STONE_BRICKS : Blocks.POLISHED_ANDESITE).defaultBlockState();
        }

        BlockState slab(int x, int z) {
            int n = hash(x, z, 55) % 4;
            Block block = n == 0 ? Blocks.COBBLESTONE_SLAB : n == 1 ? Blocks.ANDESITE_SLAB : n == 2 ? Blocks.SMOOTH_STONE_SLAB : Blocks.STONE_BRICK_SLAB;
            return block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }

        /** The roof's radio mast, lying where it came down across the heap, its tip still on it. */
        void fallenMast(Way way) {
            int z = WALL_Z0 + 11;
            int from = WALL_X0 + 19, to = WALL_X0 + 35;
            for (int x = from; x <= to; x++) {
                if (way.open(x, z)) continue;
                int y = top(x, z);
                if (!sturdy(x, y - 1, z, Direction.UP)) continue;
                set(x, y, z, x == to ? Blocks.LIGHTNING_ROD.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.EAST) : Blocks.IRON_BARS.defaultBlockState());
            }
        }

        /** The grey machine room off the roof, come down in one piece and sunk at an angle into the heap. */
        void fallenPenthouse(Way way) {
            int x0 = WALL_X0 + 21, z0 = WALL_Z0 + 6;
            int base = Math.max(1, top(x0 + 2, z0 + 2) - 2);
            for (int dx = 0; dx < 5; dx++) {
                int lean = dx / 2;      // it came to rest tipped, its east end lower
                for (int dz = 0; dz < 4; dz++) {
                    if (way.open(x0 + dx, z0 + dz)) continue;
                    int bottom = base - lean;
                    for (int y = 1; y < bottom; y++) if (get(x0 + dx, y, z0 + dz).isAir()) set(x0 + dx, y, z0 + dz, debris(x0 + dx, y, z0 + dz, false));
                    for (int h = 0; h < 4; h++)
                        set(x0 + dx, bottom + h, z0 + dz, h == 2 && dz == 0 && dx % 2 == 1
                                ? CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, false, true)
                                : Blocks.GRAY_CONCRETE.defaultBlockState());
                    set(x0 + dx, bottom + 4, z0 + dz, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
                }
            }
        }

        void growth(Way way) {
            forecourt(way);
            plants(way);
            trees(way);
            vines();
            lichen();
        }

        /** The land taking the paving back from its outer edge in, so the lot doesn't end in a ruled line. */
        void forecourt(Way way) {
            for (int x = LOT_X0; x <= LOT_X1; x++)
                for (int z = LOT_Z0; z <= LOT_Z1; z++) {
                    if (way.open(x, z) || x >= WALL_X0 && x <= WALL_X1 && z >= WALL_Z0 && z <= WALL_Z1) continue;
                    if (!get(x, 1, z).isAir()) continue;
                    int edge = Math.min(Math.min(x - LOT_X0, LOT_X1 - x), Math.min(z - LOT_Z0, LOT_Z1 - z));
                    int chance = edge == 0 ? 58 : edge == 1 ? 32 : 12;
                    if (hash(x, z, 57) % 100 >= chance) continue;
                    set(x, 0, z, Blocks.GRASS_BLOCK);
                    if (hash(x, z, 59) % 3 == 0) set(x, 1, z, Blocks.SHORT_GRASS);
                }
        }

        /** Moss and grass on whatever lies open to the sky, never on the way out. */
        void plants(Way way) {
            for (int x = LOT_X0; x <= LOT_X1; x++)
                for (int z = LOT_Z0; z <= LOT_Z1; z++) {
                    if (way.open(x, z)) continue;
                    int y = top(x, z);
                    BlockState under = get(x, y - 1, z);
                    int n = hash(x, z, 61) % 100;
                    if (under.is(Blocks.MOSS_BLOCK)) {
                        if (n < 24) set(x, y, z, Blocks.SHORT_GRASS);
                        else if (n < 52) set(x, y, z, Blocks.MOSS_CARPET);
                        else if (n < 57) set(x, y, z, Blocks.FERN);
                        else if (n < 60) set(x, y, z, Blocks.AZALEA);
                    }
                }
        }

        /** Trees that took root in the heap and on the floors the roof no longer covers. */
        void trees(Way way) {
            int[][] at = {{WALL_X0 + 17, WALL_Z0 + 13}, {WALL_X0 + 33, WALL_Z0 + 14}, {WALL_X0 + 27, WALL_Z0 + 15}};
            for (int i = 0; i < at.length; i++) {
                int x = at[i][0], z = at[i][1];
                if (way.open(x, z)) continue;
                int y = top(x, z);
                while (y > 1 && !get(x, y - 1, z).canOcclude()) y--;
                boolean birch = i == 1;
                set(x, y - 1, z, Blocks.ROOTED_DIRT);
                int height = 5 + i % 2;
                for (int h = 0; h < height; h++) set(x, y + h, z, birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG);
                BlockState leaf = (birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        for (int dy = height - 2; dy <= height + 1; dy++) {
                            if (dx * dx + dz * dz + (dy - height + .5) * (dy - height + .5) > 6.2) continue;
                            if (way.open(x + dx, z + dz) || !get(x + dx, y + dy, z + dz).isAir()) continue;
                            set(x + dx, y + dy, z + dz, leaf);
                        }
            }
        }

        /** Vines down the outside of the wings, each starting from a wall that can hold it. */
        void vines() {
            for (int x = WALL_X0; x <= WALL_X1; x++) {
                vine(x, WALL_Z0 - 1, Direction.SOUTH);
                vine(x, WALL_Z1 + 1, Direction.NORTH);
            }
            for (int z = WALL_Z0; z <= WALL_Z1; z++) {
                vine(WALL_X0 - 1, z, Direction.EAST);
                vine(WALL_X1 + 1, z, Direction.WEST);
            }
        }

        private void vine(int x, int z, Direction toWall) {
            if (hash(x, z, 71) % 3 != 0) return;
            int wx = x + toWall.getStepX(), wz = z + toWall.getStepZ();
            int y = top(wx, wz) - 1;
            if (y < 4 || !sturdy(wx, y, wz, toWall.getOpposite()) || !get(x, y, z).isAir()) return;
            int length = 3 + hash(x, z, 73) % Math.max(1, Math.min(9, y - 2));
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(toWall), true);
            for (int h = y; h > y - length && h > 1 && get(x, h, z).isAir(); h--) set(x, h, z, vine);
        }

        /** Glow lichen on the ceilings still up: enough light that the rooms under them don't fill with monsters. */
        void lichen() {
            BlockState glow = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);
            for (int x = WALL_X0 + 1; x < WALL_X1; x++)
                for (int z = WALL_Z0 + 1; z < WALL_Z1; z++) {
                    if (Math.floorMod(x, 3) != 0 || Math.floorMod(z, 3) != 0) continue;
                    for (int y = 2; y < 46; y++)
                        if (get(x, y, z).isAir() && sturdy(x, y + 1, z, Direction.DOWN)) set(x, y, z, glow);
                }
        }

        /** Joins panes, bars, fences, walls and stairs to what now stands round them. */
        void settle() {
            for (int x = LOT_X0 - 1; x <= LOT_X1 + 1; x++)
                for (int z = LOT_Z0 - 1; z <= LOT_Z1 + 1; z++)
                    for (int y = 0; y < 50; y++) {
                        BlockPos pos = at(x, y, z).immutable();
                        BlockState state = level.getBlockState(pos);
                        Block block = state.getBlock();
                        if (!(block instanceof IronBarsBlock || block instanceof FenceBlock || block instanceof WallBlock || block instanceof StairBlock)) continue;
                        BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
                        if (joined != state) level.setBlock(pos, joined, FLAGS);
                    }
        }
    }
}
