package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a drawn patch of the city for the mistakes a player notices: blocks that hang in the air with nothing holding
 * them, blocks that could not stay where they are (a wall sign with no wall, a door with no floor), glass panes that end
 * in a stub, and doors that open into a wall. Used by the game tests and the city preview. Asked to, it also finds dark
 * floor: cells a monster could stand on with no block light and no sky light, which spawn monsters day and night.
 */
public final class CityAudit {
    private static final int TOP = 230;

    /** What was found, by kind, with the first few places of each. */
    public record Report(Map<String, List<BlockPos>> found, Map<String, Integer> counts) {
        public int total() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
        @Override public String toString() {
            StringBuilder out = new StringBuilder();
            counts.forEach((kind, n) -> {
                out.append(kind).append(": ").append(n);
                List<BlockPos> some = found.get(kind);
                for (int i = 0; i < Math.min(8, some.size()); i++) out.append(i == 0 ? " at " : ", ").append(some.get(i).toShortString());
                out.append('\n');
            });
            return out.length() == 0 ? "clean" : out.toString();
        }
    }

    private final Map<String, List<BlockPos>> found = new LinkedHashMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    private CityAudit() {}

    private void flag(String kind, BlockPos pos) {
        counts.merge(kind, 1, Integer::sum);
        List<BlockPos> list = found.computeIfAbsent(kind, k -> new ArrayList<>());
        if (list.size() < 64) list.add(pos.immutable());
    }

    /** Audits the columns from (x0, z0) to (x1, z1), from the street's base layer up. */
    public static Report run(ServerLevel level, int x0, int z0, int x1, int z1) {
        return run(level, x0, CityGrid.STREET_Y - 1, z0, x1, TOP, z1);
    }

    /**
     * Audits a box. Whatever touches its bottom layer counts as standing on the ground; for a build underground (the
     * bunker), the rock round it holds everything up, so what this finds there are the things that can't stay and the
     * doors and panes.
     */
    public static Report run(ServerLevel level, int x0, int bottom, int z0, int x1, int top, int z1) {
        return run(level, x0, bottom, z0, x1, top, z1, false);
    }

    /**
     * As {@link #run(ServerLevel, int, int, int, int, int, int)}, and with {@code darkness} also reports dark floor. Run that
     * only once the light has settled (some ticks after building), or every new cell reads as dark.
     */
    public static Report run(ServerLevel level, int x0, int bottom, int z0, int x1, int top, int z1, boolean darkness) {
        CityAudit audit = new CityAudit();
        audit.darkness = darkness;
        audit.scan(level, x0, z0, x1, z1, bottom, top);
        return new Report(audit.found, audit.counts);
    }

    private boolean darkness;

