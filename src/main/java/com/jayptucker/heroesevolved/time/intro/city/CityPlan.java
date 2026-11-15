package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * What stands where in Odessa: a small city built like a slice of Manhattan. The skyline peaks in the middle of town,
 * where two great towers stand either side of the avenue (the Odessa State Building, with its lit deco crown and mooring
 * mast, and the Bishop Building, crowned with terraced steel arches and a needle), and steps down through slab towers,
 * gothic towers and pre-war apartment blocks to walk-ups and brownstone rows at the edges. Kirby Plaza, where the light
 * falls, sits in the downtown cluster at the north end of the avenue; Odessa Square, all billboards, is a block south of
 * the great towers; the Commons, a park four blocks big, fills the north-west corner. Primatech Paper closes the avenue
 * in the south, beside the Odessa Arms, where the Last Day begins. Every building is chosen from its block's position
 * alone, so the city is the same every time it is built.
 * <p>
 * The story pins a few things: the avenue (x 0..7) and the street grid; the plaza; the towers west of the plaza and
 * north of the vision's vantage (blocks 3,1 and 3,2), whose avenue faces Sylar is thrown into and so must stand at the lot
 * line (x = -4) below about y 120; Primatech's lobby and stair; and the Odessa Arms.
 */
public final class CityPlan {
    /**
     * One letter per block, rows north to south. X the Odessa State Building, Y the Bishop Building, T towers, S Odessa
     * Square, D dark glass offices (behind Kirby Plaza), p pre-war apartment blocks, m midtown, r rows of walk-ups and
     * brownstones; G the City Market ({@link Market}); K the Commons, P Kirby Plaza,
     * H city hall, C church, L parking, U construction, Q Primatech, A the Odessa Arms ({@link Apartments}).
     */
    private static final String[] MAP = {
            "KKrpDDmr",
            "KKpTPTmr",
            "rpTTTTpr",
            "rmTXYTmr",
            "rpTTSTpr",
            "rrpHGCrr",
            "rLrrprUr",
            "rrAQQrrr"};

