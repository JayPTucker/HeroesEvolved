package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.time.intro.city.CityCanvas;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import com.jayptucker.heroesevolved.time.intro.city.CityPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Brings down the actual Last Day city, in its own coordinates, before it is moved over the present bunker. */
final class OdessaDecay {
    private OdessaDecay() {}
    static final int TOP = 64;
    private static final int Y = CityGrid.STREET_Y;

    static Map<BlockPos, BlockState> collapse(CityCanvas plan) {
        Map<BlockPos, BlockState> cells = new HashMap<>();
        plan.planned().forEach((pos, state) -> {
            if (state.isAir() || state.getBlock() instanceof DoorBlock || !state.getFluidState().isEmpty()) return;
            int y = pos.getY() - Y, x = pos.getX(), z = pos.getZ();
            if (y > height(x, z)) return;
            // The old street trees burned; new trees grow from the broken paving below.
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) && outsideLot(x, z)) return;
            if (state.getBlock() instanceof BushBlock || state.getBlock() instanceof VineBlock) return;
            if (glass(state) && CityGrid.hash(x / 2, y / 2, z / 2, 713) % 100 >= 24) return;
            cells.put(pos, age(pos, state));
        });
        // A broken frame must not leave a lone pane or a detached crown hovering above the rubble.
        boolean changed;
        do {
            changed = cells.entrySet().removeIf(e -> pane(e.getValue()) && !framed(cells, e.getKey()));
        } while (changed);
        holdUp(cells);
        return cells;
    }

    /** Broad collapsed bays, with a low blast-facing side, rather than independent random holes. */
    private static int height(int x, int z) {
        int bx = CityGrid.bx(x), bz = CityGrid.bz(z);
        char kind = CityPlan.kind(bx, bz);
        int seed = CityGrid.hash(bx, bz, 701);
        int high = switch (kind) {
            case 'X' -> 55;
            case 'Y' -> 44;
            case 'T', 'D' -> 28 + seed % 13;
            case 'p', 'm', 'G' -> 19 + seed % 10;
            case 'H' -> 30;
            case 'C' -> 22;
            case 'A' -> 19;
            case 'P' -> 5;
            default -> 7 + seed % 10;
        };
        // Many ordinary buildings are only foundations or one surviving wall; the landmarks carry the skyline.
        if ("rmpD".indexOf(kind) >= 0 && seed % 5 <= 1) high = 3 + seed % 5;
        else if (kind == 'r' && seed % 5 == 2) high = 7;
        int u = CityGrid.lx(x), v = CityGrid.lz(z);
        // Whole rear corners remain, exposing the floors through the front and the missing middle.
        int bay = CityGrid.hash(Math.floorDiv(x, 5), Math.floorDiv(z, 5), 703) % 7;
        int cap = high - (u < 19 ? 4 : 0) - (v < 19 ? 4 : 0) + (bay % 3 - 1) * 3;
        if (bay <= 1 && u >= 11 && u <= 28 && v >= 11 && v <= 28) cap = 3;
        // Keep the Arms' western stair and the market's ground floor usable, but shear the east wing open.
        if (kind == 'A') cap = u <= 17 ? 18 : 7 + bay;
        if (kind == 'G' && u < 21) cap = Math.max(7, cap);
        if (outsideLot(x, z)) cap = Math.min(cap, kind == 'A' ? 6 : 2 + CityGrid.hash(x / 3, z / 3, 707) % 4);
        return Math.max(3, Math.min(TOP - 4, cap));
    }

    private static boolean outsideLot(int x, int z) {
        return CityGrid.lx(x) < CityGrid.LOT_MIN || CityGrid.lx(x) > CityGrid.LOT_MAX
                || CityGrid.lz(z) < CityGrid.LOT_MIN || CityGrid.lz(z) > CityGrid.LOT_MAX;
    }

    private static BlockState age(BlockPos p, BlockState state) {
        int n = CityGrid.hash(p.getX() / 2, p.getY() / 3, p.getZ() / 2, 709) % 100;
        Block b = state.getBlock();
        if (state.is(Blocks.YELLOW_CONCRETE)) return (p.getY() < Y ? n < 48 ? Blocks.YELLOW_TERRACOTTA : Blocks.GRAY_CONCRETE
                : n < 50 ? Blocks.TERRACOTTA : Blocks.WAXED_OXIDIZED_COPPER).defaultBlockState();
        if (state.getBlock() instanceof CarpetBlock && outsideLot(p.getX(), p.getZ())) return Blocks.MOSS_CARPET.defaultBlockState();
        if (state.is(Blocks.ORANGE_CONCRETE)) return (n < 50 ? Blocks.TERRACOTTA : Blocks.BROWN_TERRACOTTA).defaultBlockState();
        if (state.is(Blocks.WHITE_CONCRETE) && p.getY() < Y)
            return (n < 45 ? Blocks.LIGHT_GRAY_CONCRETE : n < 70 ? Blocks.ANDESITE : Blocks.WHITE_CONCRETE).defaultBlockState();
        if (state.getLightEmission() > 0) return Blocks.SMOOTH_STONE.defaultBlockState();
        if (state.is(Blocks.STONE_BRICKS)) return (n < 35 ? Blocks.MOSSY_STONE_BRICKS : n < 65 ? Blocks.CRACKED_STONE_BRICKS : b).defaultBlockState();
        if (state.is(Blocks.STONE_BRICK_STAIRS) && n < 40) return Blocks.MOSSY_STONE_BRICK_STAIRS.withPropertiesOf(state);
        if (state.is(Blocks.STONE_BRICK_SLAB) && n < 40) return Blocks.MOSSY_STONE_BRICK_SLAB.withPropertiesOf(state);
        if (state.is(Blocks.POLISHED_BLACKSTONE_BRICKS) && n < 45) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (state.is(Blocks.WHITE_CONCRETE) && n < 25) return (n < 12 ? Blocks.CALCITE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
        if (state.is(Blocks.IRON_BLOCK) && n < 35) return Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState();
        if (state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.WAXED_COPPER_BLOCK)) return Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState();
        if (b instanceof CandleBlock) return state.setValue(CandleBlock.LIT, false);
        return state;
    }

    private static boolean pane(BlockState s) { return s.getBlock() instanceof IronBarsBlock && !s.is(Blocks.IRON_BARS); }
    private static boolean glass(BlockState s) {
        return pane(s) || s.is(BlockTags.IMPERMEABLE);
    }
    private static boolean held(Map<BlockPos, BlockState> cells, BlockPos p) {
        BlockState s = cells.get(p);
        return s != null && (s.canOcclude() || s.getBlock() instanceof IronBarsBlock);
    }
    private static boolean framed(Map<BlockPos, BlockState> cells, BlockPos p) {
        return held(cells, p.west()) && held(cells, p.east()) || held(cells, p.north()) && held(cells, p.south());
    }

    private static void holdUp(Map<BlockPos, BlockState> cells) {
        Set<BlockPos> held = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        for (BlockPos p : cells.keySet()) if (p.getY() <= Y) { held.add(p); todo.add(p); }
        while (!todo.isEmpty()) {
            BlockPos p = todo.remove();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (cells.containsKey(q) && held.add(q)) todo.add(q);
            }
        }
        cells.keySet().retainAll(held);
    }

    /** Growth and fallen masonry share the same plan, so every prop has its support before placement. */
    static void reclaim(Map<BlockPos, BlockState> cells, int x0, int z0) {
        for (int x = x0; x < x0 + 32; x++)
            for (int z = z0; z < z0 + 32; z++) {
                if (primatech(x, z)) continue;
                boolean way = roadWay(x, z);
                int patch = weather(x, z);
                BlockPos road = new BlockPos(x, Y - 1, z);
                if (CityGrid.road(x, z) && patch < 62) {
                    cells.put(road, (patch < 24 ? Blocks.COARSE_DIRT : patch < 52 ? Blocks.GRASS_BLOCK : Blocks.COBBLED_DEEPSLATE).defaultBlockState());
                    BlockPos above = road.above();
                    if (patch >= 24 && patch < 44 && !cells.containsKey(above)) cells.put(above, Blocks.SHORT_GRASS.defaultBlockState());
                }
                if (way) continue;
                // Roots lifted the kerbs and sidewalk slabs; growth occupies the old slab's cell, never floats over it.
                BlockPos pavement = new BlockPos(x, Y, z);
                BlockState paving = cells.get(pavement);
                if (outsideLot(x, z) && patch < 58 && paving != null && paving.getBlock() instanceof SlabBlock) {
                    cells.put(road, Blocks.GRASS_BLOCK.defaultBlockState());
                    cells.remove(pavement);
                    if (patch < 42) cells.put(pavement, patch < 26 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                }
                // Low heaps collect beside walls and in collapsed bays; the centre of each road stays walkable.
                if (!outsideLot(x, z) && cells.containsKey(new BlockPos(x, Y, z))
                        && !cells.containsKey(new BlockPos(x, Y + 1, z)) && patch < 50) {
                    int depth = patch < 30 ? 2 : 1;
                    for (int y = 1; y <= depth; y++) {
                        BlockPos p = new BlockPos(x, Y + y, z);
                        if (cells.containsKey(p)) break;
                        cells.put(p, (y == depth && patch < 37 ? Blocks.MOSS_BLOCK
                                : patch % 3 == 0 ? Blocks.TUFF : patch % 3 == 1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.COBBLESTONE).defaultBlockState());
                    }
                }
                int top = top(cells, x, z);
                BlockPos p = new BlockPos(x, top, z);
                BlockState under = cells.get(p.below());
                if (under == null || !under.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, p.below())
                        || under.hasBlockEntity() || glass(under)) continue;
                if (patch < 62) cells.put(p.below(), Blocks.MOSS_BLOCK.defaultBlockState());
                if (patch < 35) cells.put(p, Blocks.FERN.defaultBlockState());
                else if (patch < 49) cells.put(p, Blocks.MOSS_CARPET.defaultBlockState());
            }
        // A tree growing through each second broken courtyard, rooted in the masonry rather than floating leaves.
        tree(cells, x0 + 23, z0 + 23);
        if (CityGrid.hash(x0, z0, 727) % 2 == 0) tree(cells, x0 + 15, z0 + 17);
        // Find exposed wall faces at the top of each column and let vines fall from a real full face.
        for (BlockPos p : List.copyOf(cells.keySet())) {
            if (p.getY() < Y + 3 || CityGrid.hash(p.getX(), p.getZ(), 731) % 3 != 0 || cells.containsKey(p.above())) continue;
            BlockState support = cells.get(p);
            if (!support.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, p)) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos q = p.relative(d);
                if (cells.containsKey(q) || primatech(q.getX(), q.getZ()) || roadWay(q.getX(), q.getZ())) continue;
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d.getOpposite()), true);
                int length = 3 + CityGrid.hash(q.getX(), q.getZ(), 733) % 8;
                for (int i = 0; i < length && q.getY() > Y + 1 && !cells.containsKey(q); i++, q = q.below()) cells.put(q, vine);
            }
        }
        // The power died with the city. Lichen lights the surviving ceilings, including the market and apartments.
        for (BlockPos p : List.copyOf(cells.keySet())) {
            if (p.getY() < Y + 4 || Math.floorMod(p.getX(), 3) != 0 || Math.floorMod(p.getZ(), 3) != 0) continue;
            if (!cells.get(p).isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, p)) continue;
            if (!cells.containsKey(p.below()) && !cells.containsKey(p.below(2)))
                cells.put(p.below(), Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
        }
    }

    /** Soil spreads in broad patches with ragged edges instead of an even three-block checkerboard. */
    static int weather(int x, int z) {
        int a = Math.floorDiv(x, 7), b = Math.floorDiv(z, 7);
        double u = Math.floorMod(x, 7) / 7.0, v = Math.floorMod(z, 7) / 7.0;
        u = u * u * (3 - 2 * u); v = v * v * (3 - 2 * v);
        double north = CityGrid.chance(a, b, 719) * (1 - u) + CityGrid.chance(a + 1, b, 719) * u;
        double south = CityGrid.chance(a, b + 1, 719) * (1 - u) + CityGrid.chance(a + 1, b + 1, 719) * u;
        return (int) (100 * (north * (1 - v) + south * v)) + CityGrid.hash(x, z, 721) % 9 - 4;
    }

    private static int top(Map<BlockPos, BlockState> cells, int x, int z) {
        for (int y = Y + TOP; y >= Y - 1; y--) if (cells.containsKey(new BlockPos(x, y, z))) return y + 1;
        return Y;
    }

    private static void tree(Map<BlockPos, BlockState> cells, int x, int z) {
        if (primatech(x, z)) return;
        int y = top(cells, x, z);
        if (y > Y + 5) return;
        BlockPos root = new BlockPos(x, y - 1, z);
        cells.put(root, Blocks.ROOTED_DIRT.defaultBlockState());
        for (int h = 1; h <= 5; h++) cells.put(root.above(h), Blocks.OAK_LOG.defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 6; dy++) {
            if (dx * dx + dz * dz + (dy - 4.5) * (dy - 4.5) > 7) continue;
            cells.putIfAbsent(root.offset(dx, dy, dz), CityCanvas.leaves(Blocks.OAK_LEAVES));
        }
    }

    static boolean primatech(int x, int z) {
        return x >= CityGrid.PRIMATECH_LOT_X0 && x <= CityGrid.PRIMATECH_LOT_X1
                && z >= CityGrid.PRIMATECH_LOT_Z0 && z <= CityGrid.PRIMATECH_LOT_Z1;
    }

    static boolean roadWay(int x, int z) {
        if (!CityGrid.road(x, z)) return false;
        int u = CityGrid.lx(x), v = CityGrid.lz(z);
        return u >= 2 && u <= 5 || v >= 2 && v <= 5;
    }
}
