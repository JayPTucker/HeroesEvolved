package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.LastDayGuidePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The Last Day's clairvoyance: a thread of light that shows each player the way to where they should go next. For each
 * goal the day sets (the lobby, the market's checkout and the avenue out front, someone trapped, Primatech's stair, the vault, the stasis hall) it
 * floods a walking-distance map out from the goal through the box the way lies in, over every cell a person can stand
 * in (two blocks of room above something to stand on, a step up or down at a time, through doors), once; then each
 * player's route is a walk downhill on it from where they stand, sent as its corners ({@link LastDayGuidePayload}).
 * A map is kept until the street changes under it ({@link #forget}).
 */
final class LastDayGuide {
    /** Where someone should be heading, and the box the way there lies in. */
    record Goal(BlockPos at, BoundingBox box) {}

    private static final class Field {
        final BlockPos goal;
        final BoundingBox box;
        final int nx, ny, nz;
        final int[] dist;
        Field(BlockPos goal, BoundingBox box) {
            this.goal = goal; this.box = box;
            nx = box.getXSpan(); ny = box.getYSpan(); nz = box.getZSpan();
            dist = new int[nx * ny * nz];
            java.util.Arrays.fill(dist, Integer.MAX_VALUE);
        }
        boolean inside(int x, int y, int z) { return box.isInside(x, y, z); }
        int index(int x, int y, int z) { return ((x - box.minX()) * ny + (y - box.minY())) * nz + (z - box.minZ()); }
        int at(int x, int y, int z) { return inside(x, y, z) ? dist[index(x, y, z)] : Integer.MAX_VALUE; }
    }

    private final Map<Goal, Field> fields = new HashMap<>();
    /** What each player was last sent, so nothing goes out while it's the same. */
    private final Map<UUID, List<Vec3>> sent = new HashMap<>();

    /** Maps flooded this tick: one at most, so several new goals at once never stall the server. */
    private int floods;

    /** The street changed (rubble came down, a door opened): every map is drawn again when next asked for. */
    void forget() { fields.clear(); }

    /** A new tick: another map may be flooded. */
    void tick() { floods = 0; }

    /** Sends {@code player} the way to {@code goal} (or clears their thread, for null). */
    void guide(ServerLevel city, ServerPlayer player, Goal goal) {
        List<Vec3> route = goal == null ? List.of() : route(city, goal, player.position());
        if (route == null) return;   // Its map waits for a later tick.
        List<Vec3> last = sent.get(player.getUUID());
        if (Objects.equals(last, route)) return;
        sent.put(player.getUUID(), route);
        PacketDistributor.sendToPlayer(player, new LastDayGuidePayload(route));
    }

    /** Clears everyone's thread. */
    void clear(List<ServerPlayer> players) {
        for (ServerPlayer p : players) if (!List.of().equals(sent.get(p.getUUID()))) PacketDistributor.sendToPlayer(p, new LastDayGuidePayload(List.of()));
        sent.clear();
    }

    /**
     * The way from {@code from} to the goal, as the corners of a walk downhill on its map (empty if there's no way, null
     * if its map can't be drawn this tick).
     */
    List<Vec3> route(ServerLevel city, Goal goal, Vec3 from) {
        Field f = fields.get(goal);
        if (f == null) {
            if (floods++ > 0) return null;
            f = flood(city, goal);
            fields.put(goal, f);
        }
        BlockPos start = nearest(f, BlockPos.containing(from));
        if (start == null) return List.of();
        List<BlockPos> cells = new ArrayList<>();
        BlockPos at = start;
        for (int step = 0; step < 600 && at != null; step++) {
            cells.add(at);
            if (f.at(at.getX(), at.getY(), at.getZ()) == 0) break;
            at = downhill(f, at);
        }
        if (cells.size() < 2) return List.of();
        // Only the corners: where the way turns or climbs, and every few blocks along a straight run so it hugs the floor.
        List<Vec3> out = new ArrayList<>();
        int sinceKept = 0;
        for (int i = 0; i < cells.size(); i++) {
            BlockPos c = cells.get(i);
            boolean keep = i == 0 || i == cells.size() - 1 || ++sinceKept >= 6;
            if (!keep) {
                BlockPos a = cells.get(i - 1), b = cells.get(i + 1);
                keep = !c.subtract(a).equals(b.subtract(c));
            }
            if (!keep) continue;
            sinceKept = 0;
            out.add(new Vec3(c.getX() + .5, c.getY() + floor(city, c), c.getZ() + .5));
            if (out.size() >= LastDayGuidePayload.MAX) break;
        }
        return out;
    }

    /** The height of the floor in a standing cell (a slab or a stair's top, say), above its bottom. */
    private static double floor(ServerLevel city, BlockPos feet) {
        VoxelShape own = city.getBlockState(feet).getCollisionShape(city, feet);
        return own.isEmpty() ? 0 : own.max(net.minecraft.core.Direction.Axis.Y);
    }

    /** The cell nearest {@code pos} (within two blocks) that the map reaches. */
    private static BlockPos nearest(Field f, BlockPos pos) {
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    int d = f.at(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (d == Integer.MAX_VALUE) continue;
                    int score = d + 4 * (Math.abs(dx) + Math.abs(dy) + Math.abs(dz));
                    if (score < bestD) { bestD = score; best = pos.offset(dx, dy, dz); }
                }
        return best;
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static BlockPos downhill(Field f, BlockPos at) {
        int here = f.at(at.getX(), at.getY(), at.getZ());
        BlockPos best = null;
        int bestD = here;
        for (int[] s : STEPS)
            for (int dy = -1; dy <= 1; dy++) {
                int x = at.getX() + s[0], y = at.getY() + dy, z = at.getZ() + s[1];
                int d = f.at(x, y, z);
                if (d < bestD) { bestD = d; best = new BlockPos(x, y, z); }
            }
        return best;
    }

    /** The walking distance from the goal to every standing cell in its box: a breadth-first flood, a step up or down at a time. */
    private static Field flood(ServerLevel city, Goal goal) {
        long began = System.nanoTime();
        Field f = new Field(goal.at(), goal.box());
        // Which cells can be stood in, worked out once.
        boolean[] stand = new boolean[f.dist.length];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = f.box.minX(); x <= f.box.maxX(); x++)
            for (int z = f.box.minZ(); z <= f.box.maxZ(); z++) {
                for (int y = f.box.minY(); y <= f.box.maxY(); y++) {
                    pos.set(x, y, z);
                    stand[f.index(x, y, z)] = standable(city, pos);
                }
            }
        BlockPos g = nearestStanding(f, stand, goal.at());
        if (g == null) return f;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        f.dist[f.index(g.getX(), g.getY(), g.getZ())] = 0;
        queue.add(new int[] {g.getX(), g.getY(), g.getZ()});
        int reached = 0;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int d = f.dist[f.index(c[0], c[1], c[2])];
            reached++;
            for (int[] s : STEPS)
                for (int dy = -1; dy <= 1; dy++) {
                    int x = c[0] + s[0], y = c[1] + dy, z = c[2] + s[1];
                    if (!f.inside(x, y, z)) continue;
                    int i = f.index(x, y, z);
                    if (!stand[i] || f.dist[i] != Integer.MAX_VALUE) continue;
                    // A step up needs headroom over the lower cell; a step down, over the cell stepped from.
                    if (dy == 1 && !(f.inside(c[0], c[1] + 2, c[2]) && clearHead(city, pos.set(c[0], c[1] + 2, c[2])))) continue;
                    if (dy == -1 && !clearHead(city, pos.set(x, y + 2, z))) continue;
                    f.dist[i] = d + (dy == 0 ? 1 : 2);
                    queue.add(new int[] {x, y, z});
                }
        }
        HeroesEvolved.LOGGER.debug("Last Day guide: {} cells to {} in {} ms", reached, goal.at().toShortString(), (System.nanoTime() - began) / 1_000_000);
        return f;
    }

    private static BlockPos nearestStanding(Field f, boolean[] stand, BlockPos at) {
        for (int r = 0; r <= 3; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        int x = at.getX() + dx, y = at.getY() + dy, z = at.getZ() + dz;
                        if (f.inside(x, y, z) && stand[f.index(x, y, z)]) return new BlockPos(x, y, z);
                    }
        return null;
    }

    /** Room for feet here and a head above, and something to stand on: the floor below, or a slab or carpet in the cell. */
    static boolean standable(ServerLevel city, BlockPos feet) {
        BlockState here = city.getBlockState(feet);
        double own = height(city, feet, here);
        if (own > 0.55) return false;
        BlockPos head = feet.above();
        if (height(city, head, city.getBlockState(head)) > 0) return false;
        if (own > 0) return true;
        BlockPos below = feet.below();
        return height(city, below, city.getBlockState(below)) >= 0.9;
    }

    private static boolean clearHead(ServerLevel city, BlockPos pos) { return height(city, pos, city.getBlockState(pos)) <= 0.3; }

    /**
     * How high a block's collision reaches in its cell (0 for none); open doors, gates and trapdoors count as none, and so
     * does a shut wooden door or gate, since anyone can open it (the way into the market may have been shut by someone).
     */
    private static double height(ServerLevel city, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) return 0;
        if (PrologueService.openableByHand(state)) return 0;
        if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) return 0;
        if (state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) return 0;
        VoxelShape shape = state.getCollisionShape(city, pos);
        return shape.isEmpty() ? 0 : shape.max(net.minecraft.core.Direction.Axis.Y);
    }
}
