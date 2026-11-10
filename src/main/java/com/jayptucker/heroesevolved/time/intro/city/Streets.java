package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Asphalt, paint and pavement, and everything that stands on a sidewalk. Roads stay flat at the
 * street level so people can walk and run on them; sidewalks are a half-block up behind a curb.
 * Nothing is ever parked on the avenue: it is the way to Bennet's door.
 */
final class Streets {
    private Streets() {}

    // ------------------------------------------------------------------
    // Ground, one column at a time
    // ------------------------------------------------------------------

    static void ground(CityCanvas c, int x, int z) {
        if (!CityGrid.inCity(x, z)) return;
        int y = CityGrid.STREET_Y - 1;
        if (CityGrid.terminus(x, z)) { c.set(x, y, z, Blocks.SMOOTH_STONE); return; }
        int lx = CityGrid.lx(x), lz = CityGrid.lz(z);
        if (CityGrid.road(x, z)) { c.set(x, y, z, asphalt(lx, lz)); return; }
        c.set(x, y, z, Blocks.SMOOTH_STONE);
        boolean band = lx <= 10 || lx >= 29 || lz <= 10 || lz >= 29;
        if (!band) return;
        boolean curb = lx == 8 || lx == 31 || lz == 8 || lz == 31;
        int along = lx <= 10 || lx >= 29 ? lz : lx;
        boolean corner = (lx <= 10 || lx >= 29) && (lz <= 10 || lz >= 29);
        Block slab = curb ? Blocks.STONE_SLAB : corner || along % 4 == 0 ? Blocks.POLISHED_ANDESITE_SLAB : Blocks.SMOOTH_STONE_SLAB;
        c.set(x, y + 1, z, CityCanvas.slab(slab, false));
    }

    private static Block asphalt(int lx, int lz) {
        boolean northSouth = lx < CityGrid.ROAD && lz >= CityGrid.ROAD, eastWest = lz < CityGrid.ROAD && lx >= CityGrid.ROAD;
        int across = northSouth ? lx : lz, along = northSouth ? lz : lx;
        if (northSouth || eastWest) {
            boolean crossing = along <= 10 || along >= 29;
            if (crossing) return across % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE;
            if (across == 3 || across == 4) return Blocks.YELLOW_CONCRETE;
            if (along == 11 || along == 28) return Blocks.WHITE_CONCRETE; // stop lines
        }
        return Blocks.GRAY_CONCRETE;
    }

    // ------------------------------------------------------------------
    // Sidewalk furniture, per side of a block
    // ------------------------------------------------------------------

    /** Kinds of street: how much greenery and what stands on the corners. */
    enum Kind { DOWNTOWN, MIDTOWN, RESIDENTIAL, PARK }

