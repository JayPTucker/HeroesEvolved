package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Odessa's open places: the Commons (a park four blocks big in the north-west, cut by its cross streets the way a big
 * city park is), Odessa Square (the block south-east of the great towers, all billboards and red steps), the plazas in
 * front of the slab towers, and the subway entrances in them. All original designs.
 */
final class Squares {
    private Squares() {}
    private static final int Y = CityGrid.STREET_Y;

    // ------------------------------------------------------------------
    // The Commons
    // ------------------------------------------------------------------

    /**
     * One block of the Commons. Each of the four has its own character: the pond and its bridge in the north-west, the
     * ramble (woods and rock) in the south-west, the great lawn in the north-east, and the mall (a promenade between rows
     * of trees, a fountain, and the gate onto the city) in the south-east.
     */
    static void commons(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        int a = ox + CityGrid.LOT_MIN, b = ox + CityGrid.LOT_MAX, c = oz + CityGrid.LOT_MIN, d = oz + CityGrid.LOT_MAX;
        int part = (bx % 2) + 2 * (bz % 2);
        out.add(new Feature(a - 4, c - 4, b + 4, d + 4, cv -> {
            ground(cv, a, c, b, d, bx, bz);
            switch (part) {
                case 0 -> pond(cv, a, c, b, d);
                case 2 -> ramble(cv, a, c, b, d, bx, bz);
                case 1 -> lawn(cv, a, c, b, d);
                default -> mall(cv, a, c, b, d);
            }
            wall(cv, a, c, b, d, part);
        }));
    }

    /** Grass over the whole lot, scattered with flowers and grass, a gravel path round the inside of the wall. */
    private static void ground(CityCanvas c, int a, int cz, int b, int d, int bx, int bz) {
        for (int x = a; x <= b; x++)
            for (int z = cz; z <= d; z++) {
                boolean path = x == a + 1 || x == b - 1 || z == cz + 1 || z == d - 1;
                c.set(x, Y, z, path ? Blocks.GRAVEL : Blocks.GRASS_BLOCK);
                if (path) continue;
                int pick = CityGrid.hash(x, z, bx, bz, 7) % 23;
                if (pick == 0) c.set(x, Y + 1, z, Blocks.DANDELION);
                else if (pick == 1) c.set(x, Y + 1, z, Blocks.OXEYE_DAISY);
                else if (pick < 6) c.set(x, Y + 1, z, Blocks.SHORT_GRASS);
            }
    }

    /**
     * The park wall: a low stone wall along the sidewalk with a coping, lamps on piers, and gaps for the gates at the
     * middle of each side (and at the corner that faces the city, on the mall).
     */
    private static void wall(CityCanvas c, int a, int cz, int b, int d, int part) {
        for (int x = a; x <= b; x++)
            for (int z = cz; z <= d; z++) {
                boolean edge = x == a || x == b || z == cz || z == d;
                if (!edge) continue;
                int along = x == a || x == b ? z - cz : x - a;
                boolean gate = along >= 8 && along <= 9;
                if (gate) { c.set(x, Y, z, Blocks.GRAVEL); c.set(x, Y + 1, z, Blocks.AIR); continue; }
                boolean pier = along % 6 == 0 || along == 7 || along == 10;
                c.set(x, Y, z, Blocks.STONE_BRICKS);
                c.set(x, Y + 1, z, pier ? Blocks.STONE_BRICKS : Blocks.STONE_BRICK_WALL);
                if (pier) {
                    c.set(x, Y + 2, z, CityCanvas.slab(Blocks.STONE_BRICK_SLAB, false));
                    if (along == 7 || along == 10) {
                        c.set(x, Y + 2, z, Blocks.STONE_BRICKS);
                        c.set(x, Y + 3, z, Blocks.LANTERN);
                    }
                }
            }
    }

