package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.structure.RuinWater;
import com.jayptucker.heroesevolved.time.intro.city.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Odessa's original footprints, swallowed by the overworld's hills and woods. A saved cursor builds it only once. */
public final class OdessaRuins extends SavedData {
    private static final String NAME = "heroesevolved_odessa_ruins";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<BlockPos> ORDER = order();
    /** Primatech's shared translation. Its y is the lobby floor; other blocks find their own buried footings. */
    private BlockPos origin = BlockPos.ZERO;
    private boolean started;
    private int next, cleaned;
    private final int[] floors = new int[64];
    private final Map<String, BlockPos> journals = new LinkedHashMap<>();

    public static OdessaRuins get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(OdessaRuins::new, OdessaRuins::load), NAME);
    }

    /** Only a newly built bunker opts in. Neither old saves nor later logins redraw a finished street. */
    static void start(ServerLevel level, BlockPos bunkerOrigin, int lobbyFloor) {
        OdessaRuins ruins = get(level);
        if (ruins.started) return;
        ruins.origin = new BlockPos(bunkerOrigin.getX() + PrimatechRuin.DX, lobbyFloor, bunkerOrigin.getZ() + PrimatechRuin.DZ);
        Arrays.fill(ruins.floors, lobbyFloor);
        ruins.started = true;
        ruins.setDirty();
    }

    public boolean complete() { return started && next >= ORDER.size() && cleaned >= ORDER.size(); }
    public boolean started() { return started; }
    public int completedBlocks() { return next; }
    public BlockPos origin() { return origin; }
    public Map<String, BlockPos> journals() { return Map.copyOf(journals); }
    public int floor(int bx, int bz) { return floors[bz * 8 + bx]; }

    public static BlockPos at(BlockPos origin, int cityX, int cityY, int cityZ) {
        return origin.offset(cityX, cityY - CityGrid.STREET_Y, cityZ);
    }

    private static List<BlockPos> order() {
        List<BlockPos> blocks = new ArrayList<>();
        for (int bz = 0; bz < 8; bz++) for (int bx = 0; bx < 8; bx++) blocks.add(new BlockPos(bx, 0, bz));
        blocks.sort(Comparator.comparingDouble(p -> Math.pow(p.getX() - 3.5, 2) + Math.pow(p.getZ() - 7, 2)));
        return List.copyOf(blocks);
    }

    public void tickBuild(ServerLevel level) {
        if (!started || complete()) return;
        if (next >= ORDER.size()) {
            BlockPos block = ORDER.get(cleaned);
            finishBlock(level, block.getX(), block.getZ());
            cleaned++;
            setDirty();
            if (complete()) HeroesEvolved.LOGGER.info("Finished Odessa's ruined city around the stasis bunker: {} blocks", next);
            return;
        }
        BlockPos block = ORDER.get(next);
        int bx = block.getX(), bz = block.getZ();
        floors[bz * 8 + bx] = buildBlock(level, origin, bx, bz, journals);
        next++;
        setDirty();
        if (next == ORDER.size()) HeroesEvolved.LOGGER.info("Built Odessa's {} ruined city blocks; settling the edges", next);
    }

    /** A neighbour can be in a later city block: settle seams only after every block has reached its final shape. */
    private void finishBlock(ServerLevel level, int bx, int bz) {
        int x0 = CityGrid.originX(bx), z0 = CityGrid.originZ(bz), floor = floor(bx, bz);
        for (int cx = (origin.getX() + x0 - 1) >> 4; cx <= (origin.getX() + x0 + 32) >> 4; cx++)
            for (int cz = (origin.getZ() + z0 - 1) >> 4; cz <= (origin.getZ() + z0 + 32) >> 4; cz++) level.getChunk(cx, cz);
        Set<BlockPos> props = new HashSet<>();
        for (int x = x0; x < x0 + 32; x++) for (int z = z0; z < z0 + 32; z++) {
            if (OdessaDecay.primatech(x, z)) continue;
            for (int y = Math.max(level.getMinBuildHeight(), floor - 4); y <= floor + OdessaDecay.TOP; y++) {
                BlockPos p = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
                BlockState s = level.getBlockState(p);
                if (!s.isAir()) props.add(p);
            }
        }
        settle(level, props);
        dropLoose(level, props);
        settle(level, props);
    }

    /** Small disconnected branches and rubble fall away; connected hills, rooted trees and large landforms stay whole. */
    private static void dropLoose(ServerLevel level, Set<BlockPos> props) {
        Set<BlockPos> held = new HashSet<>();
        for (BlockPos seed : props) {
            if (held.contains(seed) || level.getBlockState(seed).isAir()) continue;
            Set<BlockPos> piece = new HashSet<>();
            ArrayDeque<BlockPos> todo = new ArrayDeque<>();
            piece.add(seed); todo.add(seed);
            boolean rooted = false;
            while (!todo.isEmpty() && !rooted) {
                BlockPos p = todo.remove();
                for (Direction side : Direction.values()) {
                    BlockPos q = p.relative(side);
                    if (!level.hasChunkAt(q)) { rooted = true; break; }
                    if (level.getBlockState(q).isAir() || piece.contains(q)) continue;
                    if (held.contains(q) || piece.size() >= 64) { rooted = true; break; }
                    piece.add(q); todo.add(q);
                }
            }
            if (rooted) held.addAll(piece);
            else for (BlockPos p : piece) level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    public static int buildBlock(ServerLevel level, BlockPos origin, int bx, int bz) {
        return buildBlock(level, origin, bx, bz, new HashMap<>());
    }

    private static int buildBlock(ServerLevel level, BlockPos origin, int bx, int bz, Map<String, BlockPos> journals) {
        int x0 = CityGrid.originX(bx), z0 = CityGrid.originZ(bz), x1 = x0 + 31, z1 = z0 + 31;
        for (int cx = (origin.getX() + x0 - 3) >> 4; cx <= (origin.getX() + x1 + 3) >> 4; cx++)
            for (int cz = (origin.getZ() + z0 - 3) >> 4; cz <= (origin.getZ() + z1 + 3) >> 4; cz++) level.getChunk(cx, cz);
        Terrain terrain = new Terrain(level, origin, x0, z0);
        char kind = CityPlan.kind(bx, bz);
        int floor = kind == 'Q' ? origin.getY() : terrain.footing(bx, bz);
        BlockPos local = new BlockPos(origin.getX(), floor, origin.getZ());
        CityCanvas plan = CityCanvas.plan(level, x0 - 2, z0 - 2, x1 + 2, z1 + 2);
        CityDraw.draw(plan);
        Map<BlockPos, BlockState> cells = OdessaDecay.collapse(plan);
        OdessaDecay.reclaim(cells, x0, z0);
        cells.replaceAll((p, state) -> RuinMaterials.safe(state));
        Set<BlockPos> placed = new HashSet<>();

        // The hill buries the lower floors and stays. Only the building's exposed downhill footing reaches to soil.
        if ("KPSLUQ".indexOf(kind) < 0)
            for (int x = x0 + 11; x <= x0 + 28; x++) for (int z = z0 + 11; z <= z0 + 28; z++) {
                BlockState base = cells.get(new BlockPos(x, CityGrid.STREET_Y, z));
                if (base == null || !base.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
                for (int y = terrain.ground(x, z); y < floor; y++) put(level, new BlockPos(origin.getX() + x, y, origin.getZ() + z),
                        (y == floor - 1 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState(), placed);
            }

        cells.forEach((p, state) -> {
            int x = p.getX(), z = p.getZ(), y = p.getY();
            if (x < x0 || x > x1 || z < z0 || z > z1 || OdessaDecay.primatech(x, z) || y < CityGrid.STREET_Y) return;
            boolean lot = CityGrid.lx(x) >= 11 && CityGrid.lx(x) <= 28 && CityGrid.lz(z) >= 11 && CityGrid.lz(z) <= 28;
            if (y == CityGrid.STREET_Y && (!lot || "KPSLUQ".indexOf(kind) >= 0)) return;
            BlockPos world = at(local, x, y, z);
            if (world.getY() < terrain.ground(x, z)) return;
            // Keep the woods rooted and whole where trees have grown through former rooms and roofs.
            BlockState old = level.getBlockState(world);
            if (old.is(BlockTags.LOGS) || old.is(BlockTags.LEAVES)) return;
            put(level, world, state, placed);
        });

        // Road fragments follow today's contours and thin out at the outskirts, without clearing a grid in the woods.
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (OdessaDecay.primatech(x, z) || !CityGrid.road(x, z)) continue;
            int edge = Math.min(Math.min(x - CityGrid.MIN, CityGrid.MAX - x), Math.min(z - CityGrid.MIN, CityGrid.MAX - z));
            int n = OdessaDecay.weather(x, z);
            if (n < 48 || CityGrid.hash(x / 3, z / 3, 751) % 18 > edge) continue;
            BlockPos ground = new BlockPos(origin.getX() + x, terrain.ground(x, z) - 1, origin.getZ() + z);
            BlockState skin = level.getBlockState(ground), over = level.getBlockState(ground.above());
            if (!skin.getFluidState().isEmpty() || !skin.isSolid() || skin.is(BlockTags.LOGS) || skin.is(BlockTags.LEAVES)
                    || over.is(BlockTags.LOGS) || over.is(BlockTags.LEAVES)) continue;
            BlockState oldRoad = cells.get(new BlockPos(x, CityGrid.STREET_Y - 1, z));
            BlockState paving = n < 62 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 76 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                    : oldRoad == null ? Blocks.COBBLESTONE.defaultBlockState() : oldRoad;
            put(level, ground, paving, placed);
            for (int h = 1; h <= 2; h++) {
                BlockPos plant = ground.above(h);
                if (!level.getBlockState(plant).canSurvive(level, plant)) put(level, plant, Blocks.AIR.defaultBlockState(), placed);
            }
        }
        for (int pass = 0; pass < 2; pass++) { dropDetached(level, placed); settle(level, placed); }
        plan.plannedSigns().forEach((p, text) -> {
            BlockPos world = at(local, p.getX(), p.getY(), p.getZ());
            if (placed.contains(world) && level.getBlockEntity(world) instanceof SignBlockEntity sign) {
                sign.setText(text.setHasGlowingText(false), true); sign.setWaxed(true); sign.setChanged();
            }
        });
        if ("rmpGTXYD".indexOf(kind) >= 0) {
            BlockPos p = RuinWater.cache(level, cache(level, local, x0 + 13, z0 + 25, terrain, cells));
            OdessaCaches.supplies(level, p, level.getSeed() ^ p.asLong());
            if (RuinWater.water(level, p.below())) RuinWater.driftwood(level, p);
        }
        for (var journal : OdessaCaches.JOURNALS) {
            if (CityGrid.bx(journal.x()) != bx || CityGrid.bz(journal.z()) != bz) continue;
            BlockPos pos = RuinWater.cache(level, cache(level, local, journal.x(), journal.z(), terrain, cells));
            OdessaCaches.place(level, pos, journal);
            if (RuinWater.water(level, pos.below())) RuinWater.driftwood(level, pos);
            journals.put(journal.title(), pos);
        }
        return floor;
    }

    /** A surviving floor above the new soil, or the exposed top of a cupboard half buried in the room's sediment. */
    private static BlockPos cache(ServerLevel level, BlockPos local, int x, int z, Terrain terrain, Map<BlockPos, BlockState> cells) {
        for (int y = CityGrid.STREET_Y + 1; y < CityGrid.STREET_Y + OdessaDecay.TOP; y++) {
            BlockPos source = new BlockPos(x, y, z), p = at(local, x, y, z);
            if (p.getY() < terrain.ground(x, z) || !cells.containsKey(source.below()) || cells.containsKey(source) || cells.containsKey(source.above())) continue;
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return p;
        }
        for (int radius = 0; radius <= 3; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int nx = x + dx, nz = z + dz;
            if (nx < terrain.x0 || nx >= terrain.x0 + 32 || nz < terrain.z0 || nz >= terrain.z0 + 32) continue;
            BlockPos p = new BlockPos(local.getX() + nx, terrain.ground(nx, nz) - 1, local.getZ() + nz);
            if (!level.getBlockState(p.above()).is(BlockTags.LOGS) && !level.getBlockState(p).is(BlockTags.LOGS)
                    && !level.getBlockState(p.below()).is(BlockTags.LOGS)) return p;
        }
        return new BlockPos(local.getX() + x, terrain.ground(x, z) - 1, local.getZ() + z);
    }

    /** First free cell above solid ground, including the seabed, ignoring water, woods and undergrowth. */
    public static int ground(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        while (y > level.getMinBuildHeight()) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.isAir() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES)
                    && !s.canBeReplaced() && !(RuinWater.water(level, new BlockPos(x, y, z)) && s.getCollisionShape(level, new BlockPos(x, y, z)).isEmpty())) break;
            y--;
        }
        return y + 1;
    }

    private static final class Terrain {
        final ServerLevel level;
        final int[][] heights = new int[32][32];
        final int x0, z0;
        Terrain(ServerLevel level, BlockPos origin, int x0, int z0) {
            this.level = level; this.x0 = x0; this.z0 = z0;
            for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++)
                heights[x][z] = OdessaRuins.ground(level, origin.getX() + x0 + x, origin.getZ() + z0 + z);
        }
        int ground(int x, int z) { return heights[x - x0][z - z0]; }
        int footing(int bx, int bz) {
            List<Integer> samples = new ArrayList<>();
            for (int x = 11; x <= 28; x++) for (int z = 11; z <= 28; z++) samples.add(heights[x][z]);
            Collections.sort(samples);
            return Math.max(level.getMinBuildHeight() + 6, samples.get(samples.size() / 4) - 2 - CityGrid.hash(bx, bz, 743) % 3);
        }
    }

    private static void put(ServerLevel level, BlockPos p, BlockState s, Set<BlockPos> placed) {
        if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p)) return;
        BlockState old = level.getBlockState(p);
        if (old.is(BlockTags.LOGS) || old.is(BlockTags.LEAVES)) return;
        if (!old.isAir() && old.getDestroySpeed(level, p) < 0) return;
        if (RuinWater.water(level, p)) s = RuinWater.flooded(s);
        if (old.getBlock() instanceof DoublePlantBlock) {
            BlockPos partner = old.getValue(DoublePlantBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? p.above() : p.below();
            if (level.getBlockState(partner).is(old.getBlock())) level.setBlock(partner, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        if (old != s) { level.setBlock(p, s, FLAGS); if (!s.isAir()) placed.add(p.immutable()); }
    }

    private static void settle(ServerLevel level, Set<BlockPos> placed) {
        List<BlockPos> ordered = placed.stream().sorted(Comparator.comparingInt(BlockPos::getY)).toList();
        for (int pass = 0; pass < 4; pass++) for (BlockPos p : pass % 2 == 0 ? ordered : ordered.reversed()) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (!s.canSurvive(level, p) || s.getBlock() instanceof FallingBlock && !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS); continue;
            }
            BlockState joined = Block.updateFromNeighbourShapes(s, level, p);
            if (joined != s) level.setBlock(p, joined, FLAGS);
            if (joined.getBlock() instanceof IronBarsBlock && !joined.is(Blocks.IRON_BARS)
                    && !joined.getValue(IronBarsBlock.NORTH) && !joined.getValue(IronBarsBlock.SOUTH)
                    && !joined.getValue(IronBarsBlock.EAST) && !joined.getValue(IronBarsBlock.WEST)) level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** Terrain burial can sever a prop from a floor that collapsed: it falls, rather than hovering over a hollow. */
    private static void dropDetached(ServerLevel level, Set<BlockPos> placed) {
        placed.removeIf(p -> level.getBlockState(p).isAir());
        Set<BlockPos> held = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        for (BlockPos p : placed) for (Direction side : Direction.values()) {
            BlockPos q = p.relative(side);
            if (!placed.contains(q) && !level.getBlockState(q).isAir() && held.add(p)) { todo.add(p); break; }
        }
        while (!todo.isEmpty()) {
            BlockPos p = todo.remove();
            for (Direction side : Direction.values()) {
                BlockPos q = p.relative(side);
                if (placed.contains(q) && held.add(q)) todo.add(q);
            }
        }
        for (BlockPos p : placed) if (!held.contains(p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("started", started); tag.putLong("origin", origin.asLong()); tag.putInt("next", next); tag.putInt("cleaned", cleaned);
        tag.putIntArray("floors", floors);
        CompoundTag books = new CompoundTag(); journals.forEach((title, p) -> books.putLong(title, p.asLong())); tag.put("journals", books);
        return tag;
    }

    public static OdessaRuins load(CompoundTag tag, HolderLookup.Provider registries) {
        OdessaRuins ruins = new OdessaRuins();
        ruins.started = tag.getBoolean("started"); ruins.origin = BlockPos.of(tag.getLong("origin"));
        ruins.next = Math.max(0, Math.min(ORDER.size(), tag.getInt("next")));
        ruins.cleaned = Math.max(0, Math.min(ORDER.size(), tag.getInt("cleaned")));
        Arrays.fill(ruins.floors, ruins.origin.getY());
        int[] floors = tag.getIntArray("floors"); if (floors.length == 64) System.arraycopy(floors, 0, ruins.floors, 0, 64);
        CompoundTag books = tag.getCompound("journals"); for (String title : books.getAllKeys()) ruins.journals.put(title, BlockPos.of(books.getLong(title)));
        return ruins;
    }
}
