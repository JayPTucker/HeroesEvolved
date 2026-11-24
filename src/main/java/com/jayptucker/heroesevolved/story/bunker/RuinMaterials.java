package com.jayptucker.heroesevolved.story.bunker;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** Surface ruins are scenery and modest caches, never thousands of blocks of recoverable metal, gems or machinery. */
final class RuinMaterials {
    private RuinMaterials() {}
    private static final Set<String> VALUABLE = Set.of("coal_block", "lapis_block", "redstone_block", "emerald_block",
            "diamond_block", "netherite_block", "amethyst_block", "budding_amethyst", "ancient_debris", "bone_block",
            "beacon", "conduit", "enchanting_table", "brewing_stand", "ender_chest", "respawn_anchor", "tnt",
            "glowstone", "sea_lantern", "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "redstone_lamp",
            "bookshelf", "chiseled_bookshelf", "anvil", "chipped_anvil", "damaged_anvil", "hopper", "bell",
            "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron", "chain", "end_rod", "lightning_rod");

    static boolean valuable(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return id.contains("iron") || id.contains("gold") || id.contains("copper") || id.contains("quartz")
                || id.endsWith("_ore") || id.startsWith("raw_") || VALUABLE.contains(id);
    }

    static BlockState safe(BlockState state) {
        if (!valuable(state)) return state;
        Block block = state.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (block instanceof DoorBlock) return Blocks.DARK_OAK_DOOR.withPropertiesOf(state);
        if (block instanceof TrapDoorBlock) return Blocks.DARK_OAK_TRAPDOOR.withPropertiesOf(state);
        if (block instanceof StairBlock) return Blocks.MOSSY_STONE_BRICK_STAIRS.withPropertiesOf(state);
        if (block instanceof SlabBlock) return Blocks.MOSSY_STONE_BRICK_SLAB.withPropertiesOf(state);
        if (block instanceof WallBlock) return Blocks.MOSSY_COBBLESTONE_WALL.withPropertiesOf(state);
        if (state.is(Blocks.IRON_BARS) || state.is(Blocks.CHAIN) || state.is(Blocks.END_ROD) || state.is(Blocks.LIGHTNING_ROD))
            return Blocks.DARK_OAK_FENCE.defaultBlockState();
        if (id.contains("gold")) return Blocks.YELLOW_TERRACOTTA.defaultBlockState();
        if (id.contains("quartz")) return Blocks.CALCITE.defaultBlockState();
        if (id.contains("copper")) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (id.contains("iron")) return Blocks.POLISHED_ANDESITE.defaultBlockState();
        if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF)) return Blocks.DARK_OAK_PLANKS.defaultBlockState();
        return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    }
}
