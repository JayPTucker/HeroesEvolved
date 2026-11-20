package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Repair Ticket from Gray &amp; Sons: "Ready for collection." The way into Sylar's Collection. Read it (hold use for two
 * seconds while the ticking gets louder) and the world folds: you, and everyone within sixteen blocks, are standing in
 * his hall. The first one is left in your pocket by the story; after that they are made from the brass movement his
 * fights leave. The ticket comes back to your pocket if the fight is lost, and is torn when he falls.
 */
public final class RepairTicketItem extends Item {
    /** How long reading the ticket takes. */
    public static final int READ_TICKS = 40;

    public RepairTicketItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            Component refused = CollectionArena.refusal(server);
            if (refused != null) {
                server.displayClientMessage(refused.copy().withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        if (!level.isClientSide)
            level.playSound(null, player.getX(), player.getY(), player.getZ(), com.jayptucker.heroesevolved.sounds.ModSounds.SYLAR_WATCH.get(),
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return READ_TICKS; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.TOOT_HORN; }

    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        int read = READ_TICKS - remaining;
        // A watch ticking, louder and quicker the longer you read.
        if (read % Math.max(2, 8 - read / 6) == 0)
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS,
                    0.3F + read / (float) READ_TICKS, 1.2F + read / (float) READ_TICKS * 0.6F);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && CollectionArena.open(player) == null) {
            Component refused = CollectionArena.refusal(player);
            if (refused != null) player.displayClientMessage(refused.copy().withStyle(ChatFormatting.GRAY), true);
        }
        return stack;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.heroesevolved.repair_ticket.line1").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("item.heroesevolved.repair_ticket.line2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.heroesevolved.repair_ticket.line3").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Puts a ticket in {@code player}'s pocket (at their feet if it is full). */
    public static void give(ServerPlayer player) {
        ItemStack ticket = new ItemStack(ModItems.REPAIR_TICKET.get());
        if (!player.getInventory().add(ticket)) player.drop(ticket, false);
    }

    /** The ticket that opened a won fight tears along its perforation. */
    static void tear(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof RepairTicketItem) {
                stack.shrink(1);
                player.sendSystemMessage(Component.translatable("story.heroesevolved.sylar.ticket.torn").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                return;
            }
        }
    }
}
