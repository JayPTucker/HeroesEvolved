package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * What stands where in Odessa. Downtown towers crowd the north end of the avenue around Kirby Plaza,
 * where the light falls; midtown offices and hotels give way to brick walk-ups with shops, brownstone
 * rows, a church and a park in the south, where a visitor arrives. Every building is chosen from the
 * block's position alone, so the city is the same every time it is built.
 */
public final class CityPlan {
    /**
     * One letter per block, rows north to south. T tower, X the tallest tower, D downtown, m midtown,
     * r residential; P Kirby Plaza, H city hall, C church, K park, L parking, U construction, Q Primatech,
     * A the Odessa Arms ({@link Apartments}), where the Last Day begins.
     */
    private static final String[] MAP = {
            "mmDDDDmm",
            "mDTTPTDm",
            "mDTTTXDm",
            "mDTHTTDm",
            "rmDmmDmr",
            "rrCrrKrr",
            "rLrrrrUr",
            "rrAQQrrr"};

    private static final String[] SHOPS = {"CORNER CAFE", "PHARMACY", "CITY MARKET", "BOOKS & NEWS", "DELI", "LAUNDROMAT",
            "HARDWARE", "BARBER", "DINER", "LENA'S BAKERY", "ROSA'S FLOWERS", "SHOES", "RECORDS", "OPTICIAN", "DRY CLEANING",
            "TAILOR", "TOYS", "CAMERAS", "PIZZA", "BANK", "PAWN", "NAILS", "TACOS", "VIDEO RENTAL"};

    private static List<Feature> features;

    private CityPlan() {}

    public static char kind(int bx, int bz) {
        return bx < 0 || bz < 0 || bx >= CityGrid.BLOCKS || bz >= CityGrid.BLOCKS ? ' ' : MAP[bz].charAt(bx);
    }

    static synchronized List<Feature> features() {
        if (features != null) return features;
        List<Feature> out = new ArrayList<>();
        for (int bz = 0; bz < CityGrid.BLOCKS; bz++)
            for (int bx = 0; bx < CityGrid.BLOCKS; bx++) {
                char kind = kind(bx, bz);
                switch (kind) {
                    case 'P' -> Landmarks.plaza(out, bx, bz);
                    case 'H' -> Landmarks.cityHall(out, bx, bz);
                    case 'C' -> Landmarks.church(out, bx, bz);
                    case 'K' -> Landmarks.park(out, bx, bz);
                    case 'L' -> Landmarks.parking(out, bx, bz);
                    case 'U' -> Landmarks.construction(out, bx, bz);
                    case 'Q' -> { if (bx == 3) Landmarks.primatech(out); }
                    case 'A' -> out.add(Apartments.feature());
                    default -> buildings(out, bx, bz, kind);
                }
                if (kind != 'Q') {
                    Streets.Kind street = kind == 'T' || kind == 'X' || kind == 'D' || kind == 'H' || kind == 'P' ? Streets.Kind.DOWNTOWN
                            : kind == 'm' ? Streets.Kind.MIDTOWN : kind == 'K' ? Streets.Kind.PARK : Streets.Kind.RESIDENTIAL;
                    Streets.furnish(out, bx, bz, street);
                }
                Streets.park(out, bx, bz);
            }
        features = List.copyOf(out);
        return features;
    }

    // ------------------------------------------------------------------
    // Ordinary blocks: parcels and buildings
    // ------------------------------------------------------------------