    /** A cell where a zombie could appear: room for it to stand, a floor that allows spawning, and no light at all. */
    private boolean dark(ServerLevel level, BlockPos.MutableBlockPos pos, BlockState state) {
        BlockPos at = pos.immutable(), below = at.below(), above = at.above();
        BlockState under = level.getBlockState(below), over = level.getBlockState(above);
        if (!under.isValidSpawn(level, below, net.minecraft.world.entity.EntityType.ZOMBIE)) return false;
        if (!net.minecraft.world.level.NaturalSpawner.isValidEmptySpawnBlock(level, at, state, state.getFluidState(), net.minecraft.world.entity.EntityType.ZOMBIE)
                || !net.minecraft.world.level.NaturalSpawner.isValidEmptySpawnBlock(level, above, over, over.getFluidState(), net.minecraft.world.entity.EntityType.ZOMBIE))
            return false;
        return level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at) == 0
                && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at) == 0;
    }

    private void scan(ServerLevel level, int x0, int z0, int x1, int z1, int bottom, int top) {
        int w = x1 - x0 + 1, d = z1 - z0 + 1, h = top - bottom + 1;
        BitSet solid = new BitSet(w * d * h), reached = new BitSet(w * d * h);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = bottom; y <= top; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) {
                        if (darkness && y > bottom && dark(level, pos, state)) flag("dark floor (monsters spawn)", pos);
                        continue;
                    }
                    int i = ((x - x0) * d + (z - z0)) * h + (y - bottom);
                    solid.set(i);
                    if (y == bottom) { reached.set(i); queue.add(i); }
                    check(level, pos, state);
                }
        // Everything joined to the ground, face to face. What is left over hangs in the air.
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int y = i % h, z = (i / h) % d, x = i / h / d;
            for (int[] s : steps) {
                int nx = x + s[0], ny = y + s[1], nz = z + s[2];
                if (nx < 0 || nx >= w || nz < 0 || nz >= d || ny < 0 || ny >= h) continue;
                int j = (nx * d + nz) * h + ny;
                if (solid.get(j) && !reached.get(j)) { reached.set(j); queue.add(j); }
            }
        }
        // Report one place per floating piece (the first block of each), and how big it is.
        BitSet seen = new BitSet(w * d * h);
        for (int i = solid.nextSetBit(0); i >= 0; i = solid.nextSetBit(i + 1)) {
            if (reached.get(i) || seen.get(i)) continue;
            int size = 0;
            boolean touchesEdge = false;
            queue.add(i);
            seen.set(i);
            while (!queue.isEmpty()) {
                int k = queue.poll();
                size++;
                int y = k % h, z = (k / h) % d, x = k / h / d;
                if (x == 0 || x == w - 1 || z == 0 || z == d - 1) touchesEdge = true;
                for (int[] s : steps) {
                    int nx = x + s[0], ny = y + s[1], nz = z + s[2];
                    if (nx < 0 || nx >= w || nz < 0 || nz >= d || ny < 0 || ny >= h) continue;
                    int j = (nx * d + nz) * h + ny;
                    if (solid.get(j) && !reached.get(j) && !seen.get(j)) { seen.set(j); queue.add(j); }
                }
            }
            int y = i % h, z = (i / h) % d, x = i / h / d;
            // A piece cut off by the edge of the audited patch may be held from outside it.
            if (touchesEdge) continue;
            flag(size == 1 ? "floating block" : "floating piece", new BlockPos(x0 + x, bottom + y, z0 + z));
        }
    }

    private void check(ServerLevel level, BlockPos.MutableBlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (!state.canSurvive(level, pos)) flag("can't stay (" + name(block) + ")", pos);
        // Iron bars stand alone on purpose (lamp poles, masts); a glass pane on its own is a window that lost its frame.
        if (block instanceof IronBarsBlock && block != net.minecraft.world.level.block.Blocks.IRON_BARS) {
            int joins = 0;
            for (var property : new net.minecraft.world.level.block.state.properties.BooleanProperty[] {
                    IronBarsBlock.NORTH, IronBarsBlock.SOUTH, IronBarsBlock.EAST, IronBarsBlock.WEST})
                if (state.getValue(property)) joins++;
            if (joins == 0) flag("lone pane post (" + name(block) + ")", pos);
        }
        if (block instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockState upper = level.getBlockState(pos.above());
            if (!(upper.getBlock() instanceof DoorBlock)) flag("door missing its top", pos);
            Direction facing = state.getValue(DoorBlock.FACING);
            // Both sides must be walkable: at most a half-block step at the door's foot, and clear at head height.
            for (Direction side : new Direction[] {facing, facing.getOpposite()}) {
                BlockPos foot = pos.relative(side), head = foot.above();
                var low = level.getBlockState(foot).getCollisionShape(level, foot);
                if (!low.isEmpty() && low.max(Direction.Axis.Y) > 0.5
                        || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                    flag("door opens into a wall", pos);
                    return;
                }
                // ...and there must be something to stand on: no door opens onto a drop.
                BlockPos under = foot.below();
                if (low.isEmpty() && level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
                    flag("door opens onto a drop", pos);
                    return;
                }
            }
        }
    }

    private static String name(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
    }
}
