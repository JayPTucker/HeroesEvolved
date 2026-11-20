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
 * The stasis bunker as it was before the hundred and three years: Sublevel 4 ({@link BunkerLayout#STASIS}) from its
 * {@code _past} rooms, at any origin, with nothing broken in yet. No cave, no moss, no cobwebs, no bones, no blood on the
 * floor, new copper, every light on. Night Three builds one in its own pocket of the past; the Last Day builds one under
 * Primatech Paper in Odessa, where the prologue's survivors run for shelter.
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
        BoundingBox rooms = BunkerLayout.STASIS.bounds();
        BoundingBox box = rooms.moved(origin.getX(), origin.getY(), origin.getZ());
        box.encapsulate(origin.offset(0, 10, 0));
        return box.inflatedBy(2);
    }

    /**
     * Fills the bunker's volume with rock, so its rooms stand in solid ground wherever they are placed. Not above the
     * rooms' own ceilings: under Odessa that is where the city's cellars and subway steps begin.
     */
    public static void rock(ServerLevel level, BlockPos origin) {
        BoundingBox box = bounds(origin);
        int top = origin.getY() + BunkerLayout.STASIS.bounds().maxY();
        BlockState rock = Blocks.DEEPSLATE.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), top, box.maxZ()))
            level.setBlock(pos, rock, Block.UPDATE_CLIENTS);
    }

    /** Places every room as it was, and puts back whatever an earlier visit changed. */
    public static void rooms(ServerLevel level, BlockPos origin) {
        for (Room room : BunkerLayout.STASIS.past())
            Blueprint.load(room.blueprint()).place(level, origin.offset(room.at()), Rotation.NONE);
    }
}