    /** A pond with a rocky shore and a stone footbridge across its narrow middle, willows leaning over the water. */
    private static void pond(CityCanvas c, int a, int cz, int b, int d) {
        double cx = (a + b) / 2.0, mz = (cz + d) / 2.0;
        for (int x = a + 2; x <= b - 2; x++)
            for (int z = cz + 2; z <= d - 2; z++) {
                double dx = (x - cx) / 7.2, dz = (z - mz) / 6.2;
                // A waist across the middle, where the bridge crosses.
                double waist = 1 - 0.35 * Math.exp(-Math.pow((x - cx) / 2.0, 2));
                double r = dx * dx + dz * dz / (waist * waist) + (CityGrid.hash(x, z, 31) % 7) / 40.0;
                if (r > 1.0) {
                    if (r < 1.25 && CityGrid.hash(x, z, 5) % 3 == 0) {
                        c.set(x, Y, z, Blocks.MOSSY_COBBLESTONE);
                        c.set(x, Y + 1, z, Blocks.AIR);
                    }
                    continue;
                }
                c.set(x, Y + 1, z, Blocks.AIR);
                c.set(x, Y, z, Blocks.WATER);
                c.set(x, Y - 1, z, r < 0.45 ? Blocks.WATER.defaultBlockState() : Blocks.MUD.defaultBlockState());
                if (r < 0.45) c.set(x, Y - 2, z, Blocks.MUD);
                if (r > 0.75 && CityGrid.hash(x, z, 9) % 9 == 0) c.set(x, Y + 1, z, Blocks.LILY_PAD);
            }
        // The bridge: a low arch of stone along the north-south axis, railed both sides.
        int bxc = (a + b) / 2;
        for (int z = cz + 2; z <= d - 2; z++) {
            int t = Math.min(z - (cz + 2), (d - 2) - z);
            int deck = Y + Math.min(2, t / 2);
            for (int x = bxc - 1; x <= bxc + 1; x++) {
                c.set(x, deck, z, x == bxc ? Blocks.STONE_BRICKS : Blocks.STONE_BRICKS);
                for (int y = deck + 1; y <= Y + 3; y++) c.set(x, y, z, Blocks.AIR);
            }
            for (int x : new int[] {bxc - 2, bxc + 2}) {
                c.set(x, deck, z, Blocks.STONE_BRICKS);
                c.set(x, deck + 1, z, Blocks.STONE_BRICK_WALL);
            }
        }
        for (int[] t : new int[][] {{a + 3, cz + 3}, {b - 3, d - 3}, {b - 3, cz + 4}, {a + 4, d - 3}})
            Landmarks.bigTree(c, t[0], t[1], t[0] + t[1]);
    }

    /** Woods on rock: boulders of stone and tuff, dense trees, a winding path through them. */
    private static void ramble(CityCanvas c, int a, int cz, int b, int d, int bx, int bz) {
        for (int x = a + 2; x <= b - 2; x++)
            for (int z = cz + 2; z <= d - 2; z++) {
                double wind = (a + b) / 2.0 + 4 * Math.sin((z - cz) / 3.0);
                if (Math.abs(x - wind) <= 1) { c.set(x, Y, z, Blocks.COARSE_DIRT); c.set(x, Y + 1, z, Blocks.AIR); continue; }
                int h = CityGrid.hash(x, z, bx, bz, 13);
                if (h % 11 == 0) {
                    c.set(x, Y + 1, z, h % 2 == 0 ? Blocks.STONE : Blocks.TUFF);
                    if (h % 3 == 0) c.set(x, Y + 2, z, Blocks.COBBLESTONE);
                } else if (h % 7 == 0) c.set(x, Y + 1, z, Blocks.FERN);
            }
        int[][] trees = {{a + 4, cz + 4}, {b - 4, cz + 5}, {a + 5, d - 4}, {b - 4, d - 5}, {a + 9, cz + 9}};
        for (int i = 0; i < trees.length; i++) Landmarks.bigTree(c, trees[i][0], trees[i][1], i + bx * 5 + bz);
    }