    private static final String[] SHOPS = {"CORNER CAFE", "PHARMACY", "CITY MARKET", "BOOKS & NEWS", "DELI", "LAUNDROMAT",
            "HARDWARE", "BARBER", "DINER", "LENA'S BAKERY", "ROSA'S FLOWERS", "SHOES", "RECORDS", "OPTICIAN", "DRY CLEANING",
            "TAILOR", "TOYS", "CAMERAS", "PIZZA", "BANK", "PAWN", "NAILS", "TACOS", "VIDEO RENTAL", "BODEGA", "HOT BAGELS",
            "PASTRAMI", "LIQUOR", "NEWSSTAND", "SHOE REPAIR"};

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
                    case 'K' -> Squares.commons(out, bx, bz);
                    case 'L' -> Landmarks.parking(out, bx, bz);
                    case 'U' -> Landmarks.construction(out, bx, bz);
                    case 'Q' -> { if (bx == 3) Landmarks.primatech(out); }
                    case 'A' -> out.add(Apartments.feature());
                    case 'S' -> Squares.square(out, bx, bz);
                    default -> buildings(out, bx, bz, kind);
                }
                if (kind != 'Q') {
                    Streets.Kind street = "TXYSPHD".indexOf(kind) >= 0 ? Streets.Kind.DOWNTOWN
                            : kind == 'm' || kind == 'p' || kind == 'G' ? Streets.Kind.MIDTOWN : kind == 'K' ? Streets.Kind.PARK : Streets.Kind.RESIDENTIAL;
                    Streets.furnish(out, bx, bz, street);
                }
                Streets.park(out, bx, bz);
            }
        Outskirts.features(out);
        features = List.copyOf(out);
        return features;
    }

    // ------------------------------------------------------------------
    // Ordinary blocks
    // ------------------------------------------------------------------

    private static void buildings(List<Feature> out, int bx, int bz, char kind) {
        int ox = CityGrid.originX(bx), oz = CityGrid.originZ(bz);
        int seed = CityGrid.hash(bx, bz, 1984);
        int a = ox + CityGrid.LOT_MIN, b = ox + CityGrid.LOT_MAX, c = oz + CityGrid.LOT_MIN, d = oz + CityGrid.LOT_MAX;
        switch (kind) {
            // The Odessa State Building: a limestone podium, three setbacks, a slender shaft and a lit crown with a mast.
            case 'X' -> add(out, ox, oz, a, c, b, d, 38, state(), Building.tiers(38, 7, 1, 15, 3, 27, 5), Building.Top.CROWN, seed);
            // The Bishop Building: pale brick and steel, setbacks, a crown of arches and a needle.
            case 'Y' -> add(out, ox, oz, a, c, b, d, 32, steel(), Building.tiers(32, 9, 1, 21, 3), Building.Top.ARCHES, seed);
            case 'T' -> tower(out, bx, bz, ox, oz, a, c, b, d, seed);
            case 'p' -> prewar(out, ox, oz, a, c, b, d, seed);
            // The City Market on the avenue, and pre-war apartments behind it.
            case 'G' -> {
                Building market = Market.building(seed);
                out.add(new Feature(Market.X0 - 2, Market.Z0 - 2, Market.X1 + 2, Market.Z1 + 2, cv -> {
                    market.draw(cv);
                    Market.furnish(cv);
                }));
                add(out, ox, oz, Market.X1 + 1, c, b, d, 11, prewarLook(seed), Building.tiers(11, 10, 2), Building.Top.WATER_TOWER, seed + 1);
            }
            // Dark glass offices facing the plaza across the street, their lit windows the plaza's backdrop.
            case 'D' -> {
                int floors = 13 + seed % 4;
                add(out, ox, oz, a, c, b, d, floors, bronze(seed), Building.tiers(floors, floors - 2, 2), Building.Top.FLAT, seed);
            }
            case 'm' -> midtown(out, ox, oz, a, c, b, d, seed);
            default -> rows(out, ox, oz, a, c, b, d, seed);
        }
    }

    /**
     * A downtown tower. The two west of the avenue at the plaza and north of the great towers keep their avenue faces on
     * the lot line up past where Sylar hits them; the rest pick a type: a glass slab over a plaza, a deco setback tower, a
     * gothic tower under a copper roof, or a tower beside a lower annex.
     */
    private static void tower(List<Feature> out, int bx, int bz, int ox, int oz, int a, int c, int b, int d, int seed) {
        boolean pinned = bx == 3 && (bz == 1 || bz == 2);
        if (pinned) {
            // West of the plaza: dark glass, as the offices round Kirby Plaza are; Sylar is thrown into its avenue face.
            if (bz == 1) add(out, ox, oz, a, c, b, d, 30, bronze(seed), Building.tiers(30, 16, 1, 25, 3), Building.Top.LANTERN, seed);
            // North of the vision's vantage: a glass curtain wall, so Sylar strikes glass with the tower behind it.
            else add(out, ox, oz, a, c, b, d, 27, glass(seed), Building.tiers(27, 14, 2, 21, 4), Building.Top.MAST, seed);
            return;
        }
        if (bz == 1 && bx == 5) {
            // East of the plaza: a dark glass slab to match its neighbour across the plaza.
            add(out, ox, oz, a, c, b, d, 26, bronze(seed + 1), Building.tiers(26, 18, 2), Building.Top.MAST, seed);
            return;
        }
        int floors = 20 + seed % 9;
        switch (seed % 4) {
            case 0 -> {
                // A glass slab set back behind a plaza on the street with the most light.
                boolean northPlaza = bz % 2 == 0;
                int tz0 = northPlaza ? c + 6 : c, tz1 = northPlaza ? d : d - 6;
                add(out, ox, oz, a, tz0, b, tz1, floors + 2, bronze(seed), Building.tiers(floors + 2), Building.Top.LANTERN, seed);
                int pz0 = northPlaza ? c : d - 5, pz1 = northPlaza ? c + 5 : d;
                out.add(new Feature(a - 1, pz0 - 1, b + 1, pz1 + 1, cv -> Squares.towerPlaza(cv, a, pz0, b, pz1, northPlaza, seed)));
            }
            case 1 -> add(out, ox, oz, a, c, b, d, floors, deco(seed), Building.tiers(floors, 9, 2, 17, 4), Building.Top.SPIRE, seed);
            case 2 -> add(out, ox, oz, a, c, b, d, floors, gothic(seed), Building.tiers(floors, floors - 6, 2), Building.Top.PYRAMID, seed);
            default -> {
                boolean west = seed / 4 % 2 == 0;
                int mid = a + 10;
                if (west) {
                    add(out, ox, oz, a, c, mid, d, floors, glass(seed), Building.tiers(floors, 12, 1), Building.Top.MAST, seed);
                    add(out, ox, oz, mid + 1, c, b, d, 9 + seed % 4, prewarLook(seed >> 3), Building.tiers(12), Building.Top.WATER_TOWER, seed + 1);
                } else {
                    add(out, ox, oz, a, c, mid - 3, d, 9 + seed % 4, prewarLook(seed >> 3), Building.tiers(12), Building.Top.WATER_TOWER, seed + 1);
                    add(out, ox, oz, mid - 2, c, b, d, floors, glass(seed), Building.tiers(floors, 12, 1), Building.Top.MAST, seed);
                }
            }
        }
    }

    /** Pre-war apartment blocks: brick and limestone, ten to fifteen storeys, a setback penthouse, a water tank. */
    private static void prewar(List<Feature> out, int ox, int oz, int a, int c, int b, int d, int seed) {
        if (seed % 3 == 0) {
            int floors = 11 + seed % 5;
            add(out, ox, oz, a, c, b, d, floors, prewarLook(seed), Building.tiers(floors, floors - 1, 3), Building.Top.WATER_TOWER, seed);
            return;
        }
        // Two buildings sharing the block, of different heights and colours.
        int split = a + 8 + seed % 3;
        int f0 = 10 + seed % 4, f1 = 12 + (seed >> 2) % 4;
        add(out, ox, oz, a, c, split, d, f0, prewarLook(seed), Building.tiers(f0, f0 - 1, 2), Building.Top.WATER_TOWER, seed);
        add(out, ox, oz, split + 1, c, b, d, f1, prewarLook(seed + 3), Building.tiers(f1, f1 - 1, 2), Building.Top.PENTHOUSE, seed + 1);
    }

    /** Midtown: offices, hotels and older lofts of eight to sixteen storeys, two or three to a block. */
    private static void midtown(List<Feature> out, int ox, int oz, int a, int c, int b, int d, int seed) {
        int[][] parcels = switch (seed % 3) {
            case 0 -> new int[][] {{a, c, a + 8, d}, {a + 9, c, b, d}};
            case 1 -> new int[][] {{a, c, b, c + 8}, {a, c + 9, b, d}};
            default -> new int[][] {{a, c, a + 6, d}, {a + 7, c, a + 12, d}, {a + 13, c, b, d}};
        };
        for (int i = 0; i < parcels.length; i++) {
            int s = CityGrid.hash(seed, i);
            int[] p = parcels[i];
            switch (s % 5) {
                case 0 -> add(out, ox, oz, p[0], p[1], p[2], p[3], 8 + s % 6, office(s), Building.tiers(14), Building.Top.FLAT, s);
                case 1 -> add(out, ox, oz, p[0], p[1], p[2], p[3], 10 + s % 6, hotel(s), Building.tiers(16), Building.Top.BILLBOARD, s);
                case 2 -> add(out, ox, oz, p[0], p[1], p[2], p[3], 9 + s % 5, prewarLook(s), Building.tiers(14, 8 + s % 5, 1), Building.Top.WATER_TOWER, s);
                case 3 -> add(out, ox, oz, p[0], p[1], p[2], p[3], 12 + s % 5, deco(s), Building.tiers(17, 10, 1), Building.Top.SPIRE, s);
                default -> add(out, ox, oz, p[0], p[1], p[2], p[3], 6 + s % 3, brick(s), Building.tiers(9), Building.Top.WATER_TOWER, s);
            }
        }
    }

    /**
     * A residential block: a row of narrow buildings on its north street and another on its south, each its own width,
     * height and colour, as a real street grows one lot at a time. Brownstones with stoops, brick walk-ups with shops
     * and fire escapes, now and then a taller tenement.
     */
    private static void rows(List<Feature> out, int ox, int oz, int a, int c, int b, int d, int seed) {
        int midZ = c + 8;
        for (int row = 0; row < 2; row++) {
            int z0 = row == 0 ? c : midZ + 1, z1 = row == 0 ? midZ : d;
            int x = a, i = 0;
            while (x <= b) {
                int s = CityGrid.hash(seed, row, i, 9);
                int width = 5 + s % 3;
                if (b - (x + width - 1) < 5) width = b - x + 1;   // never leave a sliver
                int x1 = x + width - 1;
                switch (s % 7) {
                    case 0, 1 -> add(out, ox, oz, x, z0, x1, z1, 3 + s % 2, brownstone(s), Building.tiers(4), Building.Top.CHIMNEYS, s);
                    case 2 -> add(out, ox, oz, x, z0, x1, z1, 3, brownstone(s), Building.tiers(3), Building.Top.GARDEN, s);
                    case 3 -> add(out, ox, oz, x, z0, x1, z1, 7 + s % 3, prewarLook(s), Building.tiers(10), Building.Top.WATER_TOWER, s);
                    default -> add(out, ox, oz, x, z0, x1, z1, 4 + s % 3, brick(s), Building.tiers(6), s % 2 == 0 ? Building.Top.WATER_TOWER : Building.Top.FLAT, s);
                }
                x = x1 + 1;
                i++;
            }
        }
    }

    private static void add(List<Feature> out, int ox, int oz, int x0, int z0, int x1, int z1, int floors, Building.Look look,
                            int[] insets, Building.Top top, int seed) {
        boolean[] street = {z0 == oz + CityGrid.LOT_MIN, z1 == oz + CityGrid.LOT_MAX, x0 == ox + CityGrid.LOT_MIN, x1 == ox + CityGrid.LOT_MAX};
        int[] fitted = new int[floors + 1];
        for (int k = 0; k <= floors; k++) fitted[k] = Math.max(0, Math.min(insets[Math.min(k, insets.length - 1)], (Math.min(x1 - x0, z1 - z0) - 4) / 2));
        Building building = new Building(x0, z0, x1, z1, floors, look, fitted, top, street, seed, SHOPS);
        out.add(new Feature(x0 - 2, z0 - 2, x1 + 2, z1 + 2, building::draw));
    }

    // ------------------------------------------------------------------
    // Styles
    // ------------------------------------------------------------------

    /** The Odessa State Building: pale limestone piers running the full height, silver-grey spandrels, narrow windows. */
    static Building.Look state() {
        return new Building.Look(Blocks.POLISHED_DIORITE, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CHISELED_QUARTZ_BLOCK, null,
                Blocks.BLACK_STAINED_GLASS_PANE, Blocks.GLASS_PANE, false, Blocks.OCHRE_FROGLIGHT, Blocks.POLISHED_ANDESITE,
                Blocks.LIGHT_GRAY_CONCRETE, Blocks.POLISHED_DIORITE, null, 1, 0.38, Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE,
                Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.WAXED_COPPER_DOOR, Blocks.LIGHT_GRAY_CONCRETE, false);
    }

    /** The Bishop Building: white brick with dark bands under the windows and steel trim. */
    static Building.Look steel() {
        return new Building.Look(Blocks.SMOOTH_QUARTZ, Blocks.GRAY_CONCRETE, Blocks.IRON_BLOCK, null, Blocks.BLACK_STAINED_GLASS_PANE,
                Blocks.GLASS_PANE, false, Blocks.SEA_LANTERN, Blocks.POLISHED_ANDESITE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_QUARTZ, null,
                2, 0.4, Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE, Blocks.IRON_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS,
                Blocks.WAXED_COPPER_DOOR, Blocks.LIGHT_GRAY_CONCRETE, false);
    }

    /** A gothic tower: pale terracotta piers between narrow windows, rising unbroken to a copper roof. */
    static Building.Look gothic(int seed) {
        boolean cream = seed % 2 == 0;
        Block wall = cream ? Blocks.WHITE_TERRACOTTA : Blocks.CALCITE;
        return new Building.Look(wall, wall, cream ? Blocks.CUT_SANDSTONE : Blocks.POLISHED_DIORITE,
                cream ? Blocks.SMOOTH_SANDSTONE_STAIRS : Blocks.POLISHED_DIORITE_STAIRS, Blocks.GRAY_STAINED_GLASS_PANE, Blocks.GLASS_PANE,
                false, Blocks.OCHRE_FROGLIGHT, Blocks.POLISHED_ANDESITE, Blocks.LIGHT_GRAY_CONCRETE, wall, null, 1, 0.35,
                Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE, wall, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.WAXED_COPPER_DOOR,
                Blocks.LIGHT_GRAY_CONCRETE, false);
    }

    /**
     * Dark glass offices, as round Kirby Plaza: black mullions, smoked glass, and a grid of lit office windows (white
     * glass for the lit ones, many of them, as at the end of a working day).
     */
    static Building.Look bronze(int seed) {
        Block tint = seed % 3 == 0 ? Blocks.BROWN_STAINED_GLASS : seed % 3 == 1 ? Blocks.GRAY_STAINED_GLASS : Blocks.BLACK_STAINED_GLASS;
        return new Building.Look(Blocks.POLISHED_BLACKSTONE, Blocks.BLACK_STAINED_GLASS, Blocks.POLISHED_BLACKSTONE, null, tint,
                Blocks.WHITE_STAINED_GLASS, true, Blocks.SEA_LANTERN, Blocks.SMOOTH_STONE, Blocks.GRAY_CONCRETE, Blocks.POLISHED_BLACKSTONE,
                null, 2, 0.45, Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.QUARTZ_STAIRS,
                Blocks.QUARTZ_STAIRS, Blocks.WAXED_COPPER_DOOR, Blocks.GRAY_CONCRETE, false);
    }

    /** Pre-war apartments: brick over a limestone base, stone lintels and sills, a heavy cornice. */
    static Building.Look prewarLook(int seed) {
        Block[][] palettes = {
                {Blocks.BRICKS, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE},
                {Blocks.GRANITE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE},
                {Blocks.MUD_BRICKS, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE},
                {Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE},
                {Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE}};
        Block[] p = palettes[Math.floorMod(seed, palettes.length)];
        return new Building.Look(p[0], p[0], p[2], p[1], Blocks.GRAY_STAINED_GLASS_PANE, Blocks.GLASS_PANE, false, Blocks.OCHRE_FROGLIGHT,
                Blocks.SPRUCE_PLANKS, Blocks.GRAY_CONCRETE, p[0], Blocks.POLISHED_DEEPSLATE_STAIRS, 2, 0.5, Building.Ground.LOBBY,
                Blocks.POLISHED_GRANITE, p[2], Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_DOOR, Blocks.WHITE_TERRACOTTA, false);
    }

    /** Awning stripes: pairs of stairs, hung from the fascia. */
    private static final Block[][] AWNINGS = {
            {Blocks.RED_NETHER_BRICK_STAIRS, Blocks.QUARTZ_STAIRS}, {Blocks.DARK_PRISMARINE_STAIRS, Blocks.QUARTZ_STAIRS},
            {Blocks.WARPED_STAIRS, Blocks.QUARTZ_STAIRS}, {Blocks.CRIMSON_STAIRS, Blocks.CRIMSON_STAIRS},
            {Blocks.ACACIA_STAIRS, Blocks.ACACIA_STAIRS}, {Blocks.CHERRY_STAIRS, Blocks.QUARTZ_STAIRS},
            {Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS}, {Blocks.SPRUCE_STAIRS, Blocks.BIRCH_STAIRS}};

    /** Brick walk-ups: windows set back between brick piers, stone sills and lintels, shops below, fire escapes, a cornice. */
    static Building.Look brick(int seed) {
        Block[][] palettes = {
                {Blocks.BRICKS, Blocks.STONE_STAIRS, Blocks.SMOOTH_STONE},
                {Blocks.MUD_BRICKS, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE},
                {Blocks.TERRACOTTA, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE},
                {Blocks.WHITE_TERRACOTTA, Blocks.STONE_STAIRS, Blocks.LIGHT_GRAY_TERRACOTTA},
                {Blocks.BRICKS, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE},
                {Blocks.RED_TERRACOTTA, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE}};
        Block[] p = palettes[seed % palettes.length];
        Block[] awning = AWNINGS[(seed / 5) % AWNINGS.length];
        Block base = (seed / 7) % 2 == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.POLISHED_BLACKSTONE;
        Block cornice = (seed / 11) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.DARK_OAK_STAIRS;
        return new Building.Look(p[0], p[0], p[2], p[1], Blocks.GRAY_STAINED_GLASS_PANE, Blocks.GLASS_PANE, false, Blocks.OCHRE_FROGLIGHT,
                Blocks.SPRUCE_PLANKS, Blocks.GRAY_CONCRETE, p[0], cornice, 2, 0.42, Building.Ground.SHOP, base, base,
                awning[0], awning[1], (seed / 3) % 2 == 0 ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR, Blocks.WHITE_TERRACOTTA,
                (seed / 13) % 3 != 0);
    }

    /** Brownstone rows: raised doors up a stoop, tall parlour windows, a wooden cornice. */
    static Building.Look brownstone(int seed) {
        Block wall = seed % 3 == 0 ? Blocks.BROWN_TERRACOTTA : seed % 3 == 1 ? Blocks.GRANITE : Blocks.PACKED_MUD;
        return new Building.Look(wall, wall, wall, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.GRAY_STAINED_GLASS_PANE,
                Blocks.GLASS_PANE, false, Blocks.OCHRE_FROGLIGHT, Blocks.SPRUCE_PLANKS, Blocks.GRAY_CONCRETE, wall, Blocks.DARK_OAK_STAIRS,
                2, 0.5, Building.Ground.STOOP, Blocks.POLISHED_GRANITE, wall, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS,
                Blocks.DARK_OAK_DOOR, Blocks.WHITE_TERRACOTTA, false);
    }

    /** Art deco: pale stone piers running the full height, dark spandrels, narrow windows, a stepped crown and spire. */
    static Building.Look deco(int seed) {
        boolean warm = seed % 2 == 0;
        Block trim = warm ? Blocks.CUT_SANDSTONE : Blocks.POLISHED_DIORITE;
        return new Building.Look(warm ? Blocks.SMOOTH_SANDSTONE : Blocks.CALCITE, warm ? Blocks.BROWN_TERRACOTTA : Blocks.GRAY_TERRACOTTA,
                trim, null, Blocks.BLACK_STAINED_GLASS_PANE, Blocks.GLASS_PANE, false, Blocks.OCHRE_FROGLIGHT, Blocks.POLISHED_ANDESITE,
                Blocks.LIGHT_GRAY_CONCRETE, trim, null, 1, 0.3, Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE,
                warm ? Blocks.CHISELED_SANDSTONE : Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS,
                Blocks.WAXED_COPPER_DOOR, Blocks.LIGHT_GRAY_CONCRETE, false);
    }

    /** Glass curtain wall: thin mullions, tinted glass three rows tall, dark spandrel glass at each floor. */
    static Building.Look glass(int seed) {
        Block tint = seed % 3 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : seed % 3 == 1 ? Blocks.CYAN_STAINED_GLASS : Blocks.BLUE_STAINED_GLASS;
        return new Building.Look(Blocks.LIGHT_GRAY_CONCRETE, Blocks.GRAY_STAINED_GLASS, Blocks.LIGHT_GRAY_CONCRETE, null, tint,
                Blocks.WHITE_STAINED_GLASS, true, Blocks.SEA_LANTERN, Blocks.SMOOTH_STONE, Blocks.LIGHT_GRAY_CONCRETE,
                Blocks.LIGHT_GRAY_CONCRETE, null, 2, 0.25, Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE, Blocks.GRAY_CONCRETE,
                Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.WAXED_COPPER_DOOR, Blocks.GRAY_CONCRETE, false);
    }

    /** A midcentury office: wide windows in a concrete grid, a ledge under each. */
    static Building.Look office(int seed) {
        Block band = seed % 3 == 0 ? Blocks.WHITE_CONCRETE : seed % 3 == 1 ? Blocks.SMOOTH_STONE : Blocks.LIGHT_GRAY_CONCRETE;
        return new Building.Look(band, band, band, null, Blocks.BLACK_STAINED_GLASS_PANE, Blocks.GLASS_PANE, false,
                Blocks.SEA_LANTERN, Blocks.SMOOTH_STONE, Blocks.GRAY_CONCRETE, band, null, 3, 0.35, Building.Ground.LOBBY,
                Blocks.POLISHED_BLACKSTONE, band, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.WAXED_COPPER_DOOR,
                Blocks.LIGHT_GRAY_CONCRETE, false);
    }

    /** A hotel: narrow windows in a pale block, a red sign band over the lobby, a cornice. */
    static Building.Look hotel(int seed) {
        boolean quartz = seed % 2 != 0;
        Block wall = quartz ? Blocks.SMOOTH_QUARTZ : Blocks.WHITE_TERRACOTTA;
        return new Building.Look(wall, wall, Blocks.SMOOTH_STONE, quartz ? Blocks.QUARTZ_STAIRS : Blocks.STONE_STAIRS,
                Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.GLASS_PANE, false, Blocks.OCHRE_FROGLIGHT, Blocks.SPRUCE_PLANKS,
                Blocks.GRAY_CONCRETE, wall, Blocks.QUARTZ_STAIRS, 1, 0.55, Building.Ground.LOBBY, Blocks.POLISHED_BLACKSTONE,
                Blocks.RED_TERRACOTTA, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.DARK_OAK_DOOR,
                Blocks.WHITE_TERRACOTTA, false);
    }
}
