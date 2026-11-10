package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The places people in Odessa would give directions by. All original designs. */
final class Landmarks {
    private Landmarks() {}
    private static final int Y = CityGrid.STREET_Y;

    // ------------------------------------------------------------------
    // City hall: a colonnade on the avenue and a clock tower stopped at 6:14
    // ------------------------------------------------------------------

    static void cityHall(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        int x0 = ox + 11, z0 = oz + 11, x1 = ox + 24, z1 = oz + 28;
        Building.Look look = new Building.Look(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.CHISELED_QUARTZ_BLOCK,
                Blocks.GRAY_STAINED_GLASS, Blocks.GLASS, Blocks.OCHRE_FROGLIGHT, Blocks.POLISHED_ANDESITE, Blocks.LIGHT_GRAY_CONCRETE,
                Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_STAIRS, true, 2, 2, 0.5, Building.Ground.PLAIN, Blocks.QUARTZ_BRICKS,
                Blocks.QUARTZ_BRICKS, Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, Blocks.DARK_OAK_DOOR);
        Building hall = new Building(x0, z0, x1, z1, 3, look, Building.tiers(3), Building.Top.FLAT, new boolean[] {true, true, true, true}, CityGrid.hash(bx, bz, 1));
        out.add(new Feature(x0 - 2, z0 - 2, x1 + 6, z1 + 2, c -> {
            hall.draw(c);
            portico(c, x1 + 1, (z0 + z1) / 2);
            clockTower(c, (x0 + x1) / 2 - 4, (z0 + z1) / 2 - 4, hall.roofY());
        }));
    }

    /** Six columns, a pediment and steps, facing east onto the avenue. */
    private static void portico(CityCanvas c, int x, int cz) {
        int z0 = cz - 5, z1 = cz + 5;
        for (int step = 0; step < 3; step++)
            for (int z = z0; z <= z1; z++) c.set(x + 3 - step, Y + step, z, CityCanvas.stairs(Blocks.QUARTZ_STAIRS, Direction.WEST, false));
        c.fill(x, Y, z0, x + 1, Y + 2, z1, Blocks.SMOOTH_QUARTZ);
        c.fill(x, Y + 3, z0, x + 1, Y + 3, z1, Blocks.POLISHED_ANDESITE);
        for (int z = z0; z <= z1; z += 2) c.fill(x + 1, Y + 4, z, x + 1, Y + 9, z, Blocks.QUARTZ_PILLAR);
        c.fill(x, Y + 10, z0 - 1, x + 2, Y + 10, z1 + 1, Blocks.QUARTZ_BRICKS);
        // The pediment: a low triangle of stairs over the columns.
        for (int r = 0; r <= 4; r++) {
            int y = Y + 11 + r;
            for (int z = z0 - 1 + r; z <= z1 + 1 - r; z++) {
                boolean edge = z == z0 - 1 + r || z == z1 + 1 - r;
                BlockState state = edge ? CityCanvas.stairs(Blocks.QUARTZ_STAIRS, z < cz ? Direction.SOUTH : Direction.NORTH, false) : Blocks.SMOOTH_QUARTZ.defaultBlockState();
                for (int x2 = x; x2 <= x + 2; x2++) c.set(x2, y, z, state);
            }
        }
        c.sign(x + 2, Y + 10, cz, Direction.EAST, Blocks.DARK_OAK_WALL_SIGN, DyeColor.YELLOW, true, "CITY OF ODESSA");
        c.door(x - 1, Y + 4, cz, Blocks.DARK_OAK_DOOR, Direction.EAST);
        c.door(x - 1, Y + 4, cz + 1, Blocks.DARK_OAK_DOOR, Direction.EAST);
    }

