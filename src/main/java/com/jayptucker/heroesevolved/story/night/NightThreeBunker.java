package com.jayptucker.heroesevolved.story.night;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder;
import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.Room;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The stasis bunker as it was on the third night of the lockdown, built in its own pocket of the past
 * from the same blueprints as the bunker under spawn. Nothing has broken in yet: no cave, no moss, no
 * cobwebs, no bones, no blood on the floor. The lights are on, every pod is sealed, and Gabriel Gray's
 * cage still has all its bars. The rooms are rebuilt for every night, so whatever happened last time
 * (a pod forced open, a cage unmade) is put back.
 */
public final class NightThreeBunker {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "night_three"));
    /** The bunker floor under the shaft, as {@link BunkerBuilder}'s origin is in the present. */
    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    /** Pod 14, Claire Bennet's: the last pod on the east wall. */
    public static final int CLAIRE = 13;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private NightThreeBunker() {}

    public static BlockPos at(int x, int y, int z) { return ORIGIN.offset(x, y, z); }

    /** Pod 14's base block in the past. */
    public static BlockPos pod() { return ORIGIN.offset(BunkerBuilder.podOffset(CLAIRE)); }

    /** The east side of Gray's cage: the bars he unmakes on the third night. */
    public static List<BlockPos> cageBars() {
        List<BlockPos> bars = new ArrayList<>();
        for (int y = 1; y <= 3; y++) for (int z = 14; z <= 15; z++) bars.add(at(-4, y, z));
        return bars;
    }

    /** The lockdown version of a block: lights on, no decay, nothing grown in, no blood. */
    public static BlockState past(int layer, BlockState state) { return com.jayptucker.heroesevolved.story.bunker.PastBunker.past(layer, state); }

    /** The space the bunker takes up, solid rock around it included. */
    static BoundingBox bounds() { return com.jayptucker.heroesevolved.story.bunker.PastBunker.bounds(ORIGIN); }

    /** Builds the rock around the bunker once, then the rooms; afterwards only the rooms. */
    public static void build(MinecraftServer server, ServerLevel level) {
        NightThreeData data = NightThreeData.get(server);
        BoundingBox box = bounds();
        if (data.layout != NightThreeData.LAYOUT) {
            BlockState rock = Blocks.DEEPSLATE.defaultBlockState();
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
                level.setBlock(pos, rock, Block.UPDATE_CLIENTS);
            data.layout = NightThreeData.LAYOUT;
            data.setDirty();
            HeroesEvolved.LOGGER.info("Built the stasis bunker of Night Three at {}", ORIGIN);
        }
        rooms(level);
    }

    /** Places every room as it was on the third night, and puts back what any earlier night changed. */
    static void rooms(ServerLevel level) {
        com.jayptucker.heroesevolved.story.bunker.PastBunker.rooms(level, ORIGIN);
        shaft(level);
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        for (BlockPos pos : cageBars()) level.setBlock(pos, bars, Block.UPDATE_CLIENTS);
        for (BlockPos pos : cageBars()) level.setBlock(pos, Block.updateFromNeighbourShapes(bars, level, pos), Block.UPDATE_CLIENTS);
        signPod(level);
    }

    /** The ladder well from the records room, closed off by a hatch only the Company opens from above. */
    private static void shaft(ServerLevel level) {
        for (int y = 1; y <= 7; y++) level.setBlock(at(0, y, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
        level.setBlock(at(0, 8, 0), Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
    }

    /** Pod 14's sign, before anyone drew her blood. */
    private static void signPod(ServerLevel level) {
        for (BlockPos offset : Blueprint.load("bunker_chamber").markerOffsets("sign:pod14")) {
            BlockPos pos = ORIGIN.offset(0, 0, 18).offset(offset);
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                sign.setText(text(DyeColor.LIGHT_BLUE, "SUBJECT 14", "C. BENNET", "STASIS SEALED", "NIGHT 3"), true);
                sign.setChanged();
            }
        }
    }

    static SignText text(DyeColor color, String... lines) {
        SignText text = new SignText().setColor(color).setHasGlowingText(true);
        for (int i = 0; i < lines.length && i < 4; i++) text = text.setMessage(i, Component.literal(lines[i]));
        return text;
    }

    /** Pod 14 itself, in the past, opened (when Gray finishes) or sealed. */
    static void setPodOpen(ServerLevel level, boolean open) {
        BlockPos base = pod();
        for (BlockPos pos : List.of(base, base.above())) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(CryoChamberBlock.OPEN)) level.setBlock(pos, state.setValue(CryoChamberBlock.OPEN, open), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The present remembers: Pod 14 in the bunker under spawn is sealed again with someone still asleep
     * in it, and the blood trail from it to Gray's cell was never left. Does nothing if that bunker
     * hasn't been built.
     */
    public static void resealPresent(MinecraftServer server) {
        BunkerData bunker = BunkerData.get(server);
        if (!bunker.built) return;
        ServerLevel overworld = server.overworld();
        BlockPos origin = bunker.origin;
        BunkerBuilder.placePod(overworld, origin, CLAIRE, false);
        for (Room room : BunkerBuilder.ROOMS) {
            Blueprint blueprint = Blueprint.load(room.blueprint());
            for (BlockPos offset : blueprint.cellsMatching(state -> state.is(Blocks.REDSTONE_WIRE))) {
                BlockPos pos = origin.offset(room.at()).offset(offset);
                if (overworld.getBlockState(pos).is(Blocks.REDSTONE_WIRE)) overworld.setBlock(pos, AIR, Block.UPDATE_ALL);
            }
        }
        for (BlockPos offset : Blueprint.load("bunker_chamber").markerOffsets("sign:pod14")) {
            BlockPos pos = origin.offset(0, 0, 18).offset(offset);
            if (overworld.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                sign.setText(text(DyeColor.LIGHT_BLUE, "SUBJECT 14", "C. BENNET", "STILL ASLEEP", "DO NOT OPEN"), true);
                sign.setChanged();
                overworld.sendBlockUpdated(pos, overworld.getBlockState(pos), overworld.getBlockState(pos), Block.UPDATE_CLIENTS);
            }
        }
    }
}
