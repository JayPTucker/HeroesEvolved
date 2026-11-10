package com.jayptucker.heroesevolved.time.intro.city;

/**
 * Odessa's street grid. Blocks are 32 across; each block's west and north edges are an eight-wide
 * road, so the avenue runs along x 0..7 and the streets that cross it every 32 blocks. Inside a block,
 * a three-wide sidewalk rings an 18 by 18 building lot. The avenue ends at a T in front of Primatech
 * Paper, whose superblock spans the two blocks either side of it at the south edge.
 */
public final class CityGrid {
    public static final int MIN = -128, WIDTH = 256, BLOCK = 32, ROAD = 8, STREET_Y = 64;
    public static final int BLOCKS = WIDTH / BLOCK, MAX = MIN + WIDTH - 1;
    /** Building lots run 11..28 inside each block; 8..10 and 29..31 are sidewalk. */
    public static final int LOT_MIN = 11, LOT_MAX = 28;
    /** The superblock where the avenue ends: Primatech Paper's lot, blocks 3 and 4 of the southern row. */
    public static final int TERMINUS_BZ = 7;

    private CityGrid() {}

    public static int bx(int x) { return Math.floorDiv(x - MIN, BLOCK); }
    public static int bz(int z) { return Math.floorDiv(z - MIN, BLOCK); }
    public static int lx(int x) { return Math.floorMod(x - MIN, BLOCK); }
    public static int lz(int z) { return Math.floorMod(z - MIN, BLOCK); }
    /** World x of a block's west edge. */
    public static int originX(int bx) { return MIN + bx * BLOCK; }
    public static int originZ(int bz) { return MIN + bz * BLOCK; }

    public static boolean inCity(int x, int z) { return x >= MIN && x <= MAX && z >= MIN && z <= MAX; }

    /** Part of the superblock at the end of the avenue (where the avenue itself stops). */
    public static boolean terminus(int x, int z) {
        int bx = bx(x), bz = bz(z);
        return bz == TERMINUS_BZ && lz(z) >= ROAD && (bx == 4 || bx == 3 && lx(x) >= ROAD);
    }

    public static boolean road(int x, int z) {
        if (!inCity(x, z) || terminus(x, z)) return false;
        return lx(x) < ROAD || lz(z) < ROAD;
    }

    /** The avenue: the road along x 0..7, from the north edge down to the T at Primatech. */
    public static boolean avenue(int x, int z) { return x >= 0 && x <= 7 && road(x, z); }

    /** A stable pseudo-random number for any list of integers. */
    public static int hash(int... values) {
        int h = 0x9E3779B9;
        for (int v : values) {
            h ^= v + 0x7F4A7C15 + (h << 6) + (h >>> 2);
            h *= 0x85EBCA6B;
            h ^= h >>> 13;
            h *= 0xC2B2AE35;
            h ^= h >>> 16;
        }
        return h & 0x7FFFFFFF;
    }

    /** A stable number in [0, 1). */
    public static double chance(int... values) { return hash(values) / (double) 0x80000000L; }
}