    /** Nine square, a clock on every face, a belfry and a green copper roof. */
    private static void clockTower(CityCanvas c, int x, int z, int y) {
        int top = y + 22;
        for (int h = y; h <= top; h++)
            for (int dx = 0; dx < 9; dx++)
                for (int dz = 0; dz < 9; dz++) {
                    boolean edge = dx == 0 || dx == 8 || dz == 0 || dz == 8;
                    boolean corner = (dx == 0 || dx == 8) && (dz == 0 || dz == 8);
                    if (!edge) { if (h == y) c.set(x + dx, h, z + dz, Blocks.POLISHED_ANDESITE); continue; }
                    boolean slit = !corner && (h - y) % 5 >= 2 && (h - y) % 5 <= 3 && (dx == 4 || dz == 4) && h < top - 9;
                    c.set(x + dx, h, z + dz, corner ? Blocks.QUARTZ_PILLAR : slit ? Blocks.GRAY_STAINED_GLASS : Blocks.SMOOTH_QUARTZ);
                }
        int face = top - 8;
        for (Direction side : Direction.Plane.HORIZONTAL) clockFace(c, x, z, face, side);
        // The belfry: open arches above the clocks, and a bell.
        int belfry = top + 1;
        for (int h = belfry; h <= belfry + 3; h++)
            for (int dx = 0; dx < 9; dx++)
                for (int dz = 0; dz < 9; dz++) {
                    boolean edge = dx == 0 || dx == 8 || dz == 0 || dz == 8;
                    if (!edge) continue;
                    boolean pillar = dx % 4 == 0 && dz % 4 == 0;
                    c.set(x + dx, h, z + dz, pillar || h == belfry + 3 ? Blocks.SMOOTH_QUARTZ : Blocks.AIR);
                }
        c.fill(x + 1, belfry + 3, z + 1, x + 7, belfry + 3, z + 7, Blocks.SMOOTH_QUARTZ);
        c.set(x + 4, belfry + 2, z + 4, Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
        // A pyramid roof in weathered copper, and a gilded finial.
        for (int r = 0; r <= 4; r++) {
            int h = belfry + 4 + r;
            for (int dx = r; dx <= 8 - r; dx++)
                for (int dz = r; dz <= 8 - r; dz++) {
                    boolean edge = dx == r || dx == 8 - r || dz == r || dz == 8 - r;
                    if (!edge) { c.set(x + dx, h, z + dz, Blocks.OXIDIZED_COPPER); continue; }
                    Direction up = dx == r ? Direction.EAST : dx == 8 - r ? Direction.WEST : dz == r ? Direction.SOUTH : Direction.NORTH;
                    c.set(x + dx, h, z + dz, CityCanvas.stairs(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, up, false));
                }
        }
        c.set(x + 4, belfry + 9, z + 4, Blocks.GOLD_BLOCK);
        c.fill(x + 4, belfry + 10, z + 4, x + 4, belfry + 12, z + 4, Blocks.LIGHTNING_ROD);
    }

    /**
     * A seven-square clock face, gilt frame, hands at 6:14: the minute hand nearly at three, the hour
     * hand just past six. {@code side} is the direction the face looks.
     */
    private static void clockFace(CityCanvas c, int x, int z, int y, Direction side) {
        for (int i = -1; i <= 7; i++)
            for (int j = -1; j <= 7; j++) {
                boolean frame = i == -1 || i == 7 || j == -1 || j == 7;
                boolean hand = i == 3 && j >= 3 && j <= 5 || j == 3 && i >= 3 && i <= 5;
                boolean mark = (i == 3 && j == 0) || (i == 6 && j == 3) || (i == 3 && j == 6) || (i == 0 && j == 3);
                Block block = frame ? Blocks.GOLD_BLOCK : hand || mark ? Blocks.BLACK_CONCRETE : Blocks.WHITE_CONCRETE;
                // i runs to the viewer's right, j downward, on the outside of the tower.
                int px, pz;
                switch (side) {
                    case NORTH -> { px = x + 7 - i; pz = z - 1; }
                    case SOUTH -> { px = x + 1 + i; pz = z + 9; }
                    case WEST -> { px = x - 1; pz = z + 1 + i; }
                    default -> { px = x + 9; pz = z + 7 - i; }
                }
                c.set(px, y + 7 - j - 1, pz, block);
            }
    }

    // ------------------------------------------------------------------
    // A church with a steeple
    // ------------------------------------------------------------------

    static void church(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        int x0 = ox + 17, x1 = ox + 27, z0 = oz + 16, z1 = oz + 28;
        out.add(new Feature(ox + 9, oz + 9, ox + 30, oz + 30, c -> {
            churchyard(c, ox, oz);
            c.fill(x0 + 1, Y + 1, z0 + 1, x1 - 1, Y + 16, z1 - 1, Blocks.AIR);
            // Nave: buttressed stone walls, tall coloured windows, a steep slate roof.
            int wall = Y + 9;
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    c.set(x, Y, z, Blocks.POLISHED_ANDESITE);
                    if (!edge) continue;
                    for (int y = Y + 1; y <= wall; y++) {
                        boolean side = (x == x0 || x == x1) && z > z0 && z < z1;
                        boolean window = side && (z - z0) % 3 == 0 && y >= Y + 3 && y <= Y + 7;
                        Block glass = (z / 3) % 3 == 0 ? Blocks.BLUE_STAINED_GLASS : (z / 3) % 3 == 1 ? Blocks.RED_STAINED_GLASS : Blocks.YELLOW_STAINED_GLASS;
                        c.set(x, y, z, window ? glass : y == Y + 1 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS);
                    }
                    if ((x == x0 || x == x1) && (z - z0) % 3 == 1 && z > z0 && z < z1) {
                        int bx2 = x == x0 ? x - 1 : x + 1;
                        c.fill(bx2, Y + 1, z, bx2, Y + 6, z, Blocks.STONE_BRICKS);
                        c.set(bx2, Y + 7, z, CityCanvas.stairs(Blocks.STONE_BRICK_STAIRS, x == x0 ? Direction.EAST : Direction.WEST, false));
                    }
                }
            int half = (x1 - x0) / 2;
            for (int r = 0; r <= half; r++) {
                int y = wall + 1 + r;
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    c.set(x0 + r - 1 + 1, y, z, CityCanvas.stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST, false));
                    c.set(x1 - r, y, z, CityCanvas.stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST, false));
                    if (r == half) c.set(x0 + half, y, z, Blocks.DEEPSLATE_TILES);
                }
                // Gable ends.
                for (int x = x0 + r + 1; x < x1 - r; x++) { c.set(x, y, z0, Blocks.STONE_BRICKS); c.set(x, y, z1, Blocks.STONE_BRICKS); }
            }
            // A rose window in the north gable and doors under it.
            int cx = (x0 + x1) / 2;
            for (int dx = -2; dx <= 2; dx++)
                for (int dy = -2; dy <= 2; dy++)
                    if (dx * dx + dy * dy <= 5) c.set(cx + dx, wall + dy - 1, z0, (dx + dy) % 2 == 0 ? Blocks.PURPLE_STAINED_GLASS : Blocks.ORANGE_STAINED_GLASS);
            c.door(cx, Y + 1, z0, Blocks.SPRUCE_DOOR, Direction.NORTH);
            c.door(cx + 1, Y + 1, z0, Blocks.SPRUCE_DOOR, Direction.NORTH);
            c.fill(cx, Y + 3, z0, cx + 1, Y + 3, z0, Blocks.CHISELED_STONE_BRICKS);
            steeple(c, ox + 11, oz + 12);
            // Candlelight inside.
            for (int z = z0 + 2; z < z1; z += 3) c.set(cx, wall - 1, z, CityCanvas.hangingLantern(Blocks.LANTERN));
        }));
    }

    private static void steeple(CityCanvas c, int x, int z) {
        int top = Y + 26;
        for (int y = Y; y <= top; y++)
            for (int dx = 0; dx < 5; dx++)
                for (int dz = 0; dz < 5; dz++) {
                    boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                    if (!edge) { if (y == Y) c.set(x + dx, y, z + dz, Blocks.POLISHED_ANDESITE); continue; }
                    boolean louver = y >= top - 4 && y <= top - 1 && (dx == 2 || dz == 2);
                    c.set(x + dx, y, z + dz, louver ? Blocks.SPRUCE_TRAPDOOR.defaultBlockState() : ((dx == 0 || dx == 4) && (dz == 0 || dz == 4) ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState());
                }
        c.set(x + 2, top - 2, z + 2, Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
        c.fill(x + 1, top, z + 1, x + 3, top, z + 3, Blocks.STONE_BRICKS);
        // The spire.
        for (int h = 1; h <= 12; h++) {
            int r = h <= 4 ? 2 : h <= 8 ? 1 : 0;
            for (int dx = 2 - r; dx <= 2 + r; dx++)
                for (int dz = 2 - r; dz <= 2 + r; dz++) c.set(x + dx, top + h, z + dz, Blocks.DEEPSLATE_TILES);
        }
        c.fill(x + 2, top + 13, z + 2, x + 2, top + 15, z + 2, Blocks.LIGHTNING_ROD);
    }

    private static void churchyard(CityCanvas c, int ox, int oz) {
        for (int x = ox + 11; x <= ox + 28; x++)
            for (int z = oz + 11; z <= oz + 28; z++) {
                c.set(x, Y, z, (x + z) % 7 == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK);
                int pick = CityGrid.hash(x, z, 41) % 23;
                if (pick == 0) c.set(x, Y + 1, z, Blocks.POPPY);
                else if (pick == 1) c.set(x, Y + 1, z, Blocks.SHORT_GRASS);
            }
        for (int x = ox + 11; x <= ox + 28; x++) {
            c.set(x, Y + 1, oz + 11, x % 2 == 0 ? Blocks.STONE_BRICK_WALL : Blocks.IRON_BARS);
        }
        bigTree(c, ox + 27, oz + 14, 0);
        bigTree(c, ox + 12, oz + 26, 1);
    }

    // ------------------------------------------------------------------
    // Parks and plazas
    // ------------------------------------------------------------------

    static void park(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        out.add(new Feature(ox + 6, oz + 6, ox + 33, oz + 33, c -> {
            int cx = ox + 19, cz = oz + 19;
            for (int x = ox + 11; x <= ox + 28; x++)
                for (int z = oz + 11; z <= oz + 28; z++) {
                    int dx = x - cx, dz = z - cz;
                    boolean path = Math.abs(dx) <= 1 || Math.abs(dz) <= 1 || Math.abs(dx * dx + dz * dz - 36) <= 6;
                    c.set(x, Y, z, path ? Blocks.GRAVEL : Blocks.GRASS_BLOCK);
                    boolean hedge = (x == ox + 11 || x == ox + 28 || z == oz + 11 || z == oz + 28) && !path;
                    if (hedge) c.set(x, Y + 1, z, CityCanvas.leaves(Blocks.AZALEA_LEAVES));
                    else if (!path) {
                        int pick = CityGrid.hash(x, z, 7) % 17;
                        if (pick == 0) c.set(x, Y + 1, z, Blocks.DANDELION);
                        else if (pick == 1) c.set(x, Y + 1, z, Blocks.CORNFLOWER);
                        else if (pick < 6) c.set(x, Y + 1, z, Blocks.SHORT_GRASS);
                    }
                }
            fountain(c, cx, cz);
            int[][] trees = {{ox + 14, oz + 14}, {ox + 25, oz + 14}, {ox + 14, oz + 25}, {ox + 25, oz + 25}, {ox + 19, oz + 13}, {ox + 13, oz + 20}};
            for (int i = 0; i < trees.length; i++) bigTree(c, trees[i][0], trees[i][1], i);
            for (int[] lamp : new int[][] {{cx - 2, cz - 5}, {cx + 2, cz + 5}, {cx - 5, cz + 2}, {cx + 5, cz - 2}}) {
                c.fill(lamp[0], Y + 1, lamp[1], lamp[0], Y + 3, lamp[1], Blocks.POLISHED_BLACKSTONE_WALL);
                c.set(lamp[0], Y + 4, lamp[1], Blocks.LANTERN);
            }
            for (int[] bench : new int[][] {{cx - 3, cz + 7}, {cx + 2, cz - 8}}) {
                c.set(bench[0], Y + 1, bench[1], CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
                c.set(bench[0] + 1, Y + 1, bench[1], CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
            }
        }));
    }

    private static void fountain(CityCanvas c, int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                c.set(cx + dx, Y, cz + dz, Blocks.SMOOTH_STONE);
                if (d == 3) c.set(cx + dx, Y + 1, cz + dz, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
                else c.set(cx + dx, Y + 1, cz + dz, Blocks.WATER);
            }
        c.fill(cx, Y + 1, cz, cx, Y + 2, cz, Blocks.POLISHED_ANDESITE);
        c.set(cx, Y + 3, cz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
    }

    /** A broad shade tree: a short trunk, two branches and a rounded crown. */
    static void bigTree(CityCanvas c, int x, int z, int salt) {
        boolean birch = CityGrid.hash(x, z, salt) % 3 == 0;
        Block log = birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG;
        BlockState leaf = CityCanvas.leaves(birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES);
        c.fill(x, Y + 1, z, x, Y + 5, z, log);
        c.set(x + 1, Y + 5, z, CityCanvas.log(log, Direction.Axis.X));
        c.set(x, Y + 6, z - 1, CityCanvas.log(log, Direction.Axis.Z));
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 4; dy <= 9; dy++) {
                    double d = dx * dx + dz * dz + (dy - 6.5) * (dy - 6.5) * 1.3;
                    if (d > 11.5 + CityGrid.hash(x + dx, z + dz, dy) % 3) continue;
                    if (dx == 0 && dz == 0 && dy <= 5) continue;
                    c.set(x + dx, Y + dy, z + dz, leaf);
                }
    }

    /** Kirby Plaza at the north end of the avenue: paving, planters and a steel sphere. */
    static void plaza(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        out.add(new Feature(ox + 8, oz + 8, ox + 31, oz + 31, c -> {
            for (int x = ox + 11; x <= ox + 28; x++)
                for (int z = oz + 11; z <= oz + 28; z++)
                    c.set(x, Y, z, (x + z) % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);
            int cx = ox + 19, cz = oz + 19;
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = 0; dy <= 6; dy++)
                    for (int dz = -3; dz <= 3; dz++)
                        if (Math.abs(dx * dx + (dy - 3) * (dy - 3) + dz * dz - 9) <= 2)
                            c.set(cx + dx, Y + 2 + dy, cz + dz, (dx + dy + dz) % 2 == 0 ? Blocks.IRON_BLOCK : Blocks.LIGHT_GRAY_CONCRETE);
            c.fill(cx, Y + 1, cz, cx, Y + 2, cz, Blocks.POLISHED_ANDESITE);
            for (int[] p : new int[][] {{ox + 13, oz + 13}, {ox + 26, oz + 13}, {ox + 13, oz + 26}, {ox + 26, oz + 26}}) {
                c.fill(p[0] - 1, Y + 1, p[1] - 1, p[0] + 1, Y + 1, p[1] + 1, Blocks.STONE_BRICKS);
                c.set(p[0], Y + 1, p[1], Blocks.GRASS_BLOCK);
                c.set(p[0], Y + 2, p[1], Blocks.FLOWERING_AZALEA);
            }
            c.sign(cx, Y + 2, cz - 1, Direction.NORTH, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, "KIRBY", "PLAZA");
        }));
    }

    // ------------------------------------------------------------------
    // A parking lot
    // ------------------------------------------------------------------

    static void parking(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        out.add(new Feature(ox + 9, oz + 9, ox + 30, oz + 30, c -> {
            for (int x = ox + 11; x <= ox + 28; x++)
                for (int z = oz + 11; z <= oz + 28; z++) {
                    boolean line = (z == oz + 16 || z == oz + 23) && x % 3 == 0;
                    c.set(x, Y - 1, z, line ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE);
                }
            for (int row = 0; row < 2; row++)
                for (int stall = 0; stall < 5; stall++) {
                    int seed = CityGrid.hash(bx, bz, row, stall, 3);
                    if (seed % 4 == 0) continue;
                    int x = ox + 12 + stall * 3, z = row == 0 ? oz + 12 : oz + 24;
                    carOnLot(c, x, z, seed);
                }
            for (int[] p : new int[][] {{ox + 19, oz + 19}}) {
                c.fill(p[0], Y + 1, p[1], p[0], Y + 6, p[1], Blocks.POLISHED_BLACKSTONE_WALL);
                c.set(p[0], Y + 7, p[1], Blocks.SEA_LANTERN);
            }
            // The attendant's booth by the entrance.
            int bx0 = ox + 26, bz0 = oz + 18;
            c.fill(bx0, Y + 1, bz0, bx0 + 1, Y + 2, bz0 + 1, Blocks.WHITE_CONCRETE);
            c.set(bx0, Y + 2, bz0, Blocks.GLASS);
            c.fill(bx0, Y + 3, bz0, bx0 + 1, Y + 3, bz0 + 1, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
        }));
    }

    private static void carOnLot(CityCanvas c, int x, int z, int seed) {
        Block[][] paint = {{Blocks.RED_CONCRETE, Blocks.RED_CARPET}, {Blocks.WHITE_CONCRETE, Blocks.WHITE_CARPET},
                {Blocks.BLUE_CONCRETE, Blocks.BLUE_CARPET}, {Blocks.GRAY_CONCRETE, Blocks.GRAY_CARPET}};
        Streets.car(c, x, z, Direction.SOUTH, Direction.EAST, paint[seed % paint.length], true);
    }

    // ------------------------------------------------------------------
    // A construction site with a tower crane
    // ------------------------------------------------------------------

    static void construction(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        int mx = ox + 15, mz = oz + 15, jib = 26;
        out.add(new Feature(ox + 8, oz + 8 - jib, ox + 31 + 4, oz + 31, c -> {
            for (int x = ox + 11; x <= ox + 28; x++)
                for (int z = oz + 11; z <= oz + 28; z++) {
                    c.set(x, Y, z, (x * 3 + z) % 5 == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT);
                    boolean fence = x == ox + 11 || x == ox + 28 || z == oz + 11 || z == oz + 28;
                    if (fence) {
                        boolean post = (x + z) % 4 == 0;
                        c.fill(x, Y + 1, z, x, Y + 2, z, post ? Blocks.SPRUCE_LOG : Blocks.STRIPPED_BIRCH_WOOD);
                    }
                }
            c.sign(ox + 19, Y + 2, oz + 10, Direction.NORTH, Blocks.OAK_WALL_SIGN, DyeColor.BLACK, false, "HARD HAT", "AREA");
            // The frame of what was going to be a ten-storey building.
            for (int floor = 0; floor < 6; floor++) {
                int y = Y + 1 + floor * 4;
                for (int x = ox + 18; x <= ox + 26; x += 4)
                    for (int z = oz + 18; z <= oz + 26; z += 4) c.fill(x, y, z, x, y + 3, z, Blocks.GRAY_CONCRETE);
                if (floor > 0 && floor < 5) {
                    for (int x = ox + 18; x <= ox + 26; x++) { c.set(x, y, oz + 18, Blocks.GRAY_CONCRETE); c.set(x, y, oz + 26, Blocks.GRAY_CONCRETE); }
                    for (int z = oz + 18; z <= oz + 26; z++) { c.set(ox + 18, y, z, Blocks.GRAY_CONCRETE); c.set(ox + 26, y, z, Blocks.GRAY_CONCRETE); }
                    for (int x = ox + 19; x <= ox + 25; x++) for (int z = oz + 19; z <= oz + 25; z++)
                        if (floor < 4) c.set(x, y, z, Blocks.SMOOTH_STONE_SLAB);
                }
                if (floor < 3) for (int z = oz + 18; z <= oz + 26; z++) c.set(ox + 17, y + 1, z, Blocks.SCAFFOLDING);
            }
            // The crane: a lattice mast, the cab, a long jib out over the street and a counterweight.
            int top = Y + 58;
            for (int y = Y + 1; y <= top; y++)
                for (int dx = 0; dx <= 1; dx++)
                    for (int dz = 0; dz <= 1; dz++)
                        c.set(mx + dx, y, mz + dz, (y + dx + dz) % 3 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.YELLOW_TERRACOTTA);
            c.fill(mx - 1, top + 1, mz - 1, mx + 2, top + 1, mz + 2, Blocks.YELLOW_CONCRETE);
            c.fill(mx + 2, top + 2, mz, mx + 3, top + 3, mz + 1, Blocks.GLASS);
            c.fill(mx + 2, top + 4, mz, mx + 3, top + 4, mz + 1, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
            c.fill(mx, top + 2, mz, mx + 1, top + 6, mz + 1, Blocks.YELLOW_TERRACOTTA);
            c.set(mx, top + 7, mz, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
            for (int i = 1; i <= jib; i++) {
                c.set(mx, top + 2, mz - i, i % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.YELLOW_TERRACOTTA);
                c.set(mx + 1, top + 2, mz - i, i % 2 == 1 ? Blocks.YELLOW_CONCRETE : Blocks.YELLOW_TERRACOTTA);
                if (i % 3 == 0) c.set(mx, top + 3, mz - i, Blocks.CHAIN);
            }
            for (int i = 1; i <= 8; i++) c.set(mx, top + 2, mz + 1 + i, Blocks.YELLOW_TERRACOTTA);
            c.fill(mx - 1, top + 1, mz + 7, mx + 2, top + 3, mz + 9, Blocks.GRAY_CONCRETE);
            int hook = mz - 10;
            c.fill(mx, Y + 30, hook, mx, top + 1, hook, Blocks.CHAIN);
            c.fill(mx - 1, Y + 29, hook - 1, mx + 1, Y + 29, hook + 1, Blocks.SPRUCE_SLAB);
            c.set(mx, Y + 30, hook, Blocks.IRON_BLOCK);
        }));
    }

    // ------------------------------------------------------------------
    // Primatech Paper, where the avenue ends
    // ------------------------------------------------------------------

    static void primatech(List<Feature> out) {
        int bz = CityGrid.TERMINUS_BZ;
        int sx0 = CityGrid.originX(3) + 8, sx1 = CityGrid.originX(4) + 31, sz0 = CityGrid.originZ(bz) + 8, sz1 = CityGrid.originZ(bz) + 31;
        int x0 = sx0 + 3, x1 = sx1 - 3, z0 = sz0 + 4, z1 = sz1 - 3;
        Building.Look look = new Building.Look(Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.WHITE_CONCRETE,
                Blocks.BLACK_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.SEA_LANTERN, Blocks.POLISHED_ANDESITE,
                Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_STONE_SLAB, null, false, 99, 2, 0.45, Building.Ground.LOBBY,
                Blocks.POLISHED_BLACKSTONE, Blocks.WHITE_CONCRETE, Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, Blocks.IRON_DOOR);
        Building hq = new Building(x0, z0, x1, z1, 9, look, Building.tiers(9, 7, 2), Building.Top.MAST,
                new boolean[] {true, false, false, false}, CityGrid.hash(77, 7));
        out.add(new Feature(sx0, sz0, sx1, sz1, c -> {
            for (int x = sx0; x <= sx1; x++)
                for (int z = sz0; z <= sz1; z++) {
                    c.set(x, Y - 1, z, Blocks.SMOOTH_STONE);
                    boolean edge = x - sx0 < 3 || sx1 - x < 3 || z - sz0 < 4 || sz1 - z < 3;
                    if (edge) c.set(x, Y, z, CityCanvas.slab((x + z) % 2 == 0 ? Blocks.POLISHED_ANDESITE_SLAB : Blocks.SMOOTH_STONE_SLAB, false));
                }
            hq.draw(c);
            int door = 4;
            c.sign(door, Y + 5, z0 - 1, Direction.NORTH, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, "PRIMATECH");
            c.sign(door - 1, Y + 5, z0 - 1, Direction.NORTH, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, "PAPER CO.");
            for (int x : new int[] {-2, 10}) {
                c.fill(x, Y, sz0 + 1, x, Y + 8, sz0 + 1, Blocks.IRON_BARS);
                c.set(x, Y + 9, sz0 + 1, (x < 0 ? Blocks.BLUE_BANNER : Blocks.WHITE_BANNER).defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BannerBlock.ROTATION, 8));
            }
        }));
    }
}
