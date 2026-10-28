package com.jayptucker.heroesevolved.formula;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Regenerative blood. Spun in a Centrifuge it becomes the Formula's catalyst; injected
 * directly it briefly lends its healing, as Claire's blood did.
 */
public final class BloodSampleItem extends PurityItem {
    public BloodSampleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.heal(6);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            player.clearFire();
            level.playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6F, 1.6F);
            player.displayClientMessage(Component.translatable("formula.heroesevolved.blood_injected"), true);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
