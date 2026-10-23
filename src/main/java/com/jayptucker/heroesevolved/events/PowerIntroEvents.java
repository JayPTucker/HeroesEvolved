package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PowerIntroEvents {
    private PowerIntroEvents() {}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { PowerIntroService.tick(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PowerIntroService.forget(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PowerIntroService.forget(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PowerIntroService.dimensionChanged(player);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { PowerIntroService.clear(); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PowerIntroService.isActive(player) || event.getSource().is(DamageTypeTags.IS_FALL)
                && PowerIntroService.cushionFall(player)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PowerIntroService.isActive(player)) {
            event.setCanceled(true);
            player.setHealth(Math.max(1, player.getHealth()));
        }
    }
}
