package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One building on a parcel: a ground storey (shops, a lobby, a stoop or a blank wall), upper floors
 * four blocks apart, setbacks for towers, and a roof. Every facade has depth: piers stand proud of
 * glass set one block in, sills and lintels frame each bay, and a cornice overhangs the sidewalk.
 * A few windows on every floor are lit, as they would be at a quarter past six.
 */
final class Building {
    static final int GROUND = 6, FLOOR = 4, BASE = CityGrid.STREET_Y;

    enum Ground { SHOP, LOBBY, STOOP, PLAIN }
    enum Top { FLAT, WATER_TOWER, PENTHOUSE, SPIRE, BILLBOARD, CHIMNEYS, GARDEN, MAST }

    /** Everything that makes one style of building look like itself. */
    record Look(Block wall, Block band, Block sill, Block lintel, Block glass, Block litGlass, Block light,
                Block floor, Block roof, Block parapet, Block cornice, boolean recessed, int bay, int windowRows,
                double lit, Ground ground, Block groundPier, Block fascia, Block awning, Block awningStripe, Block door) {
        Look with(Ground other) {
            return new Look(wall, band, sill, lintel, glass, litGlass, light, floor, roof, parapet, cornice, recessed, bay,
                    windowRows, lit, other, groundPier, fascia, awning, awningStripe, door);
        }
    }

    final int x0, z0, x1, z1, floors, seed;
    final Look look;
    final int[] insets;
    final Top top;
    /** Which faces have a sidewalk in front of them: north, south, west, east. */
    final boolean[] street;
    final String[] shops;

    Building(int x0, int z0, int x1, int z1, int floors, Look look, int[] insets, Top top, boolean[] street, int seed, String... shops) {
        this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1;
        this.floors = floors; this.look = look; this.insets = insets; this.top = top;
        this.street = street; this.seed = seed; this.shops = shops;
    }

    int roofY() { return BASE + GROUND + FLOOR * floors; }
    int inset(int floor) { return floor <= 0 ? 0 : insets[Math.min(floor, insets.length - 1)]; }

    /** Setbacks: {@code tiers} pairs of (first floor, inset). */
    static int[] tiers(int floors, int... pairs) {
        int[] insets = new int[floors + 1];
        for (int k = 1; k <= floors; k++)
            for (int i = 0; i + 1 < pairs.length; i += 2) if (k >= pairs[i]) insets[k] = pairs[i + 1];
        return insets;
    }

    // ------------------------------------------------------------------
    // Faces
    // ------------------------------------------------------------------

    /** The four faces of a box: 0 north, 1 south, 2 west, 3 east. */
    static final Direction[] OUT = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    static int length(int face, int ax0, int az0, int ax1, int az1) { return face < 2 ? ax1 - ax0 + 1 : az1 - az0 + 1; }
    static int fx(int face, int u, int ax0, int ax1) { return face < 2 ? ax0 + u : face == 2 ? ax0 : ax1; }
    static int fz(int face, int u, int az0, int az1) { return face < 2 ? (face == 0 ? az0 : az1) : az0 + u; }

    /** Piers at both ends and evenly between, about {@code bay} cells of window apart. */
    static boolean[] piers(int length, int bay) {
        boolean[] pier = new boolean[Math.max(length, 1)];
        if (length <= 0) return pier;
        pier[0] = true;
        pier[length - 1] = true;
        int span = length - 1;
        int count = Math.max(1, Math.round(span / (float) (bay + 1)));
        for (int i = 1; i < count; i++) pier[Math.round(i * span / (float) count)] = true;
        return pier;
    }