    private static void buildings(List<Feature> out, int bx, int bz, char kind) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        int seed = CityGrid.hash(bx, bz, 1984);
        int a = ox + CityGrid.LOT_MIN, b = ox + CityGrid.LOT_MAX, c = oz + CityGrid.LOT_MIN, d = oz + CityGrid.LOT_MAX;
        int midX = a + 8, midZ = c + 8;
        switch (kind) {
            case 'X' -> add(out, ox, oz, a, c, b, d, 22, deco(seed), Building.tiers(22, 1, 0, 10, 2, 16, 4), Building.Top.SPIRE, seed);
            case 'T' -> {
                if (seed % 3 == 0) {
                    add(out, ox, oz, a, c, midX, d, 14 + seed % 5, glass(seed), Building.tiers(20, 1, 0, 11, 1), Building.Top.MAST, seed);
                    add(out, ox, oz, midX + 1, c, b, d, 8 + seed % 4, office(seed >> 3), Building.tiers(12), Building.Top.FLAT, seed + 1);
                } else if (seed % 3 == 1)
                    add(out, ox, oz, a, c, b, d, 15 + seed % 5, glass(seed), Building.tiers(20, 1, 0, 11, 2, 16, 4), Building.Top.MAST, seed);
                else
                    add(out, ox, oz, a, c, b, d, 14 + seed % 5, deco(seed), Building.tiers(19, 1, 0, 9, 2, 14, 4), Building.Top.SPIRE, seed);
            }
            case 'D' -> {
                if (seed % 2 == 0) add(out, ox, oz, a, c, b, d, 8 + seed % 4, office(seed), Building.tiers(12, 1, 0, 9, 1), Building.Top.BILLBOARD, seed);
                else {
                    add(out, ox, oz, a, c, b, midZ, 9 + seed % 4, hotel(seed), Building.tiers(13), Building.Top.BILLBOARD, seed);
                    add(out, ox, oz, a, midZ + 1, b, d, 6 + seed % 3, office(seed >> 2), Building.tiers(9), Building.Top.FLAT, seed + 1);
                }
            }
            case 'm' -> {
                int[][] parcels = seed % 2 == 0
                        ? new int[][] {{a, c, midX, d}, {midX + 1, c, b, d}}
                        : new int[][] {{a, c, midX, midZ}, {midX + 1, c, b, midZ}, {a, midZ + 1, midX, d}, {midX + 1, midZ + 1, b, d}};
                for (int i = 0; i < parcels.length; i++) {
                    int s = CityGrid.hash(seed, i);
                    int[] p = parcels[i];
                    switch (s % 4) {
                        case 0 -> add(out, ox, oz, p[0], p[1], p[2], p[3], 5 + s % 4, office(s), Building.tiers(8), Building.Top.FLAT, s);
                        case 1 -> add(out, ox, oz, p[0], p[1], p[2], p[3], 7 + s % 4, hotel(s), Building.tiers(10), Building.Top.BILLBOARD, s);
                        default -> add(out, ox, oz, p[0], p[1], p[2], p[3], 4 + s % 3, brick(s), Building.tiers(6), Building.Top.WATER_TOWER, s);
                    }
                }
            }
            default -> {
                if (seed % 3 == 0) {
                    // Brownstone rows: three houses on the north street, three on the south.
                    for (int row = 0; row < 2; row++)
                        for (int i = 0; i < 3; i++) {
                            int s = CityGrid.hash(seed, row, i);
                            int x0 = a + i * 6, z0 = row == 0 ? c : midZ + 1;
                            add(out, ox, oz, x0, z0, x0 + 5, z0 + 8, 2 + s % 2, brownstone(s), Building.tiers(3), Building.Top.CHIMNEYS, s);
                        }
                } else {
                    int[][] parcels = {{a, c, midX, midZ}, {midX + 1, c, b, midZ}, {a, midZ + 1, midX, d}, {midX + 1, midZ + 1, b, d}};
                    for (int i = 0; i < parcels.length; i++) {
                        int s = CityGrid.hash(seed, i, 3);
                        int[] p = parcels[i];
                        if (s % 5 == 0) add(out, ox, oz, p[0], p[1], p[2], p[3], 3, brownstone(s), Building.tiers(3), Building.Top.GARDEN, s);
                        else add(out, ox, oz, p[0], p[1], p[2], p[3], 3 + s % 4, brick(s), Building.tiers(6), s % 2 == 0 ? Building.Top.WATER_TOWER : Building.Top.FLAT, s);
                    }
                }
            }
        }
    }

    private static void add(List<Feature> out, int ox, int oz, int x0, int z0, int x1, int z1, int floors, Building.Look look,
                            int[] insets, Building.Top top, int seed) {
        boolean[] street = {z0 == oz + CityGrid.LOT_MIN, z1 == oz + CityGrid.LOT_MAX, x0 == ox + CityGrid.LOT_MIN, x1 == ox + CityGrid.LOT_MAX};
        int[] fitted = new int[floors + 1];
        for (int k = 0; k <= floors; k++) fitted[k] = Math.min(insets[Math.min(k, insets.length - 1)], (Math.min(x1 - x0, z1 - z0) - 4) / 2);
        Building building = new Building(x0, z0, x1, z1, floors, look, fitted, top, street, seed, SHOPS);
        out.add(new Feature(x0 - 2, z0 - 2, x1 + 2, z1 + 2, building::draw));
    }

    // ------------------------------------------------------------------
    // Styles
    // ------------------------------------------------------------------

    private static final Block[][] AWNINGS = {
            {Blocks.RED_WOOL, Blocks.WHITE_WOOL}, {Blocks.GREEN_WOOL, Blocks.WHITE_WOOL}, {Blocks.BLUE_WOOL, Blocks.WHITE_WOOL},
            {Blocks.BLACK_WOOL, Blocks.YELLOW_WOOL}, {Blocks.ORANGE_WOOL, Blocks.ORANGE_WOOL}, {Blocks.CYAN_WOOL, Blocks.CYAN_WOOL}};

    /** Brick walk-ups: recessed windows between sill and lintel, shops below, a dark cornice above. */
    static Building.Look brick(int seed) {
        Block[][] palettes = {
                {Blocks.BRICKS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS},
                {Blocks.MUD_BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE},
                {Blocks.TERRACOTTA, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE},
                {Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_STONE, Blocks.LIGHT_GRAY_TERRACOTTA},
                {Blocks.BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE}};
        Block[] p = palettes[seed % palettes.length];
        Block[] awning = AWNINGS[(seed / 5) % AWNINGS.length];
        Block pier = (seed / 7) % 2 == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.POLISHED_BLACKSTONE;
        Block cornice = (seed / 11) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.DARK_OAK_STAIRS;
        return new Building.Look(p[0], p[0], p[1], p[2], Blocks.GRAY_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS, Blocks.OCHRE_FROGLIGHT,
                Blocks.SPRUCE_PLANKS, Blocks.GRAY_CONCRETE, p[2], cornice, true, 2, 2, 0.42, Building.Ground.SHOP,
                pier, pier, awning[0], awning[1], (seed / 3) % 2 == 0 ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR);
    }

    /** Brownstone rows: raised doors up a stoop, tall windows, a wooden cornice. */
    static Building.Look brownstone(int seed) {
        Block wall = seed % 3 == 0 ? Blocks.BROWN_TERRACOTTA : seed % 3 == 1 ? Blocks.GRANITE : Blocks.PACKED_MUD;
        return new Building.Look(wall, wall, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.GRAY_STAINED_GLASS,
                Blocks.WHITE_STAINED_GLASS, Blocks.OCHRE_FROGLIGHT, Blocks.SPRUCE_PLANKS, Blocks.GRAY_CONCRETE, Blocks.CUT_SANDSTONE,
                Blocks.DARK_OAK_STAIRS, true, 2, 2, 0.5, Building.Ground.STOOP, Blocks.POLISHED_GRANITE, wall,
                Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, Blocks.DARK_OAK_DOOR);
    }

    /** Art deco: pale stone piers running the full height, dark spandrels, a stepped crown and spire. */
    static Building.Look deco(int seed) {
        boolean warm = seed % 2 == 0;
        return new Building.Look(warm ? Blocks.SMOOTH_SANDSTONE : Blocks.CALCITE, warm ? Blocks.BROWN_TERRACOTTA : Blocks.GRAY_TERRACOTTA,
                warm ? Blocks.BROWN_TERRACOTTA : Blocks.GRAY_TERRACOTTA, warm ? Blocks.CUT_SANDSTONE : Blocks.POLISHED_DIORITE,
                Blocks.BLACK_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS, Blocks.OCHRE_FROGLIGHT, Blocks.POLISHED_ANDESITE,
                Blocks.LIGHT_GRAY_CONCRETE, warm ? Blocks.CUT_SANDSTONE : Blocks.POLISHED_DIORITE, null, true, 1, 2, 0.3,
                Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE, warm ? Blocks.CHISELED_SANDSTONE : Blocks.CHISELED_QUARTZ_BLOCK,
                Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, Blocks.IRON_DOOR);
    }

    /** Glass curtain wall: thin mullions, tinted glass three rows tall, dark spandrel glass at each floor. */
    static Building.Look glass(int seed) {
        Block tint = seed % 3 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : seed % 3 == 1 ? Blocks.CYAN_STAINED_GLASS : Blocks.BLUE_STAINED_GLASS;
        return new Building.Look(Blocks.LIGHT_GRAY_CONCRETE, Blocks.GRAY_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS,
                tint, Blocks.WHITE_STAINED_GLASS, Blocks.SEA_LANTERN, Blocks.SMOOTH_STONE, Blocks.LIGHT_GRAY_CONCRETE,
                Blocks.LIGHT_GRAY_CONCRETE, null, false, 2, 3, 0.25, Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE,
                Blocks.GRAY_CONCRETE, Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, Blocks.IRON_DOOR);
    }

    /** A midcentury office: ribbon windows between concrete bands, piers only at the corners. */
    static Building.Look office(int seed) {
        Block band = seed % 3 == 0 ? Blocks.WHITE_CONCRETE : seed % 3 == 1 ? Blocks.SMOOTH_STONE : Blocks.LIGHT_GRAY_CONCRETE;
        return new Building.Look(band, band, band, band, Blocks.BLACK_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.SEA_LANTERN,
                Blocks.SMOOTH_STONE, Blocks.GRAY_CONCRETE, Blocks.SMOOTH_STONE_SLAB, null, false, 99, 2, 0.35, Building.Ground.LOBBY,
                Blocks.POLISHED_BLACKSTONE, band, Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, Blocks.IRON_DOOR);
    }

    /** A hotel: pale punched windows in a white block, a lobby on the street. */
    static Building.Look hotel(int seed) {
        Block wall = seed % 2 == 0 ? Blocks.WHITE_TERRACOTTA : Blocks.SMOOTH_QUARTZ;
        return new Building.Look(wall, wall, Blocks.SMOOTH_STONE, wall, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS,
                Blocks.OCHRE_FROGLIGHT, Blocks.SPRUCE_PLANKS, Blocks.GRAY_CONCRETE, Blocks.SMOOTH_STONE, Blocks.QUARTZ_STAIRS, true, 1, 2, 0.55,
                Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE, Blocks.RED_TERRACOTTA, Blocks.RED_WOOL, Blocks.RED_WOOL, Blocks.IRON_DOOR);
    }
}
