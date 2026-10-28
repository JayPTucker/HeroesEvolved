package com.jayptucker.heroesevolved.formula;

import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A Formula ingredient that remembers whose blood it carries. */
public class PurityItem extends Item {
    public PurityItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BloodPurity purity = stack.get(ModItems.BLOOD_PURITY.get());
        if (purity != null) tooltip.add(purity.describe());
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
