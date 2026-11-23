package com.jayptucker.heroesevolved.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Connecting new homes to the village's streets, with the same saved sites and crews as buildings. */
public final class Paths {
    private static final int SEARCH = 80, LIMIT = 12_000;
    private static final Map<VillageMemory.Village, Long> LOOKED = new WeakHashMap<>();

    private Paths() {}

    /** Retry homes without a connection, including homes already built when this feature was installed. */
    static void consider(ServerLevel level, VillageMemory.Village v) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        long now = level.getGameTime(), last = LOOKED.getOrDefault(v, -1000L);
        if (now >= last && now - last < Projects.RETRY) return;
        LOOKED.put(v, now);
        for (Projects.Home home : v.homes) {
            if (!needsConnection(level, v, home)) continue;
            if (Rebuild.all(level).stream().anyMatch(s -> s.paving && home.equals(s.home))) continue;
            plan(level, v, home);
        }
    }

    static Rebuild.Site plan(ServerLevel level, VillageMemory.Village v, Projects.Home home) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || !needsConnection(level, v, home)) return null;
        List<BlockPos> route = route(level, v.bell, home);
        if (route.isEmpty()) return null; // No safe route yet: keep the home pending and try later.
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (BlockPos p : route) {
            BlockState ground = level.getBlockState(p);
            if (PlayerBuilt.is(level, p) || !Growth.natural(ground) && !raiseable(level, p)) continue; // Keep paving and owned doorsteps.
            // Plants need to come away before their soil becomes a path.
            for (int y = 2; y >= 1; y--)
                if (!level.getBlockState(p.above(y)).isAir()) {
                    positions.add(p.above(y));
                    states.add(Blocks.AIR.defaultBlockState());
                }
            positions.add(p);
            states.add(paving(Growth.natural(ground) ? ground : level.getBlockState(p.below()), Growth.kind(level, v.bell)));
        }
        if (positions.isEmpty()) { connected(level, v, home); return null; }
        Rebuild.Site site = Rebuild.path(level, v.bell, home, positions, states);
        Projects.signs(level, v);
        return site;
    }

    static boolean needsConnection(ServerLevel level, VillageMemory.Village v, Projects.Home home) {
        if (v.connected.contains(home.origin().asLong())) return false;
        var template = level.getStructureManager().get(home.template()).orElse(null);
        if (template == null) return false;
        var blocks = Growth.blocks(level, template, home.origin(), home.rotation());
        return blocks != null && blocks.stream().anyMatch(b -> b.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock);
    }

    /** A walkable route from the actual, rotated front door, across existing streets to the bell square. */
    static List<BlockPos> route(ServerLevel level, BlockPos bell, Projects.Home home) {
        var template = level.getStructureManager().get(home.template()).orElse(null);
        if (template == null) return List.of();
        var blocks = Growth.blocks(level, template, home.origin(), home.rotation());
        if (blocks == null) return List.of();
        Direction front = home.rotation().rotate(Growth.front(template, home.template()));
        var footprint = template.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                .setRotation(home.rotation()), home.origin());
        // A finished player's porch is theirs too. Walk across it to reach the street, keeping every block intact.
        var ownPorch = VillageMemory.of(level, bell).owned.values().stream().anyMatch(o -> o.home().equals(home)) ? footprint : null;
        BlockPos door = blocks.stream().filter(b -> b.state().getBlock() instanceof DoorBlock
                        && b.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                .map(b -> b.pos()).min(java.util.Comparator.comparingInt(p -> switch (front) {
                    case NORTH -> p.getZ(); case SOUTH -> -p.getZ(); case WEST -> p.getX(); default -> -p.getX();
                })).orElse(null);
        if (door == null || !level.hasChunkAt(door) || !(level.getBlockState(door).getBlock() instanceof DoorBlock)) return List.of();
        BlockPos start = surface(level, door.relative(front).below(), door.getY() - 1, ownPorch);
        if (start == null) return List.of();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        parent.put(start, start);
        open.add(start);
        BlockPos goal = null;
        while (!open.isEmpty() && parent.size() <= LIMIT) {
            BlockPos at = open.removeFirst();
            boolean square = Math.abs(at.getX() - bell.getX()) <= 2 && Math.abs(at.getZ() - bell.getZ()) <= 2
                    && Math.abs(at.getY() - bell.getY()) <= Growth.SLOPE;
            // Follow through to the square: an isolated doorstep or half-finished path is not a connection.
            if (square) { goal = at; break; }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos column = at.relative(d);
                if (Math.abs(column.getX() - bell.getX()) > SEARCH || Math.abs(column.getZ() - bell.getZ()) > SEARCH) continue;
                BlockPos next = surface(level, column, at.getY(), ownPorch);
                if (next == null || parent.containsKey(next)) continue;
                parent.put(next, at);
                open.addLast(next);
            }
        }
        if (goal == null) return List.of();
        List<BlockPos> route = new ArrayList<>();
        for (BlockPos p = goal;; p = parent.get(p)) {
            route.add(p);
            if (p.equals(start)) break;
        }
        Collections.reverse(route);
        return route;
    }

    private static BlockPos surface(ServerLevel level, BlockPos column, int y, net.minecraft.world.level.levelgen.structure.BoundingBox ownPorch) {
        if (!level.hasChunkAt(column)) return null;
        for (int dy = 1; dy >= -1; dy--) {
            BlockPos p = new BlockPos(column.getX(), y + dy, column.getZ());
            if (!level.getWorldBorder().isWithinBounds(p) || level.isOutsideBuildHeight(p)) continue;
            BlockState ground = level.getBlockState(p);
            if (!ground.getFluidState().isEmpty() || ground.is(Blocks.FARMLAND)
                    || PlayerBuilt.is(level, p) && (ownPorch == null || !ownPorch.isInside(p))) continue;
            boolean walkable = Growth.natural(ground) || ground.is(Blocks.DIRT_PATH)
                    || ground.isFaceSturdy(level, p, Direction.UP) || ground.getBlock() instanceof StairBlock
                    || ground.getBlock() instanceof SlabBlock;
            if (!walkable || ground.is(Blocks.SNOW) || ground.is(Blocks.POWDER_SNOW)) continue;
            if (clear(level, p.above()) && clear(level, p.above(2))) return p;
        }
        // Some vanilla cottages have a two-block foundation and only one entrance stair. One supported approach
        // block makes the missing step, without digging terrain, replacing a player's work or bridging water.
        BlockPos step = new BlockPos(column.getX(), y - 1, column.getZ());
        if (level.getWorldBorder().isWithinBounds(step) && !level.isOutsideBuildHeight(step) && raiseable(level, step)
                && clear(level, step.above()) && clear(level, step.above(2))) return step;
        return null;
    }

    private static boolean raiseable(ServerLevel level, BlockPos p) {
        return clear(level, p) && Growth.natural(level.getBlockState(p.below()))
                && !level.getBlockState(p.below()).is(Blocks.POWDER_SNOW) && !level.getBlockState(p.below()).is(Blocks.SNOW)
                && level.getFluidState(p.below()).isEmpty() && !PlayerBuilt.is(level, p.below());
    }

    private static boolean clear(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        return !PlayerBuilt.is(level, p) && !PlayerBuilt.cleared(level, p) && state.getFluidState().isEmpty()
                && (state.isAir() || state.canBeReplaced() && !state.isSolid());
    }

    private static BlockState paving(BlockState ground, String kind) {
        if (kind.equals("desert")) return Blocks.SANDSTONE.defaultBlockState();
        return (ground.is(BlockTags.DIRT) ? Blocks.DIRT_PATH : Blocks.GRAVEL).defaultBlockState();
    }

    /** Recheck at placement time: a player may have built, flooded or dug up the planned route. */
    static boolean canPave(ServerLevel level, BlockPos p, BlockState want) {
        if (PlayerBuilt.is(level, p) || PlayerBuilt.cleared(level, p)) return false;
        if (want.isAir()) return clear(level, p) && !level.getBlockState(p).isAir();
        return (Growth.natural(level.getBlockState(p)) || raiseable(level, p)) && !level.getBlockState(p).is(Blocks.POWDER_SNOW)
                && level.getFluidState(p).isEmpty() && clear(level, p.above()) && clear(level, p.above(2));
    }

    static void connected(ServerLevel level, VillageMemory.Village v, Projects.Home home) {
        v.connected.add(home.origin().asLong());
        VillageMemory.get(level).setDirty();
    }
}
