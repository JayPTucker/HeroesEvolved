package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The city is a vision: players cannot die, loot it, or build an escape portal. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TimeIntroEvents {
    private TimeIntroEvents() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { TimeIntroService.tick(event.getServer()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TimeIntroService.login(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TimeIntroService.logout(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TimeIntroService.login(player);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { TimeIntroService.shutdown(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TimeIntroService.isVisiting(player)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TimeIntroService.isVisiting(player)) {
            event.setCanceled(true);
            player.setHealth(Math.max(1, player.getHealth()));
        }
    }
    @SubscribeEvent public static void spawn(EntityJoinLevelEvent event) {
        if (TimeIntroService.isCity(event.getLevel()) && event.getEntity() instanceof Mob
                && !TimeIntroService.isCurrentCrowd(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (TimeIntroService.isCity(event.getPlayer().level())) event.setCanceled(true);
    }
    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() != null && TimeIntroService.isCity(event.getEntity().level())) event.setCanceled(true);
    }
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (TimeIntroService.isCity(event.getLevel())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public static void rightItem(PlayerInteractEvent.RightClickItem event) {
        if (TimeIntroService.isCity(event.getLevel())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (TimeIntroService.isCity(event.getLevel())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (TimeIntroService.isCity(event.getEntity().level())) event.setCanceled(true);
    }
}
