package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One building on a parcel: a ground storey (shops, a lobby, raised row-house fronts or a blank wall), upper floors four
 * blocks apart, setbacks for towers, and a roof.
 * <p>
 * Every face is built in two planes. The outer plane (depth 0) carries the piers, the course under each window and the
 * lintel over it; the glass sits a block in (depth 1), between piers that are two blocks deep, so every pane is held at
 * both ends and every window has a reveal and a sill. Behind the glass is a room three blocks deep with a ceiling light
 * where the lights are on, closed off by a partition, so a window shows a room rather than a hollow shell. Anything
 * that leaves the wall (awnings, cornices, fire escapes, stoops, canopies) is drawn against the wall it hangs from, and
 * only on faces that look onto a street, so nothing reaches into the building next door.
 */
final class Building {
    static final int GROUND = 6, FLOOR = 4, BASE = CityGrid.STREET_Y;
    /** How deep the rooms behind the windows are before the partition. */
    private static final int ROOM = 4;

    enum Ground { SHOP, LOBBY, STOOP, PLAIN }
    /**
     * What stands on the roof. {@code CROWN}: a deco crown of narrowing lit tiers and a mooring mast. {@code ARCHES}: a crown
     * of terraced arches with sunburst windows and a long needle. {@code PYRAMID}: a steep copper roof with dormers and a
     * lantern. {@code LANTERN}: a lit glass crown on a flat top.
     */
    enum Top { FLAT, WATER_TOWER, PENTHOUSE, SPIRE, BILLBOARD, CHIMNEYS, GARDEN, MAST, CROWN, ARCHES, PYRAMID, LANTERN }

    /**
     * Everything that makes one style of building look like itself.
     *
     * @param wall      piers and walls
     * @param spandrel  the course under each window (often the wall itself)
     * @param trim      lintels, the band at each roof and setback
     * @param sill      stairs for the windowsill (laid upside down under each window), or null
     * @param glass     the glass of a dark window (a pane, or a full block on a curtain wall)
     * @param litGlass  the glass of a lit one
     * @param curtain   a glass curtain wall: full glass flush between mullions, no reveals
     * @param light     a ceiling light in a lit room
     * @param floor     floors inside
     * @param roof      the roof deck
     * @param parapet   the parapet round the roof
     * @param cornice   stairs for a cornice over the street, or null
     * @param bay       window cells between piers
     * @param lit       the share of rooms with the lights on
     * @param ground    the street storey on street faces
     * @param base      the street storey's piers and bulkheads
     * @param fascia    the sign band over the shops
     * @param awningA   stairs for the awnings' stripes
     * @param awningB   stairs for the stripes between
     * @param door      street doors
     * @param core      the partition behind the rooms
     * @param fireEscape iron fire escapes on one street face
     */
    record Look(Block wall, Block spandrel, Block trim, Block sill, Block glass, Block litGlass, boolean curtain, Block light,
                Block floor, Block roof, Block parapet, Block cornice, int bay, double lit, Ground ground, Block base,
                Block fascia, Block awningA, Block awningB, Block door, Block core, boolean fireEscape) {
        Look with(Ground other) {
            return new Look(wall, spandrel, trim, sill, glass, litGlass, curtain, light, floor, roof, parapet, cornice, bay, lit,
                    other, base, fascia, awningA, awningB, door, core, fireEscape);
        }
    }

    final int x0, z0, x1, z1, floors, seed;
    /** The lobby's doors stand open (Primatech's, on the Last Day, so everyone can get in). */
    boolean doorsOpen;
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

