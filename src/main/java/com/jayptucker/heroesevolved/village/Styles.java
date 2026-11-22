package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;

/**
 * Our own village buildings (the town houses) are drawn once, in oak and cobblestone, and recoloured for the village
 * that builds them: spruce in the taiga and the snow, acacia on the savanna, birch and sandstone in the desert. A block
 * is swapped by its name (oak_stairs to spruce_stairs) with every property kept, so stairs, doors and logs still face
 * the way they were drawn. Vanilla's own buildings are already in their kind's style and are left alone.
 */
public final class Styles {
    private Styles() {}

    /** Name swaps for each kind, applied to the start of a block's name (and after "stripped_"). */
    private static final Map<String, Map<String, String>> SWAPS = Map.of(
            "taiga", Map.of("oak_", "spruce_"),
            "snowy", Map.of("oak_", "spruce_", "cobblestone", "stone_bricks", "mossy_cobblestone", "mossy_stone_bricks"),
            "savanna", Map.of("oak_", "acacia_", "white_terracotta", "orange_terracotta"),
            "desert", Map.of("oak_", "birch_", "cobblestone", "sandstone", "mossy_cobblestone", "chiseled_sandstone",
                    "bricks", "smooth_sandstone", "white_terracotta", "white_terracotta"));

    /** Whether template {@code id} is one of ours, drawn to be recoloured. */
    public static boolean ours(ResourceLocation id) { return id != null && id.getNamespace().equals(HeroesEvolved.MOD_ID); }

    /** {@code state} as a village of {@code kind} builds it. */
    public static BlockState apply(BlockState state, String kind) {
        Map<String, String> swaps = SWAPS.get(kind);
        if (swaps == null || state.isAir()) return state;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        String swapped = swap(path, swaps);
        if (swapped.equals(path)) return state;
        Block to = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(swapped));
        if (to == Blocks.AIR) return state;
        return copy(state, to.defaultBlockState());
    }

    static String swap(String path, Map<String, String> swaps) {
        String stripped = path.startsWith("stripped_") ? "stripped_" : "";
        String rest = path.substring(stripped.length());
        // Whole names first (mossy_cobblestone before cobblestone), then prefixes (oak_ but never dark_oak_).
        if (swaps.containsKey(rest)) return stripped + swaps.get(rest);
        for (var e : swaps.entrySet())
            if (e.getKey().endsWith("_") && rest.startsWith(e.getKey())) return stripped + e.getValue() + rest.substring(e.getKey().length());
        for (var e : swaps.entrySet())
            if (!e.getKey().endsWith("_") && rest.startsWith(e.getKey() + "_")) return stripped + e.getValue() + rest.substring(e.getKey().length());
        return path;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copy(BlockState from, BlockState to) {
        for (Property p : from.getProperties())
            if (to.hasProperty(p)) to = to.setValue(p, from.getValue(p));
        return to;
    }
}
