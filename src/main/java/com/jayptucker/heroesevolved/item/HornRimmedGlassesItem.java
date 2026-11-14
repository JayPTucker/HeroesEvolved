package com.jayptucker.heroesevolved.item;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Noah Bennet's horn-rimmed glasses: worn on the head, where the game draws the item's own little model on the face (the
 * head slot's item layer), so they sit in front of the eyes rather than being painted on. Noah wears a pair in the Last
 * Day; a spare waits in the bunker's security chest, and a pair can be made from glass, sticks and an iron nugget.
 */
public final class HornRimmedGlassesItem extends Item implements Equipable {
    public HornRimmedGlassesItem(Properties properties) { super(properties); }

    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.HEAD; }

    @Override public Holder<SoundEvent> getEquipSound() { return SoundEvents.ARMOR_EQUIP_GENERIC; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroesevolved.horn_rimmed_glasses.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