    /** Index of the bay a cell belongs to, counting piers from the start of the face. */
    static int bayOf(boolean[] pier, int u) {
        int bay = 0;
        for (int i = 1; i <= u && i < pier.length; i++) if (pier[i]) bay++;
        return bay;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    void draw(CityCanvas c) {
        if (!c.touches(x0 - 2, z0 - 2, x1 + 2, z1 + 2)) return;
        c.fill(x0, BASE, z0, x1, BASE, z1, look.floor);
        ground(c);
        int previous = 0;
        for (int k = 1; k <= floors; k++) {
            int inset = inset(k), y = BASE + GROUND + FLOOR * (k - 1);
            if (inset > previous) terrace(c, previous, y);
            storey(c, k, inset, y);
            previous = inset;
        }
        roof(c, previous, roofY());
    }

    private void storey(CityCanvas c, int floor, int inset, int y) {
        int ax0 = x0 + inset, az0 = z0 + inset, ax1 = x1 - inset, az1 = z1 - inset;
        if (ax1 - ax0 < 2 || az1 - az0 < 2) return;
        c.fill(ax0 + 1, y, az0 + 1, ax1 - 1, y, az1 - 1, look.floor);
        for (int r = 0; r < FLOOR; r++) {
            int row = y + r;
            for (int face = 0; face < 4; face++) {
                int length = length(face, ax0, az0, ax1, az1);
                boolean[] pier = piers(length, look.bay);
                Direction out = OUT[face];
                for (int u = 1; u < length - 1; u++) {
                    int x = fx(face, u, ax0, ax1), z = fz(face, u, az0, az1);
                    if (pier[u]) { c.set(x, row, z, look.wall); continue; }
                    if (r == 0) c.set(x, row, z, look.sill);
                    else if (r <= look.windowRows) {
                        boolean lit = lit(face, bayOf(pier, u), floor);
                        Block glass = lit ? look.litGlass : look.glass;
                        if (look.recessed) {
                            c.set(x, row, z, Blocks.AIR);
                            c.set(x - out.getStepX(), row, z - out.getStepZ(), glass);
                        } else c.set(x, row, z, glass);
                    } else c.set(x, row, z, look.lintel);
                }
            }
            for (int[] corner : new int[][] {{ax0, az0}, {ax1, az0}, {ax0, az1}, {ax1, az1}})
                c.set(corner[0], row, corner[1], look.wall);
        }
        // Rooms with the lights on: a lamp in the ceiling two blocks in from each lit bay.
        for (int face = 0; face < 4; face++) {
            int length = length(face, ax0, az0, ax1, az1);
            boolean[] pier = piers(length, look.bay);
            Direction out = OUT[face];
            int start = 0;
            for (int u = 1; u < length; u++) {
                if (!pier[u]) continue;
                int bay = bayOf(pier, start + 1), middle = (start + u) / 2;
                if (u - start > 1 && lit(face, bay, floor)) {
                    int x = fx(face, middle, ax0, ax1) - out.getStepX() * 2, z = fz(face, middle, az0, az1) - out.getStepZ() * 2;
                    if (x > ax0 && x < ax1 && z > az0 && z < az1) c.set(x, y + FLOOR - 1, z, look.light);
                }
                start = u;
            }
        }
    }

    private boolean lit(int face, int bay, int floor) {
        return CityGrid.chance(seed, face * 131 + bay * 17 + floor * 7919) < look.lit;
    }

    /** Where a tower steps back: the lower floors' roof becomes a terrace behind a parapet. */
    private void terrace(CityCanvas c, int inset, int y) {
        int ax0 = x0 + inset, az0 = z0 + inset, ax1 = x1 - inset, az1 = z1 - inset;
        c.fill(ax0 + 1, y, az0 + 1, ax1 - 1, y, az1 - 1, look.roof);
        ring(c, ax0, az0, ax1, az1, y, look.band);
        ring(c, ax0, az0, ax1, az1, y + 1, look.parapet);
    }

    private static void ring(CityCanvas c, int ax0, int az0, int ax1, int az1, int y, Block block) {
        for (int x = ax0; x <= ax1; x++) { c.set(x, y, az0, block); c.set(x, y, az1, block); }
        for (int z = az0; z <= az1; z++) { c.set(ax0, y, z, block); c.set(ax1, y, z, block); }
    }

    private void roof(CityCanvas c, int inset, int y) {
        int ax0 = x0 + inset, az0 = z0 + inset, ax1 = x1 - inset, az1 = z1 - inset;
        c.fill(ax0 + 1, y, az0 + 1, ax1 - 1, y, az1 - 1, look.roof);
        ring(c, ax0, az0, ax1, az1, y, look.band);
        ring(c, ax0, az0, ax1, az1, y + 1, look.parapet);
        if (look.cornice != null) {
            // The cornice overhangs the street by a block.
            for (int x = ax0 - 1; x <= ax1 + 1; x++) {
                c.set(x, y, az0 - 1, CityCanvas.stairs(look.cornice, Direction.SOUTH, true));
                c.set(x, y, az1 + 1, CityCanvas.stairs(look.cornice, Direction.NORTH, true));
            }
            for (int z = az0; z <= az1; z++) {
                c.set(ax0 - 1, y, z, CityCanvas.stairs(look.cornice, Direction.EAST, true));
                c.set(ax1 + 1, y, z, CityCanvas.stairs(look.cornice, Direction.WEST, true));
            }
        }
        int w = ax1 - ax0 + 1, d = az1 - az0 + 1;
        int cx = (ax0 + ax1) / 2, cz = (az0 + az1) / 2;
        switch (top) {
            case WATER_TOWER -> {
                if (w >= 7 && d >= 7) waterTower(c, ax1 - 4, y + 1, az1 - 4);
                bulkhead(c, ax0 + 2, y + 1, az0 + 2);
            }
            case PENTHOUSE -> penthouse(c, ax0 + 3, az0 + 3, ax1 - 3, az1 - 3, y + 1);
            case SPIRE -> spire(c, ax0, az0, ax1, az1, y + 1);
            case BILLBOARD -> {
                billboard(c, ax0, az0, ax1, az1, y + 1);
                acUnits(c, ax0, az0, ax1, az1, y + 1);
            }
            case CHIMNEYS -> {
                for (int x = ax0 + 1; x < ax1; x += 4) {
                    c.fill(x, y + 1, az1 - 1, x, y + 2, az1 - 1, Blocks.BRICKS);
                    c.set(x, y + 3, az1 - 1, Blocks.BRICK_WALL);
                }
            }
            case GARDEN -> garden(c, ax0, az0, ax1, az1, y);
            case MAST -> {
                penthouse(c, cx - 2, cz - 2, cx + 2, cz + 2, y + 1);
                mast(c, cx, y + 6, cz, 10 + Math.floorMod(seed, 6));
            }
            case FLAT -> acUnits(c, ax0, az0, ax1, az1, y + 1);
        }
    }

    // ------------------------------------------------------------------
    // Street level
    // ------------------------------------------------------------------

    private void ground(CityCanvas c) {
        int top = BASE + GROUND - 1;
        for (int face = 0; face < 4; face++) {
            Ground kind = street[face] ? look.ground : Ground.PLAIN;
            int length = length(face, x0, z0, x1, z1);
            switch (kind) {
                case SHOP -> shopfront(c, face, length);
                case LOBBY -> lobby(c, face, length);
                case STOOP -> stoops(c, face, length);
                case PLAIN -> {
                    for (int u = 1; u < length - 1; u++)
                        c.fill(fx(face, u, x0, x1), BASE + 1, fz(face, u, z0, z1), fx(face, u, x0, x1), top, fz(face, u, z0, z1), look.wall);
                }
            }
        }
        for (int[] corner : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}})
            c.fill(corner[0], BASE + 1, corner[1], corner[0], top, corner[1], look.groundPier);
        // Lights inside, every few blocks under the first floor.
        for (int x = x0 + 2; x < x1 - 1; x += 4)
            for (int z = z0 + 2; z < z1 - 1; z += 4) c.set(x, top, z, look.ground == Ground.LOBBY ? Blocks.SEA_LANTERN : Blocks.OCHRE_FROGLIGHT);
    }

    private void shopfront(CityCanvas c, int face, int length) {
        boolean[] pier = piers(length, 4);
        Direction out = OUT[face];
        int ox = out.getStepX(), oz = out.getStepZ();
        int start = 0, shop = 0;
        for (int u = 1; u < length; u++) {
            if (!pier[u]) continue;
            int middle = (start + u) / 2;
            for (int v = start + 1; v < u; v++) {
                int x = fx(face, v, x0, x1), z = fz(face, v, z0, z1);
                boolean door = v == middle && (shop + face) % 2 == 0;
                if (door) {
                    c.door(x, BASE + 1, z, look.door, out);
                    c.set(x, BASE + 3, z, Blocks.GLASS_PANE);
                    c.set(x, BASE + 4, z, Blocks.GLASS_PANE);
                } else {
                    c.set(x, BASE + 1, z, look.groundPier);
                    c.fill(x, BASE + 2, z, x, BASE + 4, z, Blocks.GLASS_PANE);
                }
                c.set(x, BASE + 5, z, look.fascia);
                // A striped awning over the window, a block out over the sidewalk.
                c.set(x + ox, BASE + 4, z + oz, (v - start) % 2 == 0 ? look.awningStripe : look.awning);
                // Something on display just inside the glass.
                if (!door && CityGrid.chance(seed, face * 977 + v) < 0.35)
                    c.set(x - ox, BASE + 1, z - oz, display(face * 31 + v));
            }
            String name = shops.length == 0 ? "" : shops[Math.floorMod(seed + face * 7 + shop, shops.length)];
            if (!name.isEmpty() && u - start > 2) {
                int x = fx(face, middle, x0, x1) + ox, z = fz(face, middle, z0, z1) + oz;
                c.sign(x, BASE + 5, z, out, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, name);
            }
            for (int y = BASE + 1; y <= BASE + 5; y++) c.set(fx(face, u, x0, x1), y, fz(face, u, z0, z1), look.groundPier);
            start = u;
            shop++;
        }
    }

    private BlockState display(int salt) {
        return switch (Math.floorMod(seed + salt, 6)) {
            case 0 -> Blocks.POTTED_FERN.defaultBlockState();
            case 1 -> Blocks.BARREL.defaultBlockState();
            case 2 -> Blocks.BOOKSHELF.defaultBlockState();
            case 3 -> Blocks.POTTED_RED_TULIP.defaultBlockState();
            case 4 -> Blocks.CHISELED_BOOKSHELF.defaultBlockState();
            default -> Blocks.FLOWER_POT.defaultBlockState();
        };
    }

    private void lobby(CityCanvas c, int face, int length) {
        boolean[] pier = piers(length, 3);
        Direction out = OUT[face];
        int ox = out.getStepX(), oz = out.getStepZ();
        int middle = length / 2;
        for (int u = 1; u < length - 1; u++) {
            int x = fx(face, u, x0, x1), z = fz(face, u, z0, z1);
            if (pier[u]) { c.fill(x, BASE + 1, z, x, BASE + 5, z, look.groundPier); continue; }
            boolean entrance = Math.abs(u - middle) <= 1;
            if (entrance) {
                c.fill(x, BASE + 1, z, x, BASE + 2, z, Blocks.AIR);
                c.fill(x, BASE + 3, z, x, BASE + 4, z, Blocks.GLASS);
                // A canopy over the doors.
                c.set(x + ox, BASE + 4, z + oz, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, true));
                c.set(x + 2 * ox, BASE + 4, z + 2 * oz, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, true));
            } else c.fill(x, BASE + 1, z, x, BASE + 4, z, Blocks.GLASS);
            c.set(x, BASE + 5, z, look.fascia);
        }
    }

    /** Rowhouse fronts: steps up from the sidewalk to a raised door, railings either side. */
    private void stoops(CityCanvas c, int face, int length) {
        Direction out = OUT[face];
        int ox = out.getStepX(), oz = out.getStepZ();
        boolean[] pier = piers(length, 2);
        for (int u = 1; u < length - 1; u++) {
            int x = fx(face, u, x0, x1), z = fz(face, u, z0, z1);
            c.set(x, BASE + 1, z, look.groundPier);
            if (pier[u]) { c.fill(x, BASE + 2, z, x, BASE + 5, z, look.wall); continue; }
            boolean door = u == 2 || u == length - 3;
            if (door && length >= 6) {
                c.door(x, BASE + 2, z, look.door, out);
                c.set(x, BASE + 4, z, Blocks.GLASS_PANE);
                c.set(x - ox, BASE + 1, z - oz, look.floor);
                c.set(x + ox, BASE + 1, z + oz, look.groundPier);
                c.set(x + ox, BASE + 2, z + oz, CityCanvas.slab(Blocks.SMOOTH_SANDSTONE_SLAB, false));
                c.set(x + 2 * ox, BASE + 1, z + 2 * oz, CityCanvas.stairs(Blocks.SANDSTONE_STAIRS, out.getOpposite(), false));
                Direction side = out.getClockWise();
                for (int s : new int[] {-1, 1}) {
                    c.set(x + side.getStepX() * s + ox, BASE + 2, z + side.getStepZ() * s + oz, Blocks.IRON_BARS);
                    c.set(x + side.getStepX() * s + 2 * ox, BASE + 1, z + side.getStepZ() * s + 2 * oz, Blocks.IRON_BARS);
                }
            } else {
                c.fill(x, BASE + 2, z, x, BASE + 3, z, look.recessed ? Blocks.AIR : look.glass);
                if (look.recessed) c.fill(x - ox, BASE + 2, z - oz, x - ox, BASE + 3, z - oz, look.glass);
                c.set(x, BASE + 4, z, look.lintel);
            }
            c.set(x, BASE + 5, z, look.band);
        }
    }

    // ------------------------------------------------------------------
    // On the roof
    // ------------------------------------------------------------------

    static void waterTower(CityCanvas c, int x, int y, int z) {
        for (int dx = 0; dx <= 2; dx += 2) for (int dz = 0; dz <= 2; dz += 2) c.fill(x + dx, y, z + dz, x + dx, y + 2, z + dz, Blocks.SPRUCE_FENCE);
        c.fill(x, y + 3, z, x + 2, y + 3, z + 2, CityCanvas.slab(Blocks.SPRUCE_SLAB, true));
        for (int h = 4; h <= 6; h++)
            for (int dx = 0; dx <= 2; dx++) for (int dz = 0; dz <= 2; dz++)
                if (dx != 1 || dz != 1) c.set(x + dx, y + h, z + dz, h == 5 ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS);
        c.set(x + 1, y + 7, z + 1, CityCanvas.slab(Blocks.SPRUCE_SLAB, false));
        c.set(x + 1, y + 7, z, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        c.set(x + 1, y + 7, z + 2, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        c.set(x, y + 7, z + 1, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        c.set(x + 2, y + 7, z + 1, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
    }

    static void bulkhead(CityCanvas c, int x, int y, int z) {
        c.fill(x, y, z, x + 2, y + 2, z + 2, Blocks.BRICKS);
        c.fill(x, y + 3, z, x + 2, y + 3, z + 2, CityCanvas.slab(Blocks.STONE_BRICK_SLAB, false));
        c.door(x + 1, y, z + 2, Blocks.IRON_DOOR, Direction.SOUTH);
    }

    static void penthouse(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        if (ax1 - ax0 < 2 || az1 - az0 < 2) return;
        c.fill(ax0, y, az0, ax1, y + 3, az1, Blocks.GRAY_CONCRETE);
        for (int x = ax0 + 1; x < ax1; x++) { c.set(x, y + 2, az0, Blocks.IRON_BARS); c.set(x, y + 2, az1, Blocks.IRON_BARS); }
        for (int z = az0 + 1; z < az1; z++) { c.set(ax0, y + 2, z, Blocks.IRON_BARS); c.set(ax1, y + 2, z, Blocks.IRON_BARS); }
        c.fill(ax0, y + 4, az0, ax1, y + 4, az1, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
    }

    static void mast(CityCanvas c, int x, int y, int z, int height) {
        c.fill(x, y, z, x, y + height - 2, z, Blocks.IRON_BARS);
        c.set(x, y + height - 1, z, Blocks.LIGHTNING_ROD);
        c.set(x, y + height, z, Blocks.END_ROD);
        c.set(x, y + height / 2, z, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
    }

    /** An art deco crown: three narrowing steps, then a spire with a light at the tip. */
    private void spire(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        int step = 0;
        for (int tier = 1; tier <= 3; tier++) {
            int i = tier * 2;
            if (ax1 - ax0 - 2 * i < 2 || az1 - az0 - 2 * i < 2) break;
            int h = 4 - tier;
            for (int dy = 0; dy < h; dy++) {
                int yy = y + step + dy;
                for (int x = ax0 + i; x <= ax1 - i; x++)
                    for (int z = az0 + i; z <= az1 - i; z++) {
                        boolean edge = x == ax0 + i || x == ax1 - i || z == az0 + i || z == az1 - i;
                        boolean fin = edge && (x + z) % 2 == 0;
                        c.set(x, yy, z, edge ? (fin ? look.wall : look.band) : look.roof);
                    }
            }
            step += h;
        }
        int cx = (ax0 + ax1) / 2, cz = (az0 + az1) / 2;
        c.set(cx, y + step, cz, Blocks.CHISELED_SANDSTONE);
        c.fill(cx, y + step + 1, cz, cx, y + step + 7, cz, Blocks.LIGHTNING_ROD);
        c.set(cx, y + step + 8, cz, Blocks.END_ROD);
    }

    private void billboard(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        int width = Math.min(7, ax1 - ax0 - 1);
        if (width < 4) return;
        int x = (ax0 + ax1) / 2 - width / 2, z = az1 - 2;
        for (int dx = 1; dx < width; dx += 3) c.fill(x + dx, y, z, x + dx, y + 2, z, Blocks.SPRUCE_FENCE);
        int color = Math.floorMod(seed, 4);
        Block face = color == 0 ? Blocks.RED_CONCRETE : color == 1 ? Blocks.BLUE_CONCRETE : color == 2 ? Blocks.ORANGE_CONCRETE : Blocks.GREEN_CONCRETE;
        for (int dx = 0; dx < width; dx++)
            for (int dy = 3; dy <= 5; dy++) {
                boolean frame = dx == 0 || dx == width - 1 || dy == 3 || dy == 5;
                c.set(x + dx, y + dy, z, frame ? Blocks.WHITE_CONCRETE : face);
            }
        String[][] ads = {{"DRINK", "ODESSA COLA"}, {"FLY", "TEXAS AIR"}, {"PRIMATECH", "PAPER CO."}, {"NBC 7", "NEWS AT 6"}, {"GO", "SPURS!"}};
        String[] ad = ads[Math.floorMod(seed / 4, ads.length)];
        c.sign(x + width / 2, y + 4, z - 1, Direction.NORTH, Blocks.SPRUCE_WALL_SIGN, DyeColor.WHITE, true, ad);
        for (int dx = 1; dx < width; dx += 2) c.set(x + dx, y + 2, z - 1, CityCanvas.hangingLantern(Blocks.LANTERN));
    }

    private void acUnits(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        for (int i = 0; i < 3; i++) {
            int x = ax0 + 2 + Math.floorMod(seed / (i + 3), Math.max(1, ax1 - ax0 - 3));
            int z = az0 + 2 + Math.floorMod(seed / (i + 7), Math.max(1, az1 - az0 - 3));
            c.set(x, y, z, Blocks.SMOOTH_STONE);
            c.set(x, y + 1, z, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, false, false));
        }
    }

    private void garden(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        for (int x = ax0 + 1; x < ax1; x++)
            for (int z = az0 + 1; z < az1; z++) {
                c.set(x, y, z, (x + z) % 5 == 0 ? Blocks.GRAVEL : Blocks.GRASS_BLOCK);
                int pick = CityGrid.hash(seed, x, z) % 9;
                if (pick == 0) c.set(x, y + 1, z, Blocks.AZALEA);
                else if (pick == 1) c.set(x, y + 1, z, Blocks.FLOWERING_AZALEA);
                else if (pick == 2) c.set(x, y + 1, z, Blocks.SHORT_GRASS);
            }
    }
}
