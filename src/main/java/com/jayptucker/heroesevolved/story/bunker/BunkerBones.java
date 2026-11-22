package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.Room;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * The skulls of those who never made it out, lying about the rooms of the bunker under spawn. Each room is strewn once,
 * when all of it is loaded, in new bunkers and old ({@link BunkerData#strewn}); the bunkers of the past never are.
 * <p>
 * A skull lies against a wall or in a corner, where a body would have slumped: never in a doorway or a one-wide passage,
 * in front of a pod, a door or anything that is used, or where one of the infirmary's survivors is laid.
 */
final class BunkerBones {
    /** About one spot against a wall in this many gets a skull. */
    private static final int ONE_IN = 26;

    private BunkerBones() {}

    /** Strews every room that is loaded and hasn't been yet. */
    static void strew(ServerLevel level, BunkerData data) {
        BunkerLayout layout = data.layout();
        List<Room> rooms = layout.all();
        if (data.strewn.size() >= rooms.size()) return;
        List<BlockPos> kept = null;     // Read from the blueprints only once a room is ready: this runs every second.
        for (Room room : rooms) {
            if (data.strewn.contains(room.blueprint())) continue;
            // A shallow world has no room below the main level, only rock and whatever caves are in it.
            if (layout.deep().contains(room) && data.origin.getY() + room.at().getY() <= level.getMinBuildHeight()) {
                data.strewn.add(room.blueprint());
                data.setDirty();
                continue;
            }
            Blueprint plan = Blueprint.load(room.blueprint());
            BlockPos at = data.origin.offset(room.at());
            BoundingBox box = BoundingBox.fromCorners(at.offset(plan.offset(0, 0, 0, Rotation.NONE)),
                    at.offset(plan.offset(plan.width() - 1, plan.height() - 1, plan.depth() - 1, Rotation.NONE)));
            if (!level.hasChunksAt(box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1)) continue;
            if (kept == null) kept = layout.survivors().stream().map(data.origin::offset).toList();
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                int roll = noise(data.origin, pos);
                if (roll % ONE_IN != 0 || !spot(level, pos) || near(kept, pos)) continue;
                level.setBlock(pos, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, (roll >>> 8) % 16),
                        Block.UPDATE_CLIENTS);
            }
            data.strewn.add(room.blueprint());
            data.setDirty();
        }
    }

    /** Open floor against a wall, clear of anything a player walks through or uses. */
    private static boolean spot(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return false;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || level.canSeeSky(pos) || used(level, below)) return false;
        // A side with a low overhang is as good as a wall: nobody walks through it.
        boolean north = !open(level, pos.north()), south = !open(level, pos.south()), east = !open(level, pos.east()), west = !open(level, pos.west());
        if (north && south || east && west || !(north || south || east || west)) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (used(level, next)) return false;
            // A bend one block wide: the two ways on meet only through this cell, and a skull is too wide to squeeze past.
            BlockPos turn = pos.relative(side.getClockWise());
            if (open(level, next) && open(level, turn) && !open(level, next.relative(side.getClockWise()))) return false;
        }
        return true;
    }

    /** A pod, a door, a hatch, or anything with a block entity (a chest, a barrel, a station). */
    private static boolean used(ServerLevel level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof CryoChamberBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock
                || level.getBlockEntity(pos) != null;
    }

    /** Room to walk through at {@code pos}, feet and head. */
    private static boolean open(ServerLevel level, BlockPos pos) { return !wall(level, pos) && !wall(level, pos.above()); }

    private static boolean wall(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.blocksMotion();
    }

    private static boolean near(List<BlockPos> kept, BlockPos pos) {
        for (BlockPos spot : kept) if (spot.distManhattan(pos) <= 2) return true;
        return false;
    }

    /** The same in every run of one world, different from world to world. */
    private static int noise(BlockPos origin, BlockPos pos) {
        long h = origin.asLong() * 0x9E3779B97F4A7C15L ^ pos.asLong() * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ h >>> 29) * 0xBF58476D1CE4E5B9L;
        return (int) (h ^ h >>> 32) & 0x7FFFFFFF;
    }
}
