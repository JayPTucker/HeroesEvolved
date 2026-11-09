package com.jayptucker.heroesevolved.assembly;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.util.Set;

/** Closed whitelist, main inventory only, plain stacks only. Never reads containers or creates useful items. */
public final class AssemblyMaterials {
    private static final Set<Item> ALLOWED = Set.of(
            Blocks.STONE.asItem(), Blocks.COBBLESTONE.asItem(), Blocks.DEEPSLATE.asItem(),
            Blocks.COBBLED_DEEPSLATE.asItem(), Blocks.GRANITE.asItem(), Blocks.DIORITE.asItem(),
            Blocks.ANDESITE.asItem(), Blocks.TUFF.asItem(), Blocks.DIRT.asItem(), Blocks.COARSE_DIRT.asItem(),
            Blocks.SANDSTONE.asItem(), Blocks.RED_SANDSTONE.asItem(), Blocks.NETHERRACK.asItem(),
            Blocks.BLACKSTONE.asItem(), Blocks.BASALT.asItem(), Blocks.OAK_PLANKS.asItem(),
            Blocks.SPRUCE_PLANKS.asItem(), Blocks.BIRCH_PLANKS.asItem(), Blocks.JUNGLE_PLANKS.asItem(),
            Blocks.ACACIA_PLANKS.asItem(), Blocks.DARK_OAK_PLANKS.asItem(), Blocks.MANGROVE_PLANKS.asItem(),
            Blocks.CHERRY_PLANKS.asItem());
    private AssemblyMaterials() {}
    public static boolean eligible(ItemStack stack) {
        return !stack.isEmpty() && ALLOWED.contains(stack.getItem()) && stack.getComponentsPatch().isEmpty();
    }
    public static int count(ServerPlayer player) {
        return player.getInventory().items.stream().filter(AssemblyMaterials::eligible).mapToInt(ItemStack::getCount).sum();
    }
    public static ItemStack take(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (eligible(stack)) {
                ItemStack payment = stack.split(1); player.getInventory().setChanged(); return payment;
            }
        }
        return ItemStack.EMPTY;
    }
    public static boolean spend(ServerPlayer player, int amount) {
        if (count(player) < amount) return false;
        for (int i = 0; i < amount; i++) take(player);
        return true;
    }
    static void refund(ServerPlayer player, ItemStack payment) {
        if (!player.getInventory().add(payment)) player.drop(payment, false);
        player.getInventory().setChanged();
    }
}
