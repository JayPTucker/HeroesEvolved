package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.List;

/**
 * Odessa's edge, so the city never ends at a wall of fog: a ring of suburbs round it (ranch houses with yards and a car
 * in the drive), a strip of businesses on the roads in (a gas station, a motel, a diner, a strip mall), then oil
 * country — pumpjacks, tank batteries, a water tower, a rail line with a grain elevator — thinning into scrub prairie
 * along highways that run on to the horizon, where the world's own grassland takes over.
 * <p>
 * The outskirts continue the city's 32-block grid. A block's ring is how many blocks it lies outside the city: ring 1 is
 * suburb, ring 2 mixes houses and oil leases, ring 3 is prairie. Every street round a ring-1 block exists; further out
 * only the arterials (every fourth line) continue.
 */
final class Outskirts {
    private Outskirts() {}

    private static final int Y = CityGrid.STREET_Y;
    /** The rail line runs east and west across the southern prairie. */
    static final int RAIL_Z = CityGrid.MAX + 72;

    static boolean inRing(int x, int z) {
        return x >= CityGrid.OUTER_MIN && x <= CityGrid.OUTER_MAX && z >= CityGrid.OUTER_MIN && z <= CityGrid.OUTER_MAX
                && !CityGrid.inCity(x, z);
    }

    /** How many blocks outside the city a block lies (0 inside it). */
    static int ring(int bx, int bz) {
        int last = CityGrid.BLOCKS - 1;
        return Math.max(Math.max(-bx, bx - last), Math.max(Math.max(-bz, bz - last), 0));
    }

    /** A road in the outskirts: every street next to the suburbs, and the arterials beyond. */
    static boolean road(int x, int z) {
        if (!inRing(x, z)) return false;
        int bx = CityGrid.bx(x), bz = CityGrid.bz(z), lx = CityGrid.lx(x), lz = CityGrid.lz(z);
        if (lx < CityGrid.ROAD && (Math.floorMod(bx, 4) == 0 || Math.min(ring(bx, bz), ring(bx - 1, bz)) <= 1)) return true;
        return lz < CityGrid.ROAD && (Math.floorMod(bz, 4) == 0 || Math.min(ring(bx, bz), ring(bx, bz - 1)) <= 1);
    }

    private static boolean arterial(int x, int z) {
        int lx = CityGrid.lx(x), lz = CityGrid.lz(z);
        return lx < CityGrid.ROAD && Math.floorMod(CityGrid.bx(x), 4) == 0 || lz < CityGrid.ROAD && Math.floorMod(CityGrid.bz(z), 4) == 0;
    }

    private static boolean rail(int z) { return Math.abs(z - RAIL_Z) <= 1; }

    // ------------------------------------------------------------------
    // Ground
    // ------------------------------------------------------------------