    /** An open meadow ringed by trees, with a little bandshell on its south side. */
    private static void lawn(CityCanvas c, int a, int cz, int b, int d) {
        for (int x = a + 3; x <= b - 3; x++)
            for (int z = cz + 3; z <= d - 3; z++) c.set(x, Y + 1, z, Blocks.AIR);
        int[][] trees = {{a + 3, cz + 3}, {b - 3, cz + 3}, {a + 3, d - 3}, {b - 3, d - 3}};
        for (int i = 0; i < trees.length; i++) Landmarks.bigTree(c, trees[i][0], trees[i][1], 40 + i);
        // The bandshell: a quarter dome of quartz over a stage, open to the north.
        int sx = (a + b) / 2, sz = d - 4;
        for (int x = sx - 3; x <= sx + 3; x++)
            for (int z = sz - 1; z <= sz + 1; z++) c.set(x, Y + 1, z, Blocks.SMOOTH_QUARTZ_SLAB);
        for (int x = sx - 3; x <= sx + 3; x++) {
            int h = 4 - Math.abs(x - sx) / 2;
            c.fill(x, Y + 1, sz + 2, x, Y + 1 + h, sz + 2, Blocks.SMOOTH_QUARTZ);
            c.set(x, Y + 1 + h, sz + 1, CityCanvas.stairs(Blocks.QUARTZ_STAIRS, Direction.SOUTH, true));
        }
        for (int x : new int[] {sx - 3, sx + 3}) c.fill(x, Y + 2, sz - 1, x, Y + 4, sz - 1, Blocks.QUARTZ_PILLAR);
        for (int x = sx - 3; x <= sx + 3; x++) c.set(x, Y + 5, sz - 1, CityCanvas.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
    }

    /**
     * The mall: a gravel promenade down the middle between two rows of trees, benches between the trees facing the
     * promenade, and a fountain where it meets the cross path.
     */
    private static void mall(CityCanvas c, int a, int cz, int b, int d) {
        int mx = (a + b) / 2;
        for (int z = cz + 2; z <= d - 2; z++)
            for (int x = mx - 2; x <= mx + 2; x++) { c.set(x, Y, z, Blocks.GRAVEL); c.set(x, Y + 1, z, Blocks.AIR); }
        for (int x = a + 2; x <= b - 2; x++) { c.set(x, Y, (cz + d) / 2, Blocks.GRAVEL); c.set(x, Y + 1, (cz + d) / 2, Blocks.AIR); }
        for (int z = cz + 3; z <= d - 3; z += 5) {
            Landmarks.bigTree(c, mx - 4, z, z);
            Landmarks.bigTree(c, mx + 4, z, z + 1);
        }
        for (int z = cz + 5; z <= d - 5; z += 5) {
            c.set(mx - 3, Y + 1, z, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
            c.set(mx + 3, Y + 1, z, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        }
        Landmarks.fountain(c, mx, (cz + d) / 2);
    }

    // ------------------------------------------------------------------
    // Odessa Square
    // ------------------------------------------------------------------

    /**
     * Odessa Square: the block's west half, on the avenue, is open paving with red steps at its south end, people's seats
     * for watching the city (they face north up the avenue, toward Kirby Plaza); its east half is a sixteen-storey
     * building wrapped in lit billboards and a news ticker. A subway entrance goes down in the middle of the paving.
     */
    static void square(List<Feature> out, int bx, int bz) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        int a = ox + CityGrid.LOT_MIN, b = ox + CityGrid.LOT_MAX, cz = oz + CityGrid.LOT_MIN, d = oz + CityGrid.LOT_MAX;
        int split = a + 8;
        Building.Look look = CityPlan.hotel(CityGrid.hash(bx, bz, 3)).with(Building.Ground.SHOP);
        Building tower = new Building(split, cz, b, d, 16, look, Building.tiers(16, 13, 2), Building.Top.BILLBOARD,
                new boolean[] {true, true, false, true}, CityGrid.hash(bx, bz, 11), "ODESSA DELI", "THEATRE", "SOUVENIRS", "PIZZA", "CAMERAS");
        out.add(new Feature(a - 2, cz - 2, b + 2, d + 2, c -> {
            paving(c, a, cz, split - 1, d);
            steps(c, a, split - 1, d);
            subway(c, a + 3, cz + 3, Direction.SOUTH);
            for (int[] p : new int[][] {{a + 1, cz + 1}, {split - 2, cz + 1}}) {
                c.fill(p[0], Y + 1, p[1], p[0], Y + 4, p[1], Blocks.POLISHED_BLACKSTONE_WALL);
                c.set(p[0], Y + 5, p[1], Blocks.SEA_LANTERN);
            }
            tower.draw(c);
            billboards(c, split, cz, b, d);
        }));
    }

    private static void paving(CityCanvas c, int a, int cz, int b, int d) {
        for (int x = a; x <= b; x++)
            for (int z = cz; z <= d; z++)
                c.set(x, Y, z, CityCanvas.slab((x + z) % 3 == 0 ? Blocks.POLISHED_ANDESITE_SLAB : Blocks.SMOOTH_STONE_SLAB, false));
    }

    /** Red steps rising to the south: seven rows of seats, lit red from below, with stone cheeks. */
    private static void steps(CityCanvas c, int a, int b, int d) {
        int rows = 7;
        for (int r = 0; r < rows; r++) {
            int z = d - rows + 1 + r;
            for (int x = a + 1; x <= b - 1; x++) {
                for (int y = Y; y < Y + r; y++) c.set(x, y, z, y == Y + r - 1 ? Blocks.RED_STAINED_GLASS : Blocks.RED_NETHER_BRICKS);
                if (r > 0) c.set(x, Y + r - 1, z, Blocks.RED_STAINED_GLASS);
                c.set(x, Y + r, z, CityCanvas.stairs(Blocks.RED_NETHER_BRICK_STAIRS, Direction.SOUTH, false));
            }
            for (int x : new int[] {a, b}) c.fill(x, Y, z, x, Y + r + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
            // Light under the glass risers.
            if (r > 1) for (int x = a + 2; x <= b - 2; x += 2) c.set(x, Y + r - 2, z, Blocks.SHROOMLIGHT);
        }
    }

    /**
     * Billboards over the square: three boards stacked on the face toward the avenue and two on the face toward the
     * cross street, each framed, lit, and lettered in blocks, with a news ticker wrapping both faces above the shops.
     */
    private static void billboards(CityCanvas c, int x0, int z0, int x1, int z1) {
        // On the west face (seen from the square and the avenue): read north to south.
        int wx = x0 - 1, len = z1 - z0 + 1;
        board(c, wx, Y + 7, z0, Direction.SOUTH, len, 9, Blocks.RED_CONCRETE, Blocks.SEA_LANTERN, "DELI");
        board(c, wx, Y + 18, z0, Direction.SOUTH, len, 13, Blocks.BLACK_CONCRETE, Blocks.OCHRE_FROGLIGHT, "NEWS", "AT 6");
        board(c, wx, Y + 33, z0, Direction.SOUTH, len, 13, Blocks.BLUE_CONCRETE, Blocks.SEA_LANTERN, "FLY", "AIR");
        board(c, wx, Y + 48, z0, Direction.SOUTH, len, 9, Blocks.ORANGE_CONCRETE, Blocks.SEA_LANTERN, "COLA");
        // On the north face (seen from the cross street): read east to west.
        int nz = z0 - 1, wlen = x1 - x0 + 1;
        board(c, x1, Y + 9, nz, Direction.WEST, wlen, 9, Blocks.PURPLE_CONCRETE, Blocks.PEARLESCENT_FROGLIGHT, "TV");
        board(c, x1, Y + 20, nz, Direction.WEST, wlen, 13, Blocks.GREEN_CONCRETE, Blocks.SEA_LANTERN, "7", "OK");
        // The news ticker: a lit band over the shop signs, round both faces.
        for (int z = z0 - 1; z <= z1; z++) ticker(c, wx, Y + 6, z, z);
        for (int x = x0; x <= x1; x++) ticker(c, x, Y + 6, nz, x);
    }

    private static void ticker(CityCanvas c, int x, int y, int z, int i) {
        c.set(x, y, z, i % 5 == 0 || i % 7 == 0 ? Blocks.OCHRE_FROGLIGHT : Blocks.BLACK_CONCRETE);
    }

    /**
     * A billboard one block out from a wall, its bottom-left corner at ({@code x}, {@code y}, {@code z}) and running
     * {@code right} for {@code width} cells: a black frame, a coloured field, and up to two lines of lettering in lit
     * blocks, centred.
     */
    static void board(CityCanvas c, int x, int y, int z, Direction right, int width, int height, Block field, Block light, String... lines) {
        for (int u = 0; u < width; u++)
            for (int v = 0; v < height; v++) {
                boolean frame = u == 0 || v == 0 || u == width - 1 || v == height - 1;
                c.set(x + right.getStepX() * u, y + v, z + right.getStepZ() * u, frame ? Blocks.BLACK_CONCRETE : field);
            }
        int lineHeight = 6, total = lines.length * lineHeight - 1;
        int top = y + (height + total) / 2 - 1;
        for (int l = 0; l < lines.length; l++) {
            String text = lines[l];
            int textWidth = text.length() * 4 - 1;
            int start = Math.max(1, (width - textWidth) / 2);
            for (int i = 0; i < text.length(); i++) {
                int[] glyph = BlockFont.glyph(text.charAt(i));
                for (int row = 0; row < 5; row++)
                    for (int col = 0; col < 3; col++) {
                        if ((glyph[row] >> (2 - col) & 1) == 0) continue;
                        int u = start + i * 4 + col, v = top - y - l * lineHeight - row;
                        if (u < 1 || u >= width - 1 || v < 1 || v >= height - 1) continue;
                        c.set(x + right.getStepX() * u, y + v, z + right.getStepZ() * u, light);
                    }
            }
        }
    }

    // ------------------------------------------------------------------
    // Tower plazas and subway entrances
    // ------------------------------------------------------------------

    /** The plaza in front of a slab tower: paving, two planters with trees, benches, a subway entrance and lamps. */
    static void towerPlaza(CityCanvas c, int a, int z0, int b, int z1, boolean north, int seed) {
        paving(c, a, z0, b, z1);
        int mid = (z0 + z1) / 2;
        for (int px : new int[] {a + 3, b - 3}) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) c.set(px + dx, Y + 1, mid + dz, dx == 0 && dz == 0 ? Blocks.GRASS_BLOCK : Blocks.POLISHED_GRANITE);
            c.set(px, Y, mid, Blocks.DIRT);
            smallTree(c, px, mid, seed + px);
        }
        if (seed % 2 == 0) subway(c, (a + b) / 2 - 1, z0 + 1, Direction.EAST);
        else
            for (int x = a + 7; x <= b - 7; x++) c.set(x, Y + 1, north ? z1 : z0, CityCanvas.stairs(Blocks.POLISHED_ANDESITE_STAIRS,
                    north ? Direction.SOUTH : Direction.NORTH, false));
    }

    /** A street tree in a planter: a short trunk and a crown two blocks round, kept clear of the buildings beside it. */
    static void smallTree(CityCanvas c, int x, int z, int salt) {
        boolean birch = CityGrid.hash(x, z, salt) % 3 == 0;
        Block log = birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG;
        BlockState leaf = CityCanvas.leaves(birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES);
        c.fill(x, Y + 1, z, x, Y + 4, z, log);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 3; dy <= 6; dy++) {
                    if (dx == 0 && dz == 0 && dy <= 4) continue;
                    double r = dy == 6 ? 1.2 : dy == 3 ? 1.6 : 2.3;
                    if (dx * dx + dz * dz > r * r + 0.3) continue;
                    c.set(x + dx, Y + dy, z + dz, leaf);
                }
    }

