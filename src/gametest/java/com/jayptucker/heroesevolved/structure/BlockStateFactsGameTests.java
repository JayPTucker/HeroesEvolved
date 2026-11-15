package com.jayptucker.heroesevolved.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints what the game itself says about directional blocks (the shape every stair, door, trapdoor and gate state
 * fills, and which neighbour each wall-mounted block hangs on), as FACT lines in the log. The building skill's
 * block-states reference was written from this output; run it again if a Minecraft update might have changed them.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class BlockStateFactsGameTests {
    private static final String[] SIDES = {"north", "south", "west", "east"};

    /** Where in its cell a shape sits, in words: which edges and halves it fills. */
    static String where(BlockState state) {
        List<String> parts = new ArrayList<>();
        for (AABB box : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs())
            parts.add(String.format("x%.2f-%.2f y%.2f-%.2f z%.2f-%.2f", box.minX, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ));
        return String.join(" + ", parts);
    }

    /** For a stair, which quarters of the raised half are filled (a quarter is NW, NE, SW or SE of the cell). */
    static String raised(BlockState state) {
        boolean top = state.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP;
        double y = top ? 0.25 : 0.75;
        var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        List<String> filled = new ArrayList<>();
        for (String q : new String[] {"NW", "NE", "SW", "SE"}) {
            double x = q.charAt(1) == 'W' ? 0.25 : 0.75, z = q.charAt(0) == 'N' ? 0.25 : 0.75;
            for (AABB box : shape.toAabbs())
                if (box.contains(x, y, z)) { filled.add(q); break; }
        }
        return (top ? "lower half raised in " : "upper half raised in ") + String.join(",", filled);
    }

    @GameTest(template = "rewind_empty", batch = "blockstate.facts")
    public static void blockStateFacts(GameTestHelper helper) {
        List<String> out = new ArrayList<>();
        for (String facing : SIDES)
            for (String half : new String[] {"bottom", "top"})
                for (String shape : new String[] {"straight", "inner_left", "inner_right", "outer_left", "outer_right"}) {
                    BlockState s = state("oak_stairs[facing=" + facing + ",half=" + half + ",shape=" + shape + "]");
                    out.add("stairs facing=" + facing + " half=" + half + " shape=" + shape + ": " + raised(s));
                }
        for (String facing : SIDES)
            for (String hinge : new String[] {"left", "right"})
                for (String open : new String[] {"false", "true"})
                    out.add("door facing=" + facing + " hinge=" + hinge + " open=" + open + ": "
                            + where(state("oak_door[facing=" + facing + ",hinge=" + hinge + ",open=" + open + ",half=lower]")));
        for (String facing : SIDES)
            for (String half : new String[] {"bottom", "top"})
                for (String open : new String[] {"false", "true"})
                    out.add("trapdoor facing=" + facing + " half=" + half + " open=" + open + ": "
                            + where(state("oak_trapdoor[facing=" + facing + ",half=" + half + ",open=" + open + "]")));
        for (String facing : SIDES)
            for (String open : new String[] {"false", "true"})
                out.add("fence_gate facing=" + facing + " open=" + open + ": collision "
                        + state("oak_fence_gate[facing=" + facing + ",open=" + open + "]").getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs());
        // Wall-mounted blocks: stand each in a cell with a single stone neighbour and see which neighbour holds it.
        String[] mounted = {"wall_torch[facing=%s]", "ladder[facing=%s]", "oak_wall_sign[facing=%s]", "lever[face=wall,facing=%s]",
                "stone_button[face=wall,facing=%s]", "white_wall_banner[facing=%s]", "oak_wall_hanging_sign[facing=%s]",
                "bell[attachment=single_wall,facing=%s]", "grindstone[face=wall,facing=%s]", "tripwire_hook[facing=%s]",
                "skeleton_wall_skull[facing=%s]", "vine[%s=true]", "glow_lichen[%s=true]", "end_rod[facing=%s]", "lightning_rod[facing=%s]",
                "cocoa[facing=%s]"};
        BlockPos cell = new BlockPos(5, 3, 5);
        for (String pattern : mounted)
            for (String facing : SIDES) {
                BlockState s = state(String.format(pattern, facing));
                List<String> holds = new ArrayList<>();
                for (Direction side : Direction.values()) {
                    clear(helper, cell);
                    Block support = pattern.startsWith("cocoa") ? Blocks.JUNGLE_LOG : Blocks.STONE;
                    helper.setBlock(cell.relative(side), support);
                    if (s.canSurvive(helper.getLevel(), helper.absolutePos(cell))) holds.add(side.getName());
                }
                clear(helper, cell);
                out.add("mounted " + String.format(pattern, facing) + ": stays with stone on " + (holds.isEmpty() ? "no single side" : String.join("/", holds)));
            }
        // Beds and double chests: where the other half is.
        for (String facing : SIDES) {
            BlockState foot = state("red_bed[facing=" + facing + ",part=foot]");
            out.add("bed facing=" + facing + ": the head is " + net.minecraft.world.level.block.BedBlock.getConnectedDirection(foot).getName() + " of the foot");
            for (String type : new String[] {"left", "right"})
                out.add("chest facing=" + facing + " type=" + type + ": the other half is "
                        + net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state("chest[facing=" + facing + ",type=" + type + "]")).getName());
        }
        for (String line : out) System.out.println("FACT " + line);
        helper.succeed();
    }

    /**
     * Writes {@code build/block-facts.json.gz} (next to the test world): every block's properties, their values and its
     * default state, and for each state (every state of blocks with at most 128, the default of the rest) a mask of what
     * its six faces hold, in the order down, up, north, south, west, east:
     * bits 0-5 sturdy (torches, ladders, buttons, levers, doors on top), 6-11 centre support (lanterns, candles, standing
     * torches, hanging signs), 12-17 rigid support (rails, pressure plates), 18-23 a full face (vines, lichen), bit 24
     * {@code isSolid} (wall and standing signs, banners) and bit 25 air. {@code tools/blueprint.py validate} reads the copy
     * in {@code tools/block-facts.json.gz}; copy the new file there after a Minecraft or mod update.
     */
    @GameTest(template = "rewind_empty", batch = "blockstate.facts")
    public static void dumpBlockFacts(GameTestHelper helper) {
        var getter = EmptyBlockGetter.INSTANCE;
        StringBuilder json = new StringBuilder("{");
        var ids = new java.util.TreeMap<String, Block>();
        for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
            ids.put(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString(), block);
        boolean firstBlock = true;
        for (var entry : ids.entrySet()) {
            Block block = entry.getValue();
            var definition = block.getStateDefinition();
            if (!firstBlock) json.append(',');
            firstBlock = false;
            json.append('"').append(entry.getKey()).append("\":{\"p\":{");
            var properties = new ArrayList<>(definition.getProperties());
            properties.sort(java.util.Comparator.comparing(net.minecraft.world.level.block.state.properties.Property::getName));
            for (int i = 0; i < properties.size(); i++) {
                var property = properties.get(i);
                if (i > 0) json.append(',');
                json.append('"').append(property.getName()).append("\":[");
                int k = 0;
                for (Object value : property.getPossibleValues()) {
                    if (k++ > 0) json.append(',');
                    json.append('"').append(name(property, value)).append('"');
                }
                json.append(']');
            }
            json.append("},\"d\":\"").append(key(block.defaultBlockState(), properties)).append("\",\"s\":{");
            var states = definition.getPossibleStates().size() <= 128 ? definition.getPossibleStates() : List.of(block.defaultBlockState());
            boolean firstState = true;
            for (BlockState state : states) {
                int mask = 0;
                Direction[] sides = Direction.values();
                for (int i = 0; i < 6; i++) {
                    Direction side = sides[i];
                    if (state.isFaceSturdy(getter, BlockPos.ZERO, side, net.minecraft.world.level.block.SupportType.FULL)) mask |= 1 << i;
                    if (state.isFaceSturdy(getter, BlockPos.ZERO, side, net.minecraft.world.level.block.SupportType.CENTER)) mask |= 1 << (6 + i);
                    if (state.isFaceSturdy(getter, BlockPos.ZERO, side, net.minecraft.world.level.block.SupportType.RIGID)) mask |= 1 << (12 + i);
                    if (Block.isFaceFull(state.getBlockSupportShape(getter, BlockPos.ZERO), side)
                            || Block.isFaceFull(state.getCollisionShape(getter, BlockPos.ZERO), side)) mask |= 1 << (18 + i);
                }
                if (state.isSolid()) mask |= 1 << 24;
                if (state.isAir()) mask |= 1 << 25;
                if (!firstState) json.append(',');
                firstState = false;
                json.append('"').append(key(state, properties)).append("\":").append(mask);
            }
            json.append("}}");
        }
        json.append('}');
        try {
            var out = java.nio.file.Path.of("..", "block-facts.json.gz").toAbsolutePath().normalize();
            try (var stream = new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(out))) {
                stream.write(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            System.out.println("FACT wrote " + out + " (" + ids.size() + " blocks)");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        helper.succeed();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String name(net.minecraft.world.level.block.state.properties.Property property, Object value) {
        return property.getName((Comparable) value);
    }

    private static String key(BlockState state, List<net.minecraft.world.level.block.state.properties.Property<?>> properties) {
        StringBuilder out = new StringBuilder();
        for (var property : properties) {
            if (!out.isEmpty()) out.append(',');
            out.append(property.getName()).append('=').append(name(property, state.getValue(property)));
        }
        return out.toString();
    }

    private static void clear(GameTestHelper helper, BlockPos cell) {
        for (Direction side : Direction.values()) helper.setBlock(cell.relative(side), Blocks.AIR);
        helper.setBlock(cell, Blocks.AIR);
    }

    private static BlockState state(String text) {
        try {
            return net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException(text, e);
        }
    }
}
