package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

@EventBusSubscriber(modid=HeroesEvolved.MOD_ID)
public final class WitherEvents {
    private static final Map<UUID,Long> SWINGS=new HashMap<>();
    private WitherEvents(){}
    @SubscribeEvent public static void formed(EntityJoinLevelEvent event) {
        if(event.loadedFromDisk()||!(event.getLevel() instanceof ServerLevel level)
                ||!(event.getEntity() instanceof WitherBoss wither)||wither.getInvulnerableTicks()<=0)return;
        event.setCanceled(true);
        if(!WitherArena.start(level,wither.position(),wither.getYRot()))level.getServer().execute(()->{
            for(ItemStack stack:List.of(new ItemStack(Items.SOUL_SAND,4),new ItemStack(Items.WITHER_SKELETON_SKULL,3)))
                level.addFreshEntity(new ItemEntity(level,wither.getX(),wither.getY(),wither.getZ(),stack));
        });
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){WitherArena.tick(event.getServer());}
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if(event.getEntity() instanceof ServerPlayer p&&event.getTarget() instanceof RiftWitherEntity) {
            if(p.getAttackStrengthScale(.5F)>=.9F)swing(p);else SWINGS.remove(p.getUUID());
        }
    }
    public static void swing(Player p){SWINGS.put(p.getUUID(),p.level().getGameTime());}
    static boolean fullSwing(Player p){Long time=SWINGS.remove(p.getUUID());return time!=null&&p.level().getGameTime()-time<=1;}
    @SubscribeEvent public static void block(BlockEvent.BreakEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!level.dimension().equals(WitherGeometry.DIMENSION)||event.getPlayer().isCreative())return;
        RiftWitherEntity boss=WitherArena.boss();
        if(boss==null||boss.anchor()<0||!event.getPos().equals(WitherGeometry.ANCHORS.get(boss.anchor())))event.setCanceled(true);
    }
    /** The Wither's brood leaves nothing behind: no skulls, bones or swords, and no experience. */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void broodDrops(LivingDropsEvent event) {
        if(event.getEntity().getPersistentData().getBoolean(WitherMoves.BROOD)){event.getDrops().clear();event.setCanceled(true);}
    }
    @SubscribeEvent public static void broodExperience(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if(event.getEntity().getPersistentData().getBoolean(WitherMoves.BROOD))event.setDroppedExperience(0);
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void drops(LivingDropsEvent event) {
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)||!WitherArena.inside(p))return;
        for(ItemEntity drop:event.getDrops())WitherData.get(p.getServer()).stash(p.getUUID(),drop.getItem(),p.registryAccess());
        event.getDrops().clear();event.setCanceled(true);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer p){if(WitherArena.inside(p))WitherArena.sendHome(p);if(p.isAlive())WitherArena.deliver(p);}
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if(event.getEntity() instanceof ServerPlayer p){if(WitherArena.inside(p))WitherArena.sendHome(p);else WitherData.get(p.getServer()).clearHome(p.getUUID());WitherArena.deliver(p);}
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer p&&!WitherArena.moving(p)&&event.getFrom().equals(WitherGeometry.DIMENSION)) {
            WitherData.get(p.getServer()).clearHome(p.getUUID());WitherArena.deliver(p);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){SWINGS.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppingEvent event){WitherArena.reset(event.getServer(),true);SWINGS.clear();}
}
