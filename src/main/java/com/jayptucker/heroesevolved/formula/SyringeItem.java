package com.jayptucker.heroesevolved.formula;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Draws Regeneration blood: use it on yourself, a crouching (consenting) owner, or a helpless one. */
public final class SyringeItem extends Item {
    public SyringeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer holder)) return InteractionResultHolder.sidedSuccess(stack, true);
        FormulaService.Draw result = FormulaService.draw(holder, holder, stack);
        FormulaService.explain(holder, holder, result);
        return result == FormulaService.Draw.DONATED ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer holder) || !(target instanceof ServerPlayer donor)) return InteractionResult.SUCCESS;
        FormulaService.Draw result = FormulaService.draw(holder, donor, stack);
        FormulaService.explain(holder, donor, result);
        return result == FormulaService.Draw.DONATED || result == FormulaService.Draw.FORCED
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroesevolved.empty_syringe.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
