package com.jayptucker.heroesevolved.wither;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A fragment remembers the way back into the cage. */
public final class BreachSeedItem extends Item {
    public BreachSeedItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        var at=player.position().add(player.getLookAngle().multiply(1,0,1).normalize().scale(4));
        if(!WitherArena.start((ServerLevel)level,at,player.getYRot()))return InteractionResultHolder.fail(stack);
        if(!player.isCreative())stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}