    /** A cell on one face of a box, {@code depth} blocks in from the face (negative: out over the street). */
    private record Face(int face, int ax0, int az0, int ax1, int az1) {
        Direction out() { return OUT[face]; }
        int length() { return Building.length(face, ax0, az0, ax1, az1); }
        int x(int u, int depth) { return fx(face, u, ax0, ax1) - OUT[face].getStepX() * depth; }
        int z(int u, int depth) { return fz(face, u, az0, az1) - OUT[face].getStepZ() * depth; }
        void set(CityCanvas c, int u, int depth, int y, BlockState state) { c.set(x(u, depth), y, z(u, depth), state); }
        void set(CityCanvas c, int u, int depth, int y, Block block) { c.set(x(u, depth), y, z(u, depth), block); }
        /** The way along the face: toward larger {@code u} (east on the north and south faces, south on the others), or back. */
        Direction along(boolean forward) {
            Direction d = face < 2 ? Direction.EAST : Direction.SOUTH;
            return forward ? d : d.getOpposite();
        }
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
        if (look.fireEscape) fireEscape(c);
    }

    private void storey(CityCanvas c, int floor, int inset, int y) {
        int ax0 = x0 + inset, az0 = z0 + inset, ax1 = x1 - inset, az1 = z1 - inset;
        if (ax1 - ax0 < 2 || az1 - az0 < 2) return;
        c.fill(ax0 + 1, y, az0 + 1, ax1 - 1, y, az1 - 1, look.floor);
        boolean partition = Math.min(ax1 - ax0, az1 - az0) >= 2 * ROOM + 3;
        if (partition) {
            // The rooms end at a partition; what lies behind it is never seen.
            int px0 = ax0 + ROOM + 1, pz0 = az0 + ROOM + 1, px1 = ax1 - ROOM - 1, pz1 = az1 - ROOM - 1;
            for (int r = 1; r < FLOOR; r++) ring(c, px0, pz0, px1, pz1, y + r, look.core);
        }
        for (int face = 0; face < 4; face++) {
            Face f = new Face(face, ax0, az0, ax1, az1);
            int length = f.length();
            boolean[] pier = piers(length, look.bay);
            if (length > 6) { pier[1] = true; pier[length - 2] = true; }
            for (int u = 1; u < length - 1; u++) {
                boolean lit = lit(face, bayOf(pier, u), floor);
                if (pier[u]) {
                    for (int r = 0; r < FLOOR; r++) f.set(c, u, 0, y + r, look.wall);
                    for (int r = 1; r < FLOOR; r++) f.set(c, u, 1, y + r, look.wall);
                    continue;
                }
                if (look.curtain) {
                    f.set(c, u, 0, y, look.spandrel);
                    for (int r = 1; r < FLOOR; r++) f.set(c, u, 0, y + r, lit ? look.litGlass : look.glass);
                    continue;
                }
                // A reveal a block deep: a sill (an upside-down stair, its lip casting a shadow line) or the plain course
                // below, the glass set back, the lintel over it.
                f.set(c, u, 0, y, look.sill != null ? CityCanvas.stairs(look.sill, f.out().getOpposite(), true) : look.spandrel.defaultBlockState());
                f.set(c, u, 0, y + 1, Blocks.AIR);
                f.set(c, u, 0, y + 2, Blocks.AIR);
                f.set(c, u, 0, y + 3, look.trim);
                f.set(c, u, 1, y + 1, lit ? look.litGlass : look.glass);
                f.set(c, u, 1, y + 2, lit ? look.litGlass : look.glass);
                f.set(c, u, 1, y + 3, look.wall);
            }
            for (int r = 0; r < FLOOR; r++) f.set(c, 0, 0, y + r, look.wall);
            // Rooms with the lights on: a light in the ceiling, in the middle of the room behind each lit bay.
            int start = 0;
            for (int u = 1; u < length; u++) {
                if (!pier[u]) continue;
                int middle = (start + u) / 2;
                if (u - start > 1 && lit(face, bayOf(pier, start + 1), floor)) {
                    int x = f.x(middle, 2 + ROOM / 2 - 1), z = f.z(middle, 2 + ROOM / 2 - 1);
                    if (x > ax0 + 1 && x < ax1 - 1 && z > az0 + 1 && z < az1 - 1) c.set(x, y + FLOOR - 1, z, look.light);
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
        ring(c, ax0, az0, ax1, az1, y, look.trim);
        ring(c, ax0, az0, ax1, az1, y + 1, look.parapet);
    }

    static void ring(CityCanvas c, int ax0, int az0, int ax1, int az1, int y, Block block) {
        for (int x = ax0; x <= ax1; x++) { c.set(x, y, az0, block); c.set(x, y, az1, block); }
        for (int z = az0; z <= az1; z++) { c.set(ax0, y, z, block); c.set(ax1, y, z, block); }
    }

    private void roof(CityCanvas c, int inset, int y) {
        int ax0 = x0 + inset, az0 = z0 + inset, ax1 = x1 - inset, az1 = z1 - inset;
        c.fill(ax0 + 1, y, az0 + 1, ax1 - 1, y, az1 - 1, look.roof);
        ring(c, ax0, az0, ax1, az1, y, look.trim);
        ring(c, ax0, az0, ax1, az1, y + 1, look.parapet);
        if (look.cornice != null && inset == 0) {
            // The cornice overhangs the street by a block, only over the sidewalk: never into a neighbour.
            for (int face = 0; face < 4; face++) {
                if (!street[face]) continue;
                Face f = new Face(face, ax0, az0, ax1, az1);
                Direction out = f.out();
                for (int u = 0; u < f.length(); u++) f.set(c, u, -1, y, CityCanvas.stairs(look.cornice, out.getOpposite(), true));
            }
        }
        int w = ax1 - ax0 + 1, d = az1 - az0 + 1;
        int cx = (ax0 + ax1) / 2, cz = (az0 + az1) / 2;
        switch (top) {
            case WATER_TOWER -> {
                if (w >= 9 && d >= 9) waterTower(c, ax1 - 4, y + 1, az1 - 4);
                bulkhead(c, ax0 + 2, y + 1, az0 + 2);
                vents(c, ax0, az0, ax1, az1, y + 1);
            }
            case PENTHOUSE -> penthouse(c, ax0 + 3, az0 + 3, ax1 - 3, az1 - 3, y + 1);
            case SPIRE -> spire(c, ax0, az0, ax1, az1, y + 1);
            case BILLBOARD -> {
                billboard(c, ax0, az0, ax1, az1, y + 1);
                bulkhead(c, ax0 + 2, y + 1, az0 + 2);
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
                vents(c, ax0, az0, ax1, az1, y + 1);
            }
            case FLAT -> {
                bulkhead(c, ax0 + 2, y + 1, az0 + 2);
                vents(c, ax0, az0, ax1, az1, y + 1);
            }
            case CROWN -> crown(c, ax0, az0, ax1, az1, y + 1);
            case ARCHES -> arches(c, ax0, az0, ax1, az1, y + 1);
            case PYRAMID -> pyramid(c, ax0, az0, ax1, az1, y + 1);
            case LANTERN -> lantern(c, ax0, az0, ax1, az1, y + 1);
        }
    }

    // ------------------------------------------------------------------
    // Crowns for the tallest towers
    // ------------------------------------------------------------------

    /**
     * A deco crown: three narrowing tiers, each a storey of tall lit slots between piers, then an octagonal drum and a
     * mooring mast with a beacon. Reads from across the city at dusk.
     */
    private void crown(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        int base = y;
        for (int tier = 1; tier <= 3; tier++) {
            int i = tier * 2 - 1;
            int x0 = ax0 + i, z0 = az0 + i, x1 = ax1 - i, z1 = az1 - i;
            if (x1 - x0 < 3 || z1 - z0 < 3) break;
            int h = 6 - tier;
            for (int dy = 0; dy < h; dy++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) {
                        boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                        boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                        if (!edge) { c.set(x, base + dy, z, dy == 0 ? look.roof : Blocks.AIR); continue; }
                        boolean slot = !corner && (x + z) % 2 == 0 && dy > 0 && dy < h - 1;
                        c.set(x, base + dy, z, slot ? Blocks.GLOWSTONE : dy == h - 1 ? look.trim : look.wall);
                    }
            // The tier's roof, set back above it.
            base += h;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) c.set(x, base, z, look.trim);
        }
        int cx = (ax0 + ax1) / 2, cz = (az0 + az1) / 2;
        // The drum: a short ring of glass round a lit core, then the mast.
        for (int dy = 1; dy <= 3; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    c.set(cx + dx, base + dy, cz + dz, dx == 0 && dz == 0 ? Blocks.SEA_LANTERN : Math.abs(dx) + Math.abs(dz) == 2 ? look.wall
                            : Blocks.WHITE_STAINED_GLASS);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.set(cx + dx, base + 4, cz + dz, look.trim);
        int top = Math.min(base + 5 + 14, 252);
        c.fill(cx, base + 5, cz, cx, top - 2, cz, Blocks.IRON_BARS);
        c.set(cx, top - 1, cz, Blocks.LIGHTNING_ROD);
        c.set(cx, top, cz, Blocks.END_ROD);
        c.set(cx, base + 9, cz, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
    }

    /**
     * A crown of terraced arches: on each of four narrowing tiers, every face is an arch of stairs over a fan of lit
     * triangular windows, in bright metal. A needle rises from the top.
     */
    private void arches(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        Block metal = Blocks.IRON_BLOCK;
        Block arch = Blocks.SMOOTH_QUARTZ_STAIRS;
        int base = y;
        for (int tier = 0; tier < 4; tier++) {
            int i = tier * 2;
            int x0 = ax0 + i, z0 = az0 + i, x1 = ax1 - i, z1 = az1 - i;
            if (x1 - x0 < 4 || z1 - z0 < 4) break;
            int h = 5;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) c.set(x, base, z, look.roof);
            // A solid core inside the arches, so the next tier stands on something.
            c.fill(x0 + 2, base + 1, z0 + 2, x1 - 2, base + h, z1 - 2, look.roof);
            for (int face = 0; face < 4; face++) {
                Face f = new Face(face, x0, z0, x1, z1);
                int length = f.length(), mid = (length - 1) / 2;
                Direction out = f.out();
                for (int u = 0; u < length; u++) {
                    int rise = Math.min(u, length - 1 - u);     // how far up the arch has climbed at this cell
                    int springing = Math.min(h - 1, rise);
                    for (int dy = 0; dy < h; dy++) {
                        int yy = base + 1 + dy;
                        if (u == 0 || u == length - 1) { f.set(c, u, 0, yy, metal); continue; }
                        if (dy < springing) {
                            // Inside the arch: lit sunburst panes, ribs of metal on alternate cells.
                            f.set(c, u, 0, yy, (u + dy) % 2 == 0 ? Blocks.WHITE_STAINED_GLASS : metal);
                            f.set(c, u, 1, yy, Blocks.GLOWSTONE);
                        } else if (dy == springing) {
                            // The arch's edge: stairs climbing toward the middle, then a level crown of metal.
                            boolean level = rise >= h - 1 || (u == mid && length % 2 == 1);
                            Direction up = u < mid || (u == mid && length % 2 == 0) ? f.along(true) : f.along(false);
                            f.set(c, u, 0, yy, level ? metal.defaultBlockState() : CityCanvas.stairs(arch, up, false));
                        }
                    }
                }
            }
            base += h;
        }
        int cx = (ax0 + ax1) / 2, cz = (az0 + az1) / 2;
        c.fill(cx - 1, base, cz - 1, cx + 1, base, cz + 1, metal);
        int top = Math.min(base + 20, 250);
        c.fill(cx, base + 1, cz, cx, top - 1, cz, Blocks.LIGHTNING_ROD);
        c.set(cx, top, cz, Blocks.END_ROD);
    }

    /** A steep copper hip roof, two up for each one in, and a small lantern and finial at the peak. */
    private void pyramid(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        Block copper = Blocks.WAXED_OXIDIZED_CUT_COPPER;
        Block step = Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS;
        int layer = 0;
        while (ax1 - ax0 - 2 * layer >= 0 && az1 - az0 - 2 * layer >= 0) {
            int x0 = ax0 + layer, z0 = az0 + layer, x1 = ax1 - layer, z1 = az1 - layer;
            for (int h = 0; h < 2; h++) {
                int yy = y + layer * 2 + h;
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) {
                        boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                        if (h == 1 && edge) {
                            // The upper half of each course is a stair, uphill toward the middle; the game turns the corners.
                            Direction up = z == z0 ? Direction.SOUTH : z == z1 ? Direction.NORTH : x == x0 ? Direction.EAST : Direction.WEST;
                            c.set(x, yy, z, x1 - x0 > 1 && z1 - z0 > 1 ? CityCanvas.stairs(step, up, false) : copper.defaultBlockState());
                        } else c.set(x, yy, z, copper);
                    }
            }
            layer++;
            if (layer > 12) break;
        }
        int cx = (ax0 + ax1) / 2, cz = (az0 + az1) / 2, peak = y + layer * 2;
        c.set(cx, peak, cz, Blocks.LANTERN);
        c.fill(cx, peak + 1, cz, cx, peak + 4, cz, Blocks.LIGHTNING_ROD);
    }

    /** A lit glass crown: a box of pale glass round a bright core, with a slim mast. */
    private void lantern(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        int x0 = ax0 + 2, z0 = az0 + 2, x1 = ax1 - 2, z1 = az1 - 2;
        if (x1 - x0 < 2 || z1 - z0 < 2) { vents(c, ax0, az0, ax1, az1, y); return; }
        for (int dy = 0; dy < 6; dy++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    if (dy == 0) c.set(x, y, z, look.roof);
                    else if (edge) c.set(x, y + dy, z, corner || dy == 5 ? look.trim : Blocks.WHITE_STAINED_GLASS);
                    else c.set(x, y + dy, z, dy == 1 && (x + z) % 3 == 0 ? Blocks.SEA_LANTERN : Blocks.AIR);
                }
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) c.set(x, y + 6, z, look.trim);
        mast(c, (x0 + x1) / 2, y + 7, (z0 + z1) / 2, 8);
    }

    // ------------------------------------------------------------------
    // Street level
    // ------------------------------------------------------------------

    private void ground(CityCanvas c) {
        int top = BASE + GROUND - 1;

        for (int face = 0; face < 4; face++) {
            Ground kind = street[face] ? look.ground : Ground.PLAIN;
            Face f = new Face(face, x0, z0, x1, z1);
            switch (kind) {
                case SHOP -> shopfront(c, f);
                case LOBBY -> lobby(c, f);
                case STOOP -> stoops(c, f);
                case PLAIN -> {
                    for (int u = 1; u < f.length() - 1; u++) for (int y = BASE + 1; y <= top; y++) f.set(c, u, 0, y, look.wall);
                }
            }
        }
        for (int[] corner : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}})
            c.fill(corner[0], BASE + 1, corner[1], corner[0], top, corner[1], look.base);
        // Lights inside, every few blocks, under the first floor.
        Block light = look.ground == Ground.LOBBY ? Blocks.SEA_LANTERN : Blocks.OCHRE_FROGLIGHT;
        for (int x = x0 + 2; x < x1 - 1; x += 4)
            for (int z = z0 + 2; z < z1 - 1; z += 4) c.set(x, top, z, light);
    }

    private void shopfront(CityCanvas c, Face f) {
        int length = f.length();
        boolean[] pier = piers(length, 4);
        Direction out = f.out();
        int start = 0, shop = 0;
        for (int u = 1; u < length; u++) {
            if (!pier[u]) continue;
            int middle = (start + u) / 2;
            boolean hasDoor = (shop + f.face) % 2 == 0 || u - start > 4;
            for (int v = start + 1; v < u; v++) {
                boolean door = hasDoor && v == middle;
                if (door) {
                    c.door(f.x(v, 0), BASE + 1, f.z(v, 0), look.door, out, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT,
                            doorsOpen);
                    f.set(c, v, 0, BASE + 3, Blocks.GLASS_PANE);
                    f.set(c, v, 1, BASE + 1, Blocks.AIR);
                    f.set(c, v, 1, BASE + 2, Blocks.AIR);
                } else {
                    f.set(c, v, 0, BASE + 1, look.base);
                    f.set(c, v, 0, BASE + 2, Blocks.GLASS_PANE);
                    f.set(c, v, 0, BASE + 3, Blocks.GLASS_PANE);
                    // Something on display just inside the glass, standing on the sill.
                    if (CityGrid.chance(seed, f.face * 977 + v) < 0.55) display(c, f, v);
                }
                f.set(c, v, 0, BASE + 4, look.fascia);
                f.set(c, v, 0, BASE + 5, look.fascia);
                // A striped awning over the window, hung from the fascia.
                Block stripe = (v - start) % 2 == 0 ? look.awningB : look.awningA;
                f.set(c, v, -1, BASE + 4, CityCanvas.stairs(stripe, out.getOpposite(), false));
            }
            String name = shops.length == 0 ? "" : shops[Math.floorMod(seed + f.face * 7 + shop, shops.length)];
            if (!name.isEmpty() && u - start > 2)
                c.sign(f.x(middle, -1), BASE + 5, f.z(middle, -1), out, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, name);
            for (int y = BASE + 1; y <= BASE + 5; y++) {
                f.set(c, u, 0, y, look.base);
                f.set(c, u, 1, y, look.base);
            }
            start = u;
            shop++;
        }
    }

    /** A display shelf behind a shop window: a stand with something on it. */
    private void display(CityCanvas c, Face f, int v) {
        int pick = Math.floorMod(seed + f.face * 31 + v, 7);
        BlockState stand = switch (pick) {
            case 0, 1 -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case 2 -> Blocks.BARREL.defaultBlockState();
            case 3 -> Blocks.BOOKSHELF.defaultBlockState();
            case 4 -> Blocks.CHISELED_BOOKSHELF.defaultBlockState();
            default -> Blocks.WHITE_CONCRETE.defaultBlockState();
        };
        f.set(c, v, 1, BASE + 1, stand);
        BlockState on = switch (Math.floorMod(seed + v, 6)) {
            case 0 -> Blocks.POTTED_FERN.defaultBlockState();
            case 1 -> Blocks.POTTED_RED_TULIP.defaultBlockState();
            case 2 -> Blocks.DECORATED_POT.defaultBlockState();
            case 3 -> Blocks.POTTED_AZALEA.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
        f.set(c, v, 1, BASE + 2, on);
    }

    private void lobby(CityCanvas c, Face f) {
        int length = f.length();
        boolean[] pier = piers(length, 3);
        Direction out = f.out();
        int middle = length / 2;
        for (int u = 1; u < length - 1; u++) {
            if (pier[u] && u != middle && u != middle - 1) {
                for (int y = BASE + 1; y <= BASE + 5; y++) f.set(c, u, 0, y, look.base);
                continue;
            }
            boolean entrance = u == middle || u == middle - 1;
            if (entrance) {
                if (u == middle - 1) c.doubleDoor(f.x(u, 0), BASE + 1, f.z(u, 0), look.door, out, f.along(true), doorsOpen);
                f.set(c, u, 0, BASE + 3, Blocks.GLASS);
                f.set(c, u, 0, BASE + 4, Blocks.GLASS);
                f.set(c, u, 1, BASE + 1, Blocks.AIR);
                f.set(c, u, 1, BASE + 2, Blocks.AIR);
            } else for (int y = BASE + 1; y <= BASE + 4; y++) f.set(c, u, 0, y, Blocks.GLASS);
            f.set(c, u, 0, BASE + 5, look.fascia);
        }
        // A canopy over the doors, against the glass, two blocks deep.
        for (int u = middle - 2; u <= middle + 1; u++)
            for (int d = -2; d <= -1; d++) f.set(c, u, d, BASE + 4, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, true));
    }

    /**
     * Row-house fronts: a door with a transom over it, up one stone step set into the sidewalk (a stoop that climbed out
     * over a three-wide sidewalk blocked it and stood half a block above the paving), a stone base course, and tall
     * parlour windows between piers.
     */
    private void stoops(CityCanvas c, Face f) {
        int length = f.length();
        Direction out = f.out();
        boolean[] pier = piers(length, 2);
        for (int u = 1; u < length - 1; u++) {
            f.set(c, u, 0, BASE + 1, look.base);
            boolean door = u == 2 || u == length - 3 && length >= 8;
            if (door) {
                c.door(f.x(u, 0), BASE + 1, f.z(u, 0), look.door, out);
                f.set(c, u, 0, BASE + 3, Blocks.GLASS_PANE);
                f.set(c, u, 0, BASE + 4, look.trim);
                f.set(c, u, 1, BASE + 1, Blocks.AIR);
                f.set(c, u, 1, BASE + 2, Blocks.AIR);
                // The step: a stair in the sidewalk's line, its tall side against the door.
                f.set(c, u, -1, BASE, CityCanvas.stairs(Blocks.STONE_BRICK_STAIRS, out.getOpposite(), false));
                continue;
            }
            if (pier[u]) { for (int y = BASE + 2; y <= BASE + 5; y++) { f.set(c, u, 0, y, look.wall); f.set(c, u, 1, y, look.wall); } continue; }
            // A tall parlour window in the face of the wall (held by the corner, a pier or the next pane), a lintel over it.
            for (int y = BASE + 2; y <= BASE + 4; y++) f.set(c, u, 0, y, look.glass);
            f.set(c, u, 0, BASE + 5, look.trim);
            f.set(c, u, 1, BASE + 5, look.wall);
        }
    }

    /**
     * Iron fire escapes up one street face of a walk-up: a landing at every floor in front of a window, a rail on its
     * outer edge, and posts at both ends that run up to the landing above.
     */
    private void fireEscape(CityCanvas c) {
        int face = street[2] ? 2 : street[3] ? 3 : street[0] ? 0 : street[1] ? 1 : -1;
        if (face < 0 || floors < 2) return;
        Face f = new Face(face, x0, z0, x1, z1);
        boolean[] pier = piers(f.length(), look.bay);
        // The first bay at least two cells wide, counted between its two piers.
        int a = -1, b = -1;
        for (int u = 1, start = 0; u < f.length(); u++) {
            if (!pier[u]) continue;
            if (u - start >= 3 && start > 0) { a = start; b = u; break; }
            start = u;
        }
        if (a < 0) return;
        Direction out = f.out();
        for (int k = 1; k <= floors; k++) {
            if (inset(k) > 0) break;
            int y = BASE + GROUND + FLOOR * (k - 1);
            for (int u = a; u <= b; u++) {
                f.set(c, u, -1, y, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, out, true, false));
                if (u > a && u < b) f.set(c, u, -1, y + 1, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, out.getOpposite(), false, true));
            }
            if (k < floors && inset(k + 1) == 0)
                for (int u : new int[] {a, b}) for (int r = 1; r < FLOOR; r++) f.set(c, u, -1, y + r, Blocks.IRON_BARS);
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
                c.set(x + dx, y + h, z + dz, dx == 1 && dz == 1 ? Blocks.SPRUCE_PLANKS : h == 5 ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS);
        c.set(x + 1, y + 7, z + 1, CityCanvas.slab(Blocks.SPRUCE_SLAB, false));
        c.set(x + 1, y + 7, z, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        c.set(x + 1, y + 7, z + 2, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        c.set(x, y + 7, z + 1, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        c.set(x + 2, y + 7, z + 1, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
    }

    /** The head of the stair onto the roof: a brick hut with a door in its north side and room to stand inside. */
    static void bulkhead(CityCanvas c, int x, int y, int z) {
        c.fill(x, y, z, x + 2, y + 2, z + 2, Blocks.BRICKS);
        c.fill(x + 1, y, z + 1, x + 1, y + 1, z + 1, Blocks.AIR);
        c.fill(x, y + 3, z, x + 2, y + 3, z + 2, CityCanvas.slab(Blocks.STONE_BRICK_SLAB, false));
        c.door(x + 1, y, z, Blocks.IRON_DOOR, Direction.NORTH);
    }

    static void penthouse(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        if (ax1 - ax0 < 2 || az1 - az0 < 2) return;
        c.fill(ax0, y, az0, ax1, y + 3, az1, Blocks.GRAY_CONCRETE);
        // Louvres on every side: iron trapdoors set into the wall.
        for (int x = ax0 + 1; x < ax1; x += 2) {
            c.set(x, y + 2, az0, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, false, true));
            c.set(x, y + 2, az1, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.SOUTH, false, true));
        }
        for (int z = az0 + 1; z < az1; z += 2) {
            c.set(ax0, y + 2, z, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.WEST, false, true));
            c.set(ax1, y + 2, z, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.EAST, false, true));
        }
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
                        c.set(x, yy, z, edge ? (fin ? look.wall : look.trim) : look.roof);
                    }
            }
            step += h;
        }
        int cx = (ax0 + ax1) / 2, cz = (az0 + az1) / 2;
        c.set(cx, y + step, cz, Blocks.CHISELED_SANDSTONE);
        c.fill(cx, y + step + 1, cz, cx, y + step + 7, cz, Blocks.LIGHTNING_ROD);
        c.set(cx, y + step + 8, cz, Blocks.END_ROD);
    }

    /** A billboard on two pairs of legs, lit from below by floodlights on the roof. */
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
        // Floodlights: lamps on the roof in front of the board, shining up at it.
        for (int dx = 1; dx < width; dx += 2) {
            c.set(x + dx, y, z - 2, Blocks.SMOOTH_STONE_SLAB);
            c.set(x + dx, y, z - 1, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        }
    }

    /** Air-conditioning units and vents along the back of the roof, clear of the stair hut and the water tank. */
    private void vents(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        int z = az1 - 2;
        if (z <= az0 + 5) return;
        for (int x = ax0 + 2; x <= ax1 - 6; x += 4) {
            if (CityGrid.chance(seed, x, 3) < 0.3) continue;
            c.set(x, y, z, Blocks.SMOOTH_STONE);
            c.set(x + 1, y, z, Blocks.SMOOTH_STONE);
            c.set(x, y + 1, z, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, false, false));
            c.set(x + 1, y + 1, z, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, false, false));
            c.set(x + 1, y, z - 1, Blocks.LIGHTNING_ROD.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.NORTH));
        }
    }

    private void garden(CityCanvas c, int ax0, int az0, int ax1, int az1, int y) {
        for (int x = ax0 + 1; x < ax1; x++)
            for (int z = az0 + 1; z < az1; z++) {
                boolean path = x == (ax0 + ax1) / 2;
                c.set(x, y, z, path ? Blocks.GRAVEL : Blocks.GRASS_BLOCK);
                if (path) continue;
                int pick = CityGrid.hash(seed, x, z) % 9;
                if (pick == 0) c.set(x, y + 1, z, Blocks.AZALEA);
                else if (pick == 1) c.set(x, y + 1, z, Blocks.FLOWERING_AZALEA);
                else if (pick == 2) c.set(x, y + 1, z, Blocks.SHORT_GRASS);
                else if (pick == 3) c.set(x, y + 1, z, Blocks.POPPY);
            }
    }
}
