package com.jayptucker.heroesevolved.formula;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The finished Formula. Use it with an Empty Syringe in your inventory to inject yourself,
 * or use it on a crouching player to inject them. It only ever awakens an unclaimed power.
 */
public final class FormulaVialItem extends PurityItem {
    public FormulaVialItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer injector)) return InteractionResultHolder.sidedSuccess(stack, true);
        return inject(injector, injector, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer injector) || !(target instanceof ServerPlayer recipient)) return InteractionResult.SUCCESS;
        return inject(injector, recipient, stack) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    private static boolean inject(ServerPlayer injector, ServerPlayer recipient, ItemStack stack) {
        if (!FormulaService.hasNeedle(injector)) {
            injector.displayClientMessage(Component.translatable("formula.heroesevolved.inject.no_needle"), true);
            return false;
        }
        var result = FormulaService.inject(injector, recipient, stack, injector.getRandom().nextDouble());
        if (result == FormulaService.Injection.NOT_CONSENTING)
            injector.displayClientMessage(Component.translatable("formula.heroesevolved.inject.consent", recipient.getName()), true);
        return result != FormulaService.Injection.NOT_CONSENTING && result != FormulaService.Injection.NOTHING_LEFT
                && result != FormulaService.Injection.ECLIPSE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BloodPurity purity = stack.get(com.jayptucker.heroesevolved.registry.ModItems.BLOOD_PURITY.get());
        if (purity != null) tooltip.add(Component.translatable("formula.heroesevolved.success_odds",
                Math.round(FormulaService.successChance(purity) * 100)).withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.heroesevolved.formula_vial.tooltip").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