    /**
     * Everything on one block's four sidewalks. Positions run along each side from 11 to 28; lamps
     * stand at the curb every eight, trees between them.
     */
    static void furnish(List<Feature> out, int bx, int bz, Kind kind) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        for (int side = 0; side < 4; side++) {
            final int s = side;
            int seed = CityGrid.hash(bx, bz, side, 5);
            // Bus stops along the avenue, on the blocks east of it; they take that sidewalk's middle.
            boolean stop = side == 2 && bx == 4 && bz % 2 == 0;
            for (int along : new int[] {13, 27}) out.add(prop(ox, oz, s, along, 0, 2, c -> lamp(c, ox, oz, s, along)));
            if (stop) out.add(prop(ox, oz, s, 17, 2, 3, c -> busStop(c, ox, oz, s, 15)));
            else if (kind != Kind.DOWNTOWN || seed % 3 == 0)
                for (int along : new int[] {17, 22}) out.add(prop(ox, oz, s, along, 0, 3, c -> tree(c, ox, oz, s, along, bx * 7 + bz + along)));
            else for (int along : new int[] {17, 22}) out.add(prop(ox, oz, s, along, 1, 1, c -> planter(c, ox, oz, s, along)));
            if (side == 0) out.add(prop(ox, oz, s, 15, 1, 0, c -> hydrant(c, ox, oz, s, 15)));
            if (seed % 2 == 0 && !stop) out.add(prop(ox, oz, s, 19, 1, 1, c -> bench(c, ox, oz, s, 19, 24)));
        }
    }

    /** One side's frame: where along a side, how far in from the curb, its outward direction. */
    private static int sx(int ox, int side, int along, int depth) {
        return switch (side) { case 0, 1 -> ox + along; case 2 -> ox + 8 + depth; default -> ox + 31 - depth; };
    }
    private static int sz(int oz, int side, int along, int depth) {
        return switch (side) { case 0 -> oz + 8 + depth; case 1 -> oz + 31 - depth; default -> oz + along; };
    }
    private static Direction out(int side) { return Building.OUT[side]; }

    private static Feature prop(int ox, int oz, int side, int along, int depth, int reach, java.util.function.Consumer<CityCanvas> draw) {
        int x = sx(ox, side, along, depth), z = sz(oz, side, along, depth);
        return new Feature(x - reach - 2, z - reach - 2, x + reach + 2, z + reach + 2, draw);
    }

    /** A tall thin pole leaning a lantern out over the road. */
    static void lamp(CityCanvas c, int ox, int oz, int side, int along) {
        int x = sx(ox, side, along, 0), z = sz(oz, side, along, 0);
        Direction out = out(side);
        int y = CityGrid.STREET_Y;
        c.set(x, y, z, Blocks.POLISHED_BLACKSTONE_WALL);
        c.fill(x, y + 1, z, x, y + 6, z, Blocks.IRON_BARS);
        c.set(x, y + 7, z, CityCanvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB, false));
        int ax = x + out.getStepX(), az = z + out.getStepZ();
        c.set(ax, y + 7, az, CityCanvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB, false));
        c.set(ax, y + 6, az, CityCanvas.hangingLantern(Blocks.LANTERN));
    }

    static void tree(CityCanvas c, int ox, int oz, int side, int along, int salt) {
        int x = sx(ox, side, along, 0), z = sz(oz, side, along, 0);
        Direction out = out(side);
        int y = CityGrid.STREET_Y;
        int kind = CityGrid.hash(salt, 3) % 3;
        Block log = kind == 0 ? Blocks.OAK_LOG : kind == 1 ? Blocks.BIRCH_LOG : Blocks.DARK_OAK_LOG;
        Block leaves = kind == 0 ? Blocks.OAK_LEAVES : kind == 1 ? Blocks.BIRCH_LEAVES : Blocks.DARK_OAK_LEAVES;
        c.set(x, y, z, Blocks.COARSE_DIRT);
        // A low iron fence around the pit.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == out) continue;
            c.set(x + d.getStepX(), y, z + d.getStepZ(), CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, d.getOpposite(), false, true));
        }
        c.fill(x, y + 1, z, x, y + 3, z, log);
        BlockState leaf = CityCanvas.leaves(leaves);
        for (int dy = 3; dy <= 6; dy++) {
            double r = dy == 3 ? 1.5 : dy == 6 ? 1.3 : 2.4;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0 && dy <= 3) continue;
                    // Never into the shopfronts: the canopy reaches at most one block in from the curb.
                    int inward = -(dx * out.getStepX() + dz * out.getStepZ());
                    if (inward > 1) continue;
                    if (dx * dx + dz * dz + (dy - 4.5) * (dy - 4.5) * 0.6 > r * r + 0.5) continue;
                    c.set(x + dx, y + dy, z + dz, leaf);
                }
        }
    }

    static void planter(CityCanvas c, int ox, int oz, int side, int along) {
        int x = sx(ox, side, along, 1), z = sz(oz, side, along, 1);
        int y = CityGrid.STREET_Y;
        c.set(x, y, z, Blocks.POLISHED_ANDESITE);
        c.set(x, y + 1, z, Blocks.AZALEA);
    }

    static void hydrant(CityCanvas c, int ox, int oz, int side, int along) {
        int x = sx(ox, side, along, 1), z = sz(oz, side, along, 1);
        c.set(x, CityGrid.STREET_Y, z, Blocks.RED_NETHER_BRICK_WALL);
    }

    static void bench(CityCanvas c, int ox, int oz, int side, int along, int bin) {
        Direction out = out(side);
        for (int a = along; a <= along + 1; a++)
            c.set(sx(ox, side, a, 1), CityGrid.STREET_Y, sz(oz, side, a, 1), CityCanvas.stairs(Blocks.SPRUCE_STAIRS, out.getOpposite(), false));
        c.set(sx(ox, side, bin, 1), CityGrid.STREET_Y, sz(oz, side, bin, 1), Blocks.CAULDRON);
    }

    static void busStop(CityCanvas c, int ox, int oz, int side, int start) {
        Direction out = out(side);
        int y = CityGrid.STREET_Y;
        for (int a = start; a <= start + 4; a++) {
            int bx = sx(ox, side, a, 2), bz = sz(oz, side, a, 2);
            c.fill(bx, y, bz, bx, y + 2, bz, Blocks.GLASS_PANE);
            c.set(bx, y + 3, bz, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
            c.set(sx(ox, side, a, 1), y + 3, sz(oz, side, a, 1), CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
        }
        for (int a : new int[] {start, start + 4}) c.fill(sx(ox, side, a, 1), y, sz(oz, side, a, 1), sx(ox, side, a, 1), y + 2, sz(oz, side, a, 1), Blocks.POLISHED_BLACKSTONE_WALL);
        for (int a = start + 1; a <= start + 3; a++)
            c.set(sx(ox, side, a, 1), y, sz(oz, side, a, 1), CityCanvas.stairs(Blocks.SPRUCE_STAIRS, out.getOpposite(), false));
        int px = sx(ox, side, start - 1, 0), pz = sz(oz, side, start - 1, 0);
        c.fill(px, y, pz, px, y + 3, pz, Blocks.POLISHED_BLACKSTONE_WALL);
        c.sign(px + out.getStepX(), y + 2, pz + out.getStepZ(), out, Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, "BUS 14", "AVENUE");
    }

    // ------------------------------------------------------------------
    // Parked cars
    // ------------------------------------------------------------------

    private static final Block[][] PAINT = {
            {Blocks.RED_CONCRETE, Blocks.RED_CARPET}, {Blocks.BLUE_CONCRETE, Blocks.BLUE_CARPET},
            {Blocks.WHITE_CONCRETE, Blocks.WHITE_CARPET}, {Blocks.BLACK_CONCRETE, Blocks.BLACK_CARPET},
            {Blocks.YELLOW_CONCRETE, Blocks.YELLOW_CARPET}, {Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_CARPET},
            {Blocks.GREEN_CONCRETE, Blocks.GREEN_CARPET}, {Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_CARPET},
            {Blocks.ORANGE_CONCRETE, Blocks.ORANGE_CARPET}, {Blocks.CYAN_CONCRETE, Blocks.CYAN_CARPET}};

    /** Cars parked along the outer lanes of every road segment except the avenue's. */
    static void park(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        // The road on this block's west edge (north-south) and north edge (east-west).
        for (int road = 0; road < 2; road++) {
            boolean northSouth = road == 0;
            if (northSouth && ox == 0) continue; // the avenue
            for (int lane : new int[] {0, 6}) {
                for (int slot = 0; slot < 3; slot++) {
                    int seed = CityGrid.hash(bx, bz, road, lane, slot, 11);
                    if (seed % 5 < 3) continue;
                    int along = 12 + slot * 6;
                    int x = northSouth ? ox + lane : ox + along, z = northSouth ? oz + along : oz + lane;
                    if (CityGrid.terminus(x, z) || !CityGrid.road(x, z)) continue;
                    final int fx = x, fz = z, fs = seed;
                    out.add(new Feature(x - 1, z - 1, x + (northSouth ? 2 : 6), z + (northSouth ? 6 : 2),
                            c -> car(c, fx, fz, northSouth ? Direction.SOUTH : Direction.EAST, northSouth ? Direction.EAST : Direction.SOUTH, PAINT[fs % PAINT.length])));
                }
            }
        }
    }

    /** Two wide and five long: a painted body, a glass cabin with a roof, and wheels on both sides. */
    static void car(CityCanvas c, int x, int z, Direction along, Direction across, Block[] paint) { car(c, x, z, along, across, paint, false); }

    static void car(CityCanvas c, int x, int z, Direction along, Direction across, Block[] paint, boolean onLot) {
        int y = CityGrid.STREET_Y;
        Block body = paint[0], top = paint[1];
        for (int w = 0; w < 2; w++)
            for (int u = 0; u < 5; u++) {
                int cx = x + along.getStepX() * u + across.getStepX() * w, cz = z + along.getStepZ() * u + across.getStepZ() * w;
                c.set(cx, y, cz, body);
                boolean cabin = u >= 1 && u <= 3;
                c.set(cx, y + 1, cz, cabin ? Blocks.GRAY_STAINED_GLASS.defaultBlockState() : top.defaultBlockState());
                if (cabin) c.set(cx, y + 2, cz, top);
                // Wheels: dark buttons on the sides, front and back.
                if (u == 1 || u == 3) {
                    Direction outward = w == 0 ? across.getOpposite() : across;
                    if (!onLot && !CityGrid.road(cx + outward.getStepX(), cz + outward.getStepZ())) continue;
                    c.set(cx + outward.getStepX(), y, cz + outward.getStepZ(), Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                            .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, outward));
                }
            }
    }
}
