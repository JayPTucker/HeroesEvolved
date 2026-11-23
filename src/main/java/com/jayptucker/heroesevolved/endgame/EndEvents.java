package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid=HeroesEvolved.MOD_ID)
public final class EndEvents {
    private EndEvents(){}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){EndChannel.tick();EndBattle.tick(event.getServer());}
    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!event.getDimension().equals(Level.END)||p.level().dimension().equals(Level.END))return;
        if(!p.isCreative()&&!p.isSpectator()&&!EndBattle.eligible(p)){event.setCanceled(true);p.sendSystemMessage(Component.translatable("endgame.heroesevolved.level",EndGeometry.MIN_LEVEL));return;}
        EndData.get(p.getServer()).home(p.getUUID(),new EndData.Home(p.level().dimension(),p.position(),p.getYRot(),p.getXRot()));
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event){
        if(event.getEntity() instanceof ServerPlayer p&&(event.getTarget() instanceof EnderDragon||event.getTarget() instanceof EnderDragonPart
                ||event.getTarget() instanceof SylarEntity sylar&&sylar.inheritance())){
            if(p.getAttackStrengthScale(.5F)>=.9F)EndBattle.swing(p);else EndBattle.unchargedSwing(p);
        }
    }
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event){if(event.getEntity() instanceof ServerPlayer p&&event.getNewDamage()>0)EndChannel.cancel(p);}
    @SubscribeEvent(priority=EventPriority.HIGH) public static void anchor(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)||!p.level().dimension().equals(Level.END)||!event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD))return;
        for(var hand:net.minecraft.world.InteractionHand.values()){
            var stack=p.getItemInHand(hand);if(!stack.is(ModItems.ANCHOR_OF_RETURN.get()))continue;
            stack.shrink(1);p.setHealth(Math.max(2,p.getMaxHealth()*.35F));event.setCanceled(true);EndBattle.rescue(p);
            p.sendSystemMessage(Component.translatable("endgame.heroesevolved.anchor_saved"));return;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void drops(LivingDropsEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)||!EndBattle.active()||!EndBattle.inArena(p)||!EndBattle.current().party.containsKey(p.getUUID()))return;
        var data=EndData.get(p.getServer());for(var drop:event.getDrops())data.stash(p.getUUID(),drop.getItem(),p.registryAccess());event.getDrops().clear();event.setCanceled(true);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p){
        if(p.level().dimension().equals(Level.END)&&EndData.get(p.getServer()).participants.contains(p.getUUID())&&!EndData.get(p.getServer()).completed)EndBattle.rescue(p);
        if(p.isAlive())EndBattle.deliver(p);
    }}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer p){EndChannel.cancel(p);EndBattle.deliver(p);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)EndChannel.cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p){EndChannel.cancel(p);
        if(event.getFrom().equals(Level.END)&&!EndBattle.moving(p)){EndData.get(p.getServer()).forgetHome(p.getUUID());EndBattle.deliver(p);}
    }}
    @SubscribeEvent public static void stop(ServerStoppingEvent event){EndBattle.reset(event.getServer(),true);EndChannel.clear();}
}
