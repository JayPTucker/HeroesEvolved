package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.Room;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The stasis bunker as it was before the hundred and three years: built from the same blueprints as the bunker under
 * spawn, at any origin, with nothing broken in yet. No cave, no moss, no cobwebs, no bones, no blood on the floor, every
 * light on. Night Three builds one in its own pocket of the past; the Last Day builds one under Primatech Paper in
 * Odessa, where the prologue's survivors run for shelter.
 */
public final class PastBunker {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** What a hundred years grew or left behind; none of it is here yet. */
    private static final Set<Block> LATER = Set.of(Blocks.MOSS_CARPET, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.AZALEA,
            Blocks.FLOWERING_AZALEA_LEAVES, Blocks.AZALEA_LEAVES, Blocks.GLOW_LICHEN, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT,
            Blocks.COBWEB, Blocks.POINTED_DRIPSTONE, Blocks.HANGING_ROOTS, Blocks.SPORE_BLOSSOM, Blocks.MANGROVE_ROOTS,
            Blocks.DARK_OAK_WOOD, Blocks.BONE_BLOCK, Blocks.SKELETON_SKULL, Blocks.REDSTONE_WIRE);

    private PastBunker() {}

    /** The lockdown version of a block: lights on, no decay, nothing grown in, no blood. */
    public static BlockState past(int layer, BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.ROOTED_DIRT || block == Blocks.MOSS_BLOCK || block == Blocks.DIRT)
            return layer == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        if (LATER.contains(block)) return AIR;
        if (block == Blocks.CRACKED_DEEPSLATE_TILES) return Blocks.DEEPSLATE_TILES.defaultBlockState();
        if (block == Blocks.CRACKED_DEEPSLATE_BRICKS) return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        if (block == Blocks.RED_STAINED_GLASS) return Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        if (block == Blocks.SOUL_LANTERN) return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING));
        if (state.hasProperty(BlockStateProperties.LIT)) return state.setValue(BlockStateProperties.LIT, true);
        return state;
    }

    /** The space a bunker at {@code origin} takes up, solid rock around it included. */
    public static BoundingBox bounds(BlockPos origin) {
        List<Room> rooms = new ArrayList<>(BunkerBuilder.ROOMS);
        rooms.add(BunkerBuilder.OFFICE);
        BoundingBox box = new BoundingBox(origin);
        for (Room room : rooms) {
            Blueprint blueprint = Blueprint.load(room.blueprint());
            BlockPos anchor = origin.offset(room.at());
            box.encapsulate(anchor.offset(blueprint.offset(0, 0, 0, Rotation.NONE)));
            box.encapsulate(anchor.offset(blueprint.offset(blueprint.width() - 1, blueprint.height() - 1, blueprint.depth() - 1, Rotation.NONE)));
        }
        box.encapsulate(origin.offset(0, 10, 0));
        return box.inflatedBy(2);
    }

    /** Fills the bunker's whole volume with rock, so its rooms stand in solid ground wherever they are placed. */
    public static void rock(ServerLevel level, BlockPos origin) {
        BoundingBox box = bounds(origin);
        BlockState rock = Blocks.DEEPSLATE.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            level.setBlock(pos, rock, Block.UPDATE_CLIENTS);
    }

    /** Places every room as it was, and puts back whatever an earlier visit changed. */
    public static void rooms(ServerLevel level, BlockPos origin) {
        for (Room room : BunkerBuilder.ROOMS) place(level, origin, room);
        place(level, origin, BunkerBuilder.OFFICE);
        BunkerBuilder.retireFirstExhibit(level, origin.offset(BunkerBuilder.CHAMBER.at()));
        repairChamber(level, origin);
    }

    private static void place(ServerLevel level, BlockPos origin, Room room) {
        Blueprint.load(room.blueprint()).place(level, origin.offset(room.at()), Rotation.NONE, Integer.MAX_VALUE - 1, PastBunker::past);
    }

    /** Where the cave broke into the stasis hall in the present, the hall is whole: floor, wall frame, beam and duct. */
    private static void repairChamber(ServerLevel level, BlockPos origin) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int gz = 14; gz <= 21; gz++) for (int gx = 10; gx <= 13; gx++) level.setBlock(chamber(origin, gx, 0, gz), floor, Block.UPDATE_CLIENTS);
        level.setBlock(chamber(origin, 9, 0, 18), Blocks.POLISHED_TUFF.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(chamber(origin, 11, 1, 14), AIR, Block.UPDATE_CLIENTS);
        level.setBlock(chamber(origin, 10, 1, 15), AIR, Block.UPDATE_CLIENTS);
        level.setBlock(chamber(origin, 12, 1, 15), Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int gx = 10; gx <= 13; gx++) level.setBlock(chamber(origin, gx, 11, 19), floor, Block.UPDATE_CLIENTS);
        for (int gz = 15; gz <= 18; gz++) level.setBlock(chamber(origin, 10, 11, gz), Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Chamber grid cell to a world position (the chamber's anchor is grid 7 0 0, at bunker 0 0 18). */
    public static BlockPos chamber(BlockPos origin, int gx, int gy, int gz) { return origin.offset(gx - 7, gy, gz + 18); }
}