    /**
     * A subway entrance going down in direction {@code down} from ({@code x}, {@code z}): two wide, three steps down to a
     * tiled landing and a shut gate, a rail on three sides, and a green globe lamp on each side of the way in.
     */
    static void subway(CityCanvas c, int x, int z, Direction down) {
        Direction across = down.getClockWise();
        BlockState step = CityCanvas.stairs(Blocks.POLISHED_ANDESITE_STAIRS, down.getOpposite(), false);
        for (int i = -1; i <= 6; i++)
            for (int w = -1; w <= 2; w++) {
                int cx = x + down.getStepX() * i + across.getStepX() * w, cz = z + down.getStepZ() * i + across.getStepZ() * w;
                boolean pit = i >= 0 && i <= 5 && w >= 0 && w <= 1;
                if (pit) {
                    int floor = i <= 2 ? Y - 1 - i : Y - 4;
                    for (int y = floor + 1; y <= Y + 2; y++) c.set(cx, y, cz, Blocks.AIR);
                    if (i <= 2) c.set(cx, floor, cz, step);
                    else c.set(cx, floor, cz, Blocks.SMOOTH_STONE);
                    if (i == 5) c.fill(cx, Y - 3, cz, cx, Y - 2, cz, Blocks.IRON_BARS);
                    continue;
                }
                if (i < 0) continue;
                // Tiled walls round the pit underground, the rail on the paving above.
                for (int y = Y - 4; y <= Y - 1; y++) c.set(cx, y, cz, (y + i) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.SMOOTH_QUARTZ);
                c.set(cx, Y, cz, Blocks.POLISHED_BLACKSTONE);
                c.set(cx, Y + 1, cz, Blocks.IRON_BARS);
            }
        // Under the landing and past the gate.
        for (int w = 0; w <= 1; w++) {
            int cx = x + down.getStepX() * 6 + across.getStepX() * w, cz = z + down.getStepZ() * 6 + across.getStepZ() * w;
            c.fill(cx, Y - 4, cz, cx, Y - 1, cz, Blocks.BLACK_CONCRETE);
        }
        // The globes, either side of the way in, and the sign.
        for (int w : new int[] {-1, 2}) {
            int cx = x + across.getStepX() * w, cz = z + across.getStepZ() * w;
            c.fill(cx, Y + 1, cz, cx, Y + 2, cz, Blocks.POLISHED_BLACKSTONE_WALL);
            c.set(cx, Y + 3, cz, Blocks.VERDANT_FROGLIGHT);
        }
        int sx = x - down.getStepX() + across.getStepX() * -1, sz = z - down.getStepZ() + across.getStepZ() * -1;
        c.sign(sx, Y + 2, sz, down.getOpposite(), Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, "SUBWAY", "UPTOWN");
    }
}