    static void ground(CityCanvas c, int x, int z) {
        if (!inRing(x, z)) return;
        int y = Y - 1;
        if (rail(z)) {
            c.set(x, y, z, Blocks.GRAVEL);
            if (z == RAIL_Z) c.set(x, Y, z, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.EAST_WEST));
            return;
        }
        if (road(x, z)) { c.set(x, y, z, asphalt(x, z)); return; }
        int bx = CityGrid.bx(x), bz = CityGrid.bz(z);
        int ring = ring(bx, bz);
        double noise = CityGrid.chance(x >> 2, z >> 2, 17) * 0.6 + CityGrid.chance(x, z, 18) * 0.4;
        if (ring >= 2 && noise > 0.78) c.set(x, y, z, Blocks.COARSE_DIRT);
        else if (ring >= 2 && noise < 0.06) c.set(x, y, z, Blocks.PACKED_MUD);
        else c.set(x, y, z, Blocks.GRASS_BLOCK);
        // Scrub: dry grass, dead bushes, a mesquite here and there.
        double pick = CityGrid.chance(x, z, 19);
        if (ring == 1) { if (pick < 0.05) c.set(x, Y, z, Blocks.SHORT_GRASS); return; }
        if (ring >= 2 && noise < 0.06) return;  // bare packed earth: nothing grows there
        if (pick < 0.16) c.set(x, Y, z, Blocks.SHORT_GRASS);
        else if (pick < 0.19) c.set(x, Y, z, Blocks.DEAD_BUSH);
        else if (pick < 0.2 && noise <= 0.78) c.set(x, Y, z, Blocks.FERN);
    }

    private static Block asphalt(int x, int z) {
        int lx = CityGrid.lx(x), lz = CityGrid.lz(z);
        boolean ns = lx < CityGrid.ROAD, ew = lz < CityGrid.ROAD;
        if (ns && ew) return Blocks.GRAY_CONCRETE;
        int across = ns ? lx : lz, along = ns ? lz : lx;
        if (!arterial(x, z)) return across == 0 || across == 7 ? Blocks.GRAVEL : Blocks.GRAY_CONCRETE;
        // A highway: gravel shoulders (the power poles stand on the west or north one), white edge lines, a dashed centre.
        if (across == 0 || across == 7) return Blocks.GRAVEL;
        if (across == 1 || across == 6) return Blocks.WHITE_CONCRETE;
        if ((across == 3 || across == 4) && along % 6 < 3) return Blocks.YELLOW_CONCRETE;
        return Blocks.GRAY_CONCRETE;
    }

    // ------------------------------------------------------------------
    // What stands where
    // ------------------------------------------------------------------

    static void features(List<Feature> out) {
        int first = CityGrid.bx(CityGrid.OUTER_MIN), last = CityGrid.bx(CityGrid.OUTER_MAX);
        for (int bx = first; bx <= last; bx++)
            for (int bz = first; bz <= last; bz++) {
                int ring = ring(bx, bz);
                if (ring == 0) continue;
                block(out, bx, bz, ring);
                powerLines(out, bx, bz);
            }
        railway(out);
    }

    private static void block(List<Feature> out, int bx, int bz, int ring) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        int seed = CityGrid.hash(bx, bz, 2026);
        // Landmarks first: the water tower east of town, the grain elevator by the tracks.
        if (bx == 9 && bz == 2) { out.add(new Feature(ox + 6, oz + 6, ox + 31, oz + 31, c -> waterTower(c, ox + 19, oz + 19))); return; }
        if (bx == 5 && bz == 10) { out.add(new Feature(ox + 6, oz + 6, ox + 31, oz + 31, c -> grainElevator(c, ox, oz))); return; }
        if (overlapsRail(oz)) {
            if (ring >= 2) oilLease(out, ox, oz, seed, true);
            return;
        }
        if (ring == 1) {
            // On the roads into town, businesses; everywhere else, houses.
            boolean entry = bx == 4 || bz == 4 || bx == 0 || bz == 0;
            if (entry && seed % 3 == 0) business(out, bx, bz, ox, oz, seed);
            else houses(out, ox, oz, seed, 4);
        } else if (ring == 2) {
            if (seed % 5 < 2) houses(out, ox, oz, seed, 2);
            else if (seed % 5 < 4) oilLease(out, ox, oz, seed, false);
            else farmstead(out, ox, oz, seed);
        } else if (seed % 3 != 0) oilLease(out, ox, oz, seed, false);
    }

    private static boolean overlapsRail(int oz) { return RAIL_Z + 1 >= oz && RAIL_Z - 1 <= oz + 31; }

    // ------------------------------------------------------------------
    // Houses
    // ------------------------------------------------------------------

    private static final Block[][] HOUSES = {
            // wall, roof stairs, roof block, trim
            {Blocks.WHITE_TERRACOTTA, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_DARK_OAK_LOG},
            {Blocks.SMOOTH_SANDSTONE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS},
            {Blocks.BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.SMOOTH_STONE},
            {Blocks.YELLOW_TERRACOTTA, Blocks.BRICK_STAIRS, Blocks.BRICKS, Blocks.WHITE_CONCRETE},
            {Blocks.BIRCH_PLANKS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICKS, Blocks.STRIPPED_BIRCH_LOG},
            {Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.WHITE_CONCRETE},
            {Blocks.CUT_SANDSTONE, Blocks.GRANITE_STAIRS, Blocks.GRANITE, Blocks.SMOOTH_SANDSTONE}};

    /** Up to four ranch houses on a block, one per quarter, each facing the road on its side of the block. */
    private static void houses(List<Feature> out, int ox, int oz, int seed, int count) {
        int[][] lots = {{8, 8}, {20, 8}, {8, 20}, {20, 20}};
        for (int i = 0; i < 4; i++) {
            int s = CityGrid.hash(seed, i);
            if (count < 4 && s % 4 >= count) continue;
            int lx = ox + lots[i][0], lz = oz + lots[i][1];
            boolean north = i < 2;
            out.add(new Feature(lx - 1, lz - 1, lx + 12, lz + 12, c -> ranch(c, lx, lz, north, s)));
        }
    }

    /**
     * A single-storey ranch house on a 12 by 12 lot: a front yard with a walk and a driveway, a gabled roof with eaves,
     * windows either side of the door, a fenced back yard with a tree.
     */
    static void ranch(CityCanvas c, int lx, int lz, boolean north, int seed) {
        Block[] p = HOUSES[seed % HOUSES.length];
        Block wall = p[0], roofStairs = p[1], roofBlock = p[2], trim = p[3];
        Direction front = north ? Direction.NORTH : Direction.SOUTH;
        // The house: 9 wide and 6 deep, three blocks back from the road.
        int x0 = lx + 1, x1 = lx + 9, z0 = lz + 3, z1 = z0 + 5;
        int frontZ = north ? z0 : z1, yardZ = north ? lz : lz + 11, backZ = north ? lz + 11 : lz;
        int top = Y + 3;
        for (int x = lx; x <= lx + 11; x++)
            for (int z = lz; z <= lz + 11; z++) floor(c, x, z, Blocks.GRASS_BLOCK);
        // Walls, floor and a flat ceiling under the roof.
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                c.set(x, Y - 1, z, Blocks.SPRUCE_PLANKS);
                for (int y = Y; y <= top; y++) c.set(x, y, z, edge ? (corner ? trim : wall) : Blocks.AIR);
                c.set(x, top + 1, z, edge ? trim : Blocks.SPRUCE_PLANKS);
            }
        // Windows: two either side of the door at the front, two at the back, one in each end.
        int door = x0 + 3;
        for (int x : new int[] {x0 + 1, x0 + 2, x0 + 5, x0 + 6, x0 + 7})
            for (int y = Y + 1; y <= Y + 2; y++) {
                if (x == x0 + 5) continue;
                c.set(x, y, frontZ, Blocks.GLASS_PANE);
            }
        for (int x : new int[] {x0 + 2, x0 + 6}) c.set(x, Y + 1, north ? z1 : z0, Blocks.GLASS_PANE);
        for (int z = z0 + 2; z <= z0 + 3; z++) { c.set(x0, Y + 1, z, Blocks.GLASS_PANE); c.set(x1, Y + 1, z, Blocks.GLASS_PANE); }
        c.door(door, Y, frontZ, Blocks.SPRUCE_DOOR, front);
        c.set(door, Y + 2, frontZ, trim);
        // A step and a walk to the street; a porch light by the door.
        int fz = frontZ + front.getStepZ();
        floor(c, door, fz, Blocks.SMOOTH_STONE);
        for (int z = fz + front.getStepZ(); north ? z >= yardZ : z <= yardZ; z += front.getStepZ()) floor(c, door, z, Blocks.SMOOTH_STONE);
        c.set(door + 1, Y + 2, frontZ, Blocks.OCHRE_FROGLIGHT);
        // The driveway down the east side to the road, with a car in it.
        for (int z = lz; z <= lz + 11; z++) { floor(c, lx + 10, z, Blocks.SMOOTH_STONE); floor(c, lx + 11, z, Blocks.SMOOTH_STONE); }
        if (seed % 3 != 0)
            Streets.car(c, lx + 10, north ? lz : lz + 7, Direction.SOUTH, Direction.EAST, PAINT[seed % PAINT.length], true);
        // A lit room or two.
        if (seed % 2 == 0) c.set(x0 + 2, top, z0 + 2, Blocks.OCHRE_FROGLIGHT);
        if (seed % 5 == 0) c.set(x1 - 2, top, z1 - 2, Blocks.OCHRE_FROGLIGHT);
        // The roof: a gable along the house, a block of eave all round.
        for (int i = 0; i <= 3; i++) {
            int y = top + 2 + i;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                c.set(x, y, z0 - 1 + i, CityCanvas.stairs(roofStairs, Direction.SOUTH, false));
                c.set(x, y, z1 + 1 - i, CityCanvas.stairs(roofStairs, Direction.NORTH, false));
            }
            // The gable ends, in the wall's material.
            for (int z = z0 + i; z <= z1 - i; z++) { c.set(x0, y, z, wall); c.set(x1, y, z, wall); }
        }
        // A capping row of slabs along the ridge.
        Block cap = roofBlock == Blocks.BRICKS ? Blocks.BRICK_SLAB : roofBlock == Blocks.DEEPSLATE_TILES ? Blocks.DEEPSLATE_TILE_SLAB
                : roofBlock == Blocks.MUD_BRICKS ? Blocks.MUD_BRICK_SLAB : roofBlock == Blocks.GRANITE ? Blocks.GRANITE_SLAB
                : roofBlock == Blocks.SPRUCE_PLANKS ? Blocks.SPRUCE_SLAB : Blocks.DARK_OAK_SLAB;
        for (int x = x0 - 1; x <= x1 + 1; x++)
            for (int z = z0 + 2; z <= z0 + 3; z++) c.set(x, top + 6, z, CityCanvas.slab(cap, false));
        // The back yard: a fence along its edges, a tree, and a mailbox at the front.
        int fence0 = north ? z1 + 1 : backZ, fence1 = north ? backZ : z0 - 1;
        for (int z = Math.min(fence0, fence1); z <= Math.max(fence0, fence1); z++) {
            c.set(lx, Y, z, Blocks.OAK_FENCE);
            c.set(lx + 9, Y, z, Blocks.OAK_FENCE);
        }
        for (int x = lx; x <= lx + 9; x++) c.set(x, Y, backZ, Blocks.OAK_FENCE);
        int treeZ = north ? z1 + 2 : z0 - 2;
        if (seed % 4 != 1) yardTree(c, lx + 3, treeZ, seed);
        c.set(door - 2, Y, yardZ, Blocks.SPRUCE_FENCE);
        c.set(door - 2, Y + 1, yardZ, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, front));
    }

    private static final Block[][] PAINT = {
            {Blocks.RED_CONCRETE, Blocks.RED_CARPET}, {Blocks.BLUE_CONCRETE, Blocks.BLUE_CARPET},
            {Blocks.WHITE_CONCRETE, Blocks.WHITE_CARPET}, {Blocks.BROWN_CONCRETE, Blocks.BROWN_CARPET},
            {Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_CARPET}, {Blocks.GREEN_CONCRETE, Blocks.GREEN_CARPET},
            {Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_CARPET}};

    /** A small shade tree in a yard. */
    private static void yardTree(CityCanvas c, int x, int z, int seed) {
        Block log = seed % 2 == 0 ? Blocks.OAK_LOG : Blocks.ACACIA_LOG;
        BlockState leaf = CityCanvas.leaves(seed % 2 == 0 ? Blocks.OAK_LEAVES : Blocks.ACACIA_LEAVES);
        c.fill(x, Y, z, x, Y + 2, z, log);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 2; dy <= 4; dy++) {
                    if (dx == 0 && dz == 0 && dy == 2) continue;
                    double d = dx * dx + dz * dz + (dy - 3) * (dy - 3) * 1.5;
                    if (d > 5.2 + CityGrid.hash(x + dx, z + dz, dy) % 2) continue;
                    c.set(x + dx, Y + dy, z + dz, leaf);
                }
    }

    // ------------------------------------------------------------------
    // Businesses on the roads into town
    // ------------------------------------------------------------------

    private static void business(List<Feature> out, int bx, int bz, int ox, int oz, int seed) {
        switch (seed % 4) {
            case 0 -> out.add(new Feature(ox + 6, oz + 6, ox + 31, oz + 31, c -> gasStation(c, ox, oz, seed)));
            case 1 -> out.add(new Feature(ox + 6, oz + 6, ox + 31, oz + 31, c -> motel(c, ox, oz, seed)));
            case 2 -> out.add(new Feature(ox + 6, oz + 6, ox + 31, oz + 31, c -> diner(c, ox, oz, seed)));
            default -> stripMall(out, ox, oz, seed);
        }
    }

    private static void pave(CityCanvas c, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) floor(c, x, z, (x + z) % 9 == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE);
    }

    /** Lays a ground block, and clears the scrub that grew on the ground it replaces. */
    private static void floor(CityCanvas c, int x, int z, Block block) {
        c.set(x, Y - 1, z, block);
        c.set(x, Y, z, Blocks.AIR);
    }

    /** A filling station: a canopy on four posts over two islands of pumps, a little store, a tall price sign. */
    static void gasStation(CityCanvas c, int ox, int oz, int seed) {
        pave(c, ox + 8, oz + 8, ox + 31, oz + 31);
        int cx0 = ox + 11, cx1 = ox + 22, cz0 = oz + 11, cz1 = oz + 18;
        // The canopy: posts at the corners, a deck of white with a red band, lights underneath.
        for (int[] post : new int[][] {{cx0 + 1, cz0 + 1}, {cx1 - 1, cz0 + 1}, {cx0 + 1, cz1 - 1}, {cx1 - 1, cz1 - 1}})
            c.fill(post[0], Y, post[1], post[0], Y + 4, post[1], Blocks.WHITE_CONCRETE);
        for (int x = cx0; x <= cx1; x++)
            for (int z = cz0; z <= cz1; z++) {
                boolean edge = x == cx0 || x == cx1 || z == cz0 || z == cz1;
                c.set(x, Y + 5, z, edge ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
                if (!edge && (x - cx0) % 3 == 0 && (z - cz0) % 3 == 0) c.set(x, Y + 5, z, Blocks.SEA_LANTERN);
            }
        // Two islands, each with two pumps.
        for (int z : new int[] {cz0 + 3, cz1 - 3}) {
            for (int x = cx0 + 3; x <= cx1 - 3; x++) c.set(x, Y, z, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
            for (int x : new int[] {cx0 + 4, cx1 - 4}) {
                c.set(x, Y, z, Blocks.WHITE_CONCRETE);
                c.set(x, Y + 1, z, Blocks.RED_CONCRETE);
                c.set(x, Y + 2, z, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
                c.set(x + 1, Y + 1, z, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.EAST));
            }
        }
        // The store behind the canopy.
        int sx0 = ox + 12, sx1 = ox + 21, sz0 = oz + 23, sz1 = oz + 29;
        for (int x = sx0; x <= sx1; x++)
            for (int z = sz0; z <= sz1; z++) {
                boolean edge = x == sx0 || x == sx1 || z == sz0 || z == sz1;
                floor(c, x, z, Blocks.SMOOTH_STONE);
                for (int y = Y; y <= Y + 3; y++) c.set(x, y, z, edge ? Blocks.WHITE_CONCRETE : Blocks.AIR);
                c.set(x, Y + 4, z, edge ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
            }
        for (int x = sx0 + 1; x < sx1; x++) if (x != sx0 + 4) c.fill(x, Y + 1, sz0, x, Y + 2, sz0, Blocks.GLASS_PANE);
        c.door(sx0 + 4, Y, sz0, Blocks.WAXED_COPPER_DOOR, Direction.NORTH);
        for (int x = sx0 + 2; x < sx1; x += 3) c.set(x, Y + 3, sz0 + 3, Blocks.SEA_LANTERN);
        for (int x = sx0 + 1; x < sx1; x++) c.set(x, Y, sz1 - 1, Blocks.BOOKSHELF);
        c.sign(sx0 + 6, Y + 4, sz0 - 1, Direction.NORTH, Blocks.OAK_WALL_SIGN, DyeColor.RED, true, "FOOD MART");
        // The price sign on a tall pole by the road.
        int px = ox + 27, pz = oz + 10;
        c.fill(px, Y, pz, px, Y + 9, pz, Blocks.POLISHED_BLACKSTONE_WALL);
        c.fill(px - 1, Y + 10, pz, px + 1, Y + 12, pz, Blocks.WHITE_CONCRETE);
        c.set(px, Y + 13, pz, Blocks.RED_CONCRETE);
        c.sign(px, Y + 11, pz - 1, Direction.NORTH, Blocks.OAK_WALL_SIGN, DyeColor.RED, true, "GAS", "1.29 9/10");
        c.sign(px, Y + 11, pz + 1, Direction.SOUTH, Blocks.OAK_WALL_SIGN, DyeColor.RED, true, "GAS", "1.29 9/10");
    }

    /** A two-storey motel round a parking court, rooms opening onto a balcony, a tall sign that is lit. */
    static void motel(CityCanvas c, int ox, int oz, int seed) {
        pave(c, ox + 8, oz + 8, ox + 31, oz + 31);
        int x0 = ox + 10, x1 = ox + 29, z0 = oz + 22, z1 = oz + 29;
        Block wall = seed % 2 == 0 ? Blocks.WHITE_TERRACOTTA : Blocks.LIGHT_BLUE_TERRACOTTA;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                c.set(x, Y - 1, z, Blocks.SMOOTH_STONE);
                for (int y = Y; y <= Y + 8; y++) c.set(x, y, z, edge ? wall : Blocks.AIR);
                c.set(x, Y + 4, z, Blocks.SMOOTH_STONE);
                c.set(x, Y + 9, z, Blocks.GRAY_CONCRETE);
            }
        // Doors along the front on both floors, a window beside each, and the balcony with its rail.
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            for (int floor = 0; floor < 2; floor++) {
                int y = Y + floor * 5;
                c.door(x, y, z0, Blocks.SPRUCE_DOOR, Direction.NORTH);
                c.set(x + 1, y + 1, z0, Blocks.GLASS_PANE);
                c.set(x, y + 3, z0 - 1, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, true));
            }
        }
        for (int x = x0; x <= x1; x++) {
            c.set(x, Y + 4, z0 - 1, Blocks.SMOOTH_STONE);
            c.set(x, Y + 4, z0 - 2, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, true));
            c.set(x, Y + 5, z0 - 2, Blocks.IRON_BARS);
            c.set(x, Y + 9, z0 - 1, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
        }
        for (int x = x0; x <= x1; x += 4) c.fill(x, Y, z0 - 2, x, Y + 3, z0 - 2, Blocks.WHITE_CONCRETE);
        for (int x = x0 + 1; x < x1; x++) { c.set(x, Y + 5, z0 - 1, Blocks.AIR); c.set(x, Y + 6, z0 - 1, Blocks.AIR); }
        // A stair up to the balcony at the west end, its top step level with the balcony floor.
        for (int i = 0; i <= 4; i++) {
            int z = z0 - 6 + i;
            if (i > 0) c.fill(x0 - 1, Y, z, x0 - 1, Y + i - 1, z, Blocks.SMOOTH_SANDSTONE);
            c.set(x0 - 1, Y + i, z, CityCanvas.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, Direction.SOUTH, false));
        }
        c.set(x0, Y + 5, z0 - 2, Blocks.AIR);
        // Parking stalls and a couple of cars.
        for (int x = x0 + 1; x <= x1 - 1; x += 3) c.set(x, Y - 1, oz + 15, Blocks.WHITE_CONCRETE);
        Streets.car(c, x0 + 2, oz + 15, Direction.SOUTH, Direction.EAST, PAINT[seed % PAINT.length], true);
        Streets.car(c, x0 + 11, oz + 15, Direction.SOUTH, Direction.EAST, PAINT[(seed + 3) % PAINT.length], true);
        // The sign: a pole, a board, lights round it.
        int px = ox + 10, pz = oz + 10;
        c.fill(px, Y, pz, px, Y + 8, pz, Blocks.POLISHED_BLACKSTONE_WALL);
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = 9; dy <= 12; dy++) {
                boolean frame = Math.abs(dx) == 2 || dy == 9 || dy == 12;
                c.set(px + dx, Y + dy, pz, frame ? (dx + dy) % 2 == 0 ? Blocks.REDSTONE_LAMP.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true) : Blocks.RED_CONCRETE.defaultBlockState()
                        : Blocks.WHITE_CONCRETE.defaultBlockState());
            }
        c.sign(px, Y + 11, pz - 1, Direction.NORTH, Blocks.OAK_WALL_SIGN, DyeColor.RED, true, "MOTEL", "VACANCY");
        c.sign(px, Y + 11, pz + 1, Direction.SOUTH, Blocks.OAK_WALL_SIGN, DyeColor.RED, true, "MOTEL", "VACANCY");
    }

    /** A chrome diner: a long low box with a band of windows, a red stripe, a sign on its roof. */
    static void diner(CityCanvas c, int ox, int oz, int seed) {
        pave(c, ox + 8, oz + 8, ox + 31, oz + 31);
        int x0 = ox + 12, x1 = ox + 27, z0 = oz + 16, z1 = oz + 22;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                c.set(x, Y - 1, z, (x + z) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.BLACK_CONCRETE);
                c.set(x, Y, z, edge ? Blocks.IRON_BLOCK : Blocks.AIR);
                c.set(x, Y + 1, z, edge ? Blocks.GLASS : Blocks.AIR);
                c.set(x, Y + 2, z, edge ? Blocks.GLASS : Blocks.AIR);
                c.set(x, Y + 3, z, edge ? Blocks.RED_CONCRETE : Blocks.AIR);
                c.set(x, Y + 4, z, Blocks.IRON_BLOCK);
            }
        c.door((x0 + x1) / 2, Y, z0, Blocks.WAXED_COPPER_DOOR, Direction.NORTH);
        c.set((x0 + x1) / 2, Y + 2, z0, Blocks.IRON_BLOCK);
        // A counter with stools, booths along the windows.
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            c.set(x, Y, z1 - 2, Blocks.WHITE_CONCRETE);
            if (x % 2 == 0) c.set(x, Y, z1 - 3, Blocks.RED_NETHER_BRICK_WALL);
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 4) c.set(x, Y + 3, (z0 + z1) / 2, Blocks.SEA_LANTERN);
        // The roof sign.
        int sx = (x0 + x1) / 2;
        for (int dx = -3; dx <= 3; dx++) {
            c.set(sx + dx, Y + 5, z0 + 1, Blocks.RED_CONCRETE);
            c.set(sx + dx, Y + 6, z0 + 1, Blocks.WHITE_CONCRETE);
        }
        c.sign(sx, Y + 6, z0, Direction.NORTH, Blocks.OAK_WALL_SIGN, DyeColor.RED, true, "DINER", "OPEN 24 HRS");
    }

    /** A strip of shops behind a parking lot, drawn like the city's own shopfronts. */
    private static void stripMall(List<Feature> out, int ox, int oz, int seed) {
        Building.Look look = CityPlan.brick(seed).with(Building.Ground.SHOP);
        int x0 = ox + 10, x1 = ox + 29, z0 = oz + 21, z1 = oz + 29;
        Building mall = new Building(x0, z0, x1, z1, 0, look, Building.tiers(0), Building.Top.FLAT,
                new boolean[] {true, false, false, false}, seed, "VIDEO RENTAL", "NAILS", "TACOS", "PAWN", "CLEANERS", "PIZZA");
        out.add(new Feature(ox + 6, oz + 6, ox + 31, oz + 31, c -> {
            pave(c, ox + 8, oz + 8, ox + 31, oz + 31);
            for (int x = x0; x <= x1; x++) c.set(x, Y, z0 - 1, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
            for (int x = x0 + 1; x <= x1 - 1; x += 3) c.set(x, Y - 1, oz + 13, Blocks.WHITE_CONCRETE);
            mall.draw(c);
            Streets.car(c, x0 + 3, oz + 12, Direction.SOUTH, Direction.EAST, PAINT[seed % PAINT.length], true);
            Streets.car(c, x0 + 12, oz + 12, Direction.SOUTH, Direction.EAST, PAINT[(seed + 2) % PAINT.length], true);
        }));
    }

    // ------------------------------------------------------------------
    // Oil country
    // ------------------------------------------------------------------

    /** A lease: one or two pumpjacks on caliche pads, a tank battery, a dirt track to the road. */
    private static void oilLease(List<Feature> out, int ox, int oz, int seed, boolean nearRail) {
        int jacks = 1 + seed % 2;
        for (int i = 0; i < jacks; i++) {
            int s = CityGrid.hash(seed, i, 9);
            int x = ox + 11 + s % 12, z = oz + 11 + (s / 13) % 12;
            if (nearRail && Math.abs(z - RAIL_Z) < 6) z = RAIL_Z > oz + 16 ? oz + 11 : oz + 24;
            boolean ns = s % 2 == 0;
            final int fx = x, fz = z;
            out.add(new Feature(x - 6, z - 6, x + 6, z + 6, c -> pumpjack(c, fx, fz, ns)));
        }
        if (seed % 3 == 0 && !nearRail) out.add(new Feature(ox + 20, oz + 9, ox + 31, oz + 15, c -> tanks(c, ox + 22, oz + 11)));
    }

    /**
     * A pumpjack: a caliche pad, the gearbox and crank, a Samson post carrying the walking beam, the horse's head over
     * the wellhead at one end and the counterweight at the other.
     */
    static void pumpjack(CityCanvas c, int x, int z, boolean northSouth) {
        Direction along = northSouth ? Direction.SOUTH : Direction.EAST, across = northSouth ? Direction.EAST : Direction.SOUTH;
        for (int u = -4; u <= 4; u++)
            for (int w = -2; w <= 2; w++)
                floor(c, x + along.getStepX() * u + across.getStepX() * w, z + along.getStepZ() * u + across.getStepZ() * w, Blocks.SMOOTH_SANDSTONE);
        java.util.function.BiConsumer<int[], BlockState> put = (p, state) ->
                c.set(x + along.getStepX() * p[0] + across.getStepX() * p[2], Y + p[1], z + along.getStepZ() * p[0] + across.getStepZ() * p[2], state);
        // The base rail and the Samson post's two legs.
        for (int u = -3; u <= 3; u++) put.accept(new int[] {u, 0, 0}, Blocks.GRAY_CONCRETE.defaultBlockState());
        for (int w : new int[] {-1, 1}) for (int h = 1; h <= 4; h++) put.accept(new int[] {0, h, w}, Blocks.IRON_BARS.defaultBlockState());
        for (int w : new int[] {-1, 1}) put.accept(new int[] {0, 0, w}, Blocks.GRAY_CONCRETE.defaultBlockState());
        put.accept(new int[] {0, 5, -1}, Blocks.GRAY_CONCRETE.defaultBlockState());
        put.accept(new int[] {0, 5, 1}, Blocks.GRAY_CONCRETE.defaultBlockState());
        put.accept(new int[] {0, 5, 0}, Blocks.GRAY_CONCRETE.defaultBlockState());
        // The walking beam along the top.
        for (int u = -3; u <= 3; u++) put.accept(new int[] {u, 6, 0}, Blocks.BLACK_CONCRETE.defaultBlockState());
        // The horse's head at the wellhead end, and the bridle down to the well.
        put.accept(new int[] {4, 6, 0}, Blocks.BLACK_CONCRETE.defaultBlockState());
        put.accept(new int[] {4, 5, 0}, CityCanvas.stairs(Blocks.BLACKSTONE_STAIRS, along.getOpposite(), true));
        put.accept(new int[] {4, 7, 0}, CityCanvas.stairs(Blocks.BLACKSTONE_STAIRS, along.getOpposite(), false));
        for (int h = 1; h <= 4; h++) put.accept(new int[] {4, h, 0}, Blocks.CHAIN.defaultBlockState());
        put.accept(new int[] {4, 0, 0}, Blocks.IRON_BLOCK.defaultBlockState());
        // The crank, the counterweights and the gearbox at the other end.
        put.accept(new int[] {-3, 5, 0}, Blocks.IRON_BARS.defaultBlockState());
        put.accept(new int[] {-3, 4, 0}, Blocks.IRON_BARS.defaultBlockState());
        for (int w : new int[] {-1, 1}) {
            put.accept(new int[] {-3, 2, w}, Blocks.RED_CONCRETE.defaultBlockState());
            put.accept(new int[] {-3, 3, w}, Blocks.RED_CONCRETE.defaultBlockState());
        }
        put.accept(new int[] {-3, 1, 0}, Blocks.GRAY_CONCRETE.defaultBlockState());
        put.accept(new int[] {-3, 2, 0}, Blocks.GRAY_CONCRETE.defaultBlockState());
        put.accept(new int[] {-3, 3, 0}, Blocks.GRAY_CONCRETE.defaultBlockState());
    }

    /** A tank battery: three squat tanks on a pad behind a berm, with a walkway along their tops. */
    static void tanks(CityCanvas c, int x, int z) {
        for (int i = 0; i < 3; i++) {
            int tx = x + i * 3;
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++) {
                    floor(c, tx + dx, z + dz, Blocks.SMOOTH_SANDSTONE);
                    for (int y = Y; y <= Y + 3; y++) c.set(tx + dx, y, z + dz, y == Y + 1 ? Blocks.BROWN_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE);
                }
            c.set(tx, Y + 4, z, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
            c.set(tx + 1, Y + 4, z + 1, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
            if (i < 2) {
                c.set(tx + 2, Y + 3, z, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, true));
                c.set(tx + 2, Y + 4, z, Blocks.IRON_BARS);
            }
        }
        for (int dx = -1; dx <= 8; dx++) { c.set(x + dx, Y, z - 1, Blocks.COARSE_DIRT); c.set(x + dx, Y, z + 2, Blocks.COARSE_DIRT); }
        for (int dx = -1; dx <= 8; dx++) for (int dz = 0; dz <= 1; dz++) if (Math.floorMod(dx, 3) == 2) floor(c, x + dx, z + dz, Blocks.SMOOTH_SANDSTONE);
    }

    /** A farmstead: a red barn and a windmill on a tower, the farm's own drive. */
    private static void farmstead(List<Feature> out, int ox, int oz, int seed) {
        out.add(new Feature(ox + 6, oz + 6, ox + 31, oz + 31, c -> {
            int x0 = ox + 14, x1 = ox + 22, z0 = oz + 16, z1 = oz + 26;
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    floor(c, x, z, Blocks.COARSE_DIRT);
                    for (int y = Y; y <= Y + 4; y++) c.set(x, y, z, edge ? (y == Y ? Blocks.STONE_BRICKS : Blocks.RED_TERRACOTTA) : Blocks.AIR);
                }
            for (int i = 0; i <= 4; i++)
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    c.set(x0 - 1 + i, Y + 5 + i, z, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST, false));
                    c.set(x1 + 1 - i, Y + 5 + i, z, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
                }
            for (int z = z0 - 1; z <= z1 + 1; z++) c.set(x0 + 4, Y + 9, z, Blocks.DARK_OAK_PLANKS);
            for (int i = 0; i <= 3; i++)
                for (int x = x0 + i; x <= x1 - i; x++) { c.set(x, Y + 5 + i, z0, Blocks.RED_TERRACOTTA); c.set(x, Y + 5 + i, z1, Blocks.RED_TERRACOTTA); }
            for (int x = x0 + 3; x <= x1 - 3; x++) c.fill(x, Y, z0, x, Y + 3, z0, Blocks.AIR);
            for (int y = Y; y <= Y + 3; y++) { c.set(x0 + 2, y, z0, Blocks.WHITE_CONCRETE); c.set(x1 - 2, y, z0, Blocks.WHITE_CONCRETE); }
            for (int x = x0 + 2; x <= x1 - 2; x++) c.set(x, Y + 4, z0, Blocks.WHITE_CONCRETE);
            c.fill(x0 + 1, Y, z0 + 4, x0 + 1, Y, z1 - 1, Blocks.HAY_BLOCK);
            c.fill(x0 + 1, Y + 1, z0 + 6, x0 + 1, Y + 1, z1 - 1, Blocks.HAY_BLOCK);
            // The windmill.
            int wx = ox + 27, wz = oz + 12;
            for (int y = Y; y <= Y + 9; y++) c.set(wx, y, wz, Blocks.SPRUCE_FENCE);
            c.set(wx, Y + 10, wz, Blocks.GRAY_CONCRETE);
            for (int[] blade : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {0, 2}, {0, -2}, {2, 0}, {-2, 0}})
                c.set(wx + blade[0], Y + 10 + blade[1], wz - 1, Blocks.IRON_TRAPDOOR.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.SOUTH)
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true));
            c.set(wx, Y + 10, wz - 1, Blocks.GRAY_CONCRETE);
            c.set(wx, Y + 10, wz + 1, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
            c.fill(wx - 1, Y, wz + 2, wx + 1, Y, wz + 3, Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        }));
    }

    // ------------------------------------------------------------------
    // Landmarks on the edge of town
    // ------------------------------------------------------------------

    /** Odessa's water tower: four legs and cross-bracing, a round tank with the town's name, a ladder, a light on top. */
    static void waterTower(CityCanvas c, int x, int z) {
        int base = Y + 22;
        for (int[] leg : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            floor(c, x + leg[0], z + leg[1], Blocks.SMOOTH_STONE);
            c.fill(x + leg[0], Y, z + leg[1], x + leg[0], base + 1, z + leg[1], Blocks.ANDESITE_WALL);
        }
        // Bracing rings every seven blocks.
        for (int y = Y + 7; y < base; y += 7) {
            for (int d = -2; d <= 2; d++) {
                c.set(x + d, y, z - 3, Blocks.IRON_BARS); c.set(x + d, y, z + 3, Blocks.IRON_BARS);
                c.set(x - 3, y, z + d, Blocks.IRON_BARS); c.set(x + 3, y, z + d, Blocks.IRON_BARS);
            }
        }
        c.fill(x, Y, z, x, base, z, Blocks.POLISHED_ANDESITE);
        // The tank: a fat disc, a band round its middle, a cap.
        for (int dy = 1; dy <= 7; dy++) {
            double r = dy == 1 ? 3.6 : dy == 7 ? 4.2 : dy == 2 || dy == 6 ? 4.6 : 5.2;
            for (int dx = -6; dx <= 6; dx++)
                for (int dz = -6; dz <= 6; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r) continue;
                    boolean shell = d > r - 1.2 || dy == 1 || dy == 7;
                    c.set(x + dx, base + dy, z + dz, shell ? (dy == 4 ? Blocks.LIGHT_BLUE_CONCRETE : Blocks.WHITE_CONCRETE) : Blocks.AIR);
                }
        }
        c.set(x, base + 8, z, Blocks.WHITE_CONCRETE);
        c.set(x, base + 9, z, Blocks.LIGHTNING_ROD);
        c.set(x, base + 10, z, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        for (Direction side : Direction.Plane.HORIZONTAL)
            c.sign(x + side.getStepX() * 6, base + 4, z + side.getStepZ() * 6, side, Blocks.BIRCH_WALL_SIGN, DyeColor.BLUE, true, "ODESSA");
        // A ladder up one leg's side of the central column.
        for (int y = Y; y <= base; y++) c.set(x, y, z + 1, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
    }

    /** A grain elevator by the tracks: a row of tall concrete silos and the headhouse on top. */
    static void grainElevator(CityCanvas c, int ox, int oz) {
        int x0 = ox + 10, z = oz + 22;
        if (Math.abs(z - RAIL_Z) < 6) z = RAIL_Z - 9;
        for (int i = 0; i < 4; i++) {
            int cx = x0 + 2 + i * 5;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    floor(c, cx + dx, z + dz, Blocks.SMOOTH_STONE);
                    for (int y = Y; y <= Y + 26; y++) c.set(cx + dx, y, z + dz, edge ? Blocks.WHITE_CONCRETE : Blocks.AIR);
                    c.set(cx + dx, Y + 27, z + dz, Blocks.LIGHT_GRAY_CONCRETE);
                }
        }
        // The headhouse spanning the silo tops, its windows lit.
        int hx0 = x0 + 4, hx1 = x0 + 15;
        for (int x = hx0; x <= hx1; x++)
            for (int dz = -2; dz <= 2; dz++)
                for (int y = Y + 28; y <= Y + 32; y++) {
                    boolean edge = x == hx0 || x == hx1 || Math.abs(dz) == 2 || y == Y + 32;
                    c.set(x, y, z + dz, edge ? (y == Y + 30 && x % 3 == 0 && Math.abs(dz) == 2 ? Blocks.GLASS : Blocks.LIGHT_GRAY_CONCRETE) : Blocks.AIR);
                }
        c.set(hx0 + 2, Y + 31, z, Blocks.SEA_LANTERN);
        c.sign(x0 + 12, Y + 20, z - 3, Direction.NORTH, Blocks.BIRCH_WALL_SIGN, DyeColor.BLACK, false, "ODESSA", "GRAIN CO-OP");
        // A loading shed against the silos, toward the tracks.
        for (int x = x0; x <= x0 + 19; x++) {
            c.fill(x, Y, z + 3, x, Y + 3, z + 3, x % 4 == 0 ? Blocks.SPRUCE_LOG : Blocks.AIR);
            c.set(x, Y + 4, z + 3, CityCanvas.slab(Blocks.SPRUCE_SLAB, false));
            c.set(x, Y + 4, z + 4, CityCanvas.slab(Blocks.SPRUCE_SLAB, false));
            c.set(x, Y + 3, z + 4, x % 4 == 0 ? Blocks.SPRUCE_LOG : Blocks.AIR);
        }
        for (int x = x0; x <= x0 + 19; x += 4) c.fill(x, Y, z + 4, x, Y + 3, z + 4, Blocks.SPRUCE_LOG);
    }

    /** A freight train standing on the line south of town: boxcars, hoppers and tank cars. */
    private static void railway(List<Feature> out) {
        int start = CityGrid.OUTER_MIN + 40;
        for (int i = 0; i < 9; i++) {
            int x = start + i * 10;
            int kind = CityGrid.hash(i, 4) % 3;
            out.add(new Feature(x - 1, RAIL_Z - 2, x + 9, RAIL_Z + 2, c -> railcar(c, x, kind)));
        }
    }

    static void railcar(CityCanvas c, int x, int kind) {
        Block body = kind == 0 ? Blocks.BROWN_CONCRETE : kind == 1 ? Blocks.GRAY_CONCRETE : Blocks.BLACK_CONCRETE;
        for (int dx = 0; dx < 9; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int z = RAIL_Z + dz;
                c.set(x + dx, Y + 1, z, Blocks.POLISHED_BLACKSTONE);
                for (int y = Y + 2; y <= Y + 4; y++) {
                    boolean round = kind == 2 && (y == Y + 4 && dz != 0);
                    c.set(x + dx, y, z, round ? Blocks.AIR : body);
                }
                if (kind == 0 && dz != 0 && dx == 4) c.fill(x + dx, Y + 2, z, x + dx, Y + 3, z, Blocks.SPRUCE_PLANKS);
            }
        if (kind == 2) for (int dx = 0; dx < 9; dx++) c.set(x + dx, Y + 4, RAIL_Z, body);
        // Wheels: the car stands on its trucks on the rails.
        for (int dx : new int[] {1, 7}) c.set(x + dx, Y, RAIL_Z, Blocks.RAIL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.EAST_WEST));
        for (int dx : new int[] {0, 2, 6, 8}) c.set(x + dx, Y, RAIL_Z, Blocks.POLISHED_BLACKSTONE_SLAB);
    }

    /** Wooden poles on the gravel shoulder of every arterial, wires slung between them. */
    private static void powerLines(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        if (Math.floorMod(bx, 4) == 0) out.add(new Feature(ox - 1, oz, ox + 1, oz + 31, c -> poles(c, ox, oz, true)));
        if (Math.floorMod(bz, 4) == 0) out.add(new Feature(ox, oz - 1, ox + 31, oz + 1, c -> poles(c, oz, ox, false)));
    }

    /** Poles every sixteen blocks along one shoulder, a crossarm on each, two wires running the length of the line. */
    private static void poles(CityCanvas c, int line, int from, boolean northSouth) {
        int top = Y + 9;
        Direction across = northSouth ? Direction.EAST : Direction.SOUTH;
        for (int a = from; a < from + 32; a++) {
            int x = northSouth ? line : a, z = northSouth ? a : line;
            if (!inRing(x, z)) continue;
            int crossing = northSouth ? CityGrid.lz(z) : CityGrid.lx(x);
            boolean pole = Math.floorMod(a, 16) == 12 && crossing >= CityGrid.ROAD && !rail(z);
            if (pole) {
                c.fill(x, Y, z, x, top - 1, z, Blocks.SPRUCE_FENCE);
                for (int w = -1; w <= 1; w++) c.set(x + across.getStepX() * w, top, z + across.getStepZ() * w,
                        CityCanvas.log(Blocks.STRIPPED_SPRUCE_LOG, northSouth ? Direction.Axis.X : Direction.Axis.Z));
                continue;
            }
            for (int w = -1; w <= 1; w += 2)
                c.set(x + across.getStepX() * w, top, z + across.getStepZ() * w, Blocks.CHAIN.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.ChainBlock.AXIS, northSouth ? Direction.Axis.Z : Direction.Axis.X));
        }
    }
}
