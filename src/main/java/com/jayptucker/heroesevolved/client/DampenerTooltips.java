package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.dampener.DampenerGeometry;
import com.jayptucker.heroesevolved.dampener.Nullifier;
import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.text.NumberFormat;
import java.util.Locale;

/** The Nullifier's battery and an Ability Dampener item's stored charge, under their names. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class DampenerTooltips {
    private DampenerTooltips() {}

    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        NumberFormat n = NumberFormat.getIntegerInstance(Locale.ROOT);
        if (stack.is(ModItems.ABILITY_DAMPENER.get())) {
            int stored = stack.getOrDefault(ModItems.STORED_ENERGY.get(), 0);
            event.getToolTip().add(1, Component.translatable("block.heroesevolved.ability_dampener.stored",
                    n.format(stored), n.format(DampenerGeometry.CAPACITY)).withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(2, Component.translatable("block.heroesevolved.ability_dampener.desc").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!Nullifier.is(stack)) return;
        int at = Math.min(1, event.getToolTip().size());
        event.getToolTip().add(at, Component.translatable("item.heroesevolved.nullifier.battery", Nullifier.rounds(stack),
                DampenerGeometry.BATTERY_ROUNDS, n.format(Nullifier.stored(stack))).withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(at + 1, Component.translatable("item.heroesevolved.nullifier.how").withStyle(ChatFormatting.GRAY));
    }
}
