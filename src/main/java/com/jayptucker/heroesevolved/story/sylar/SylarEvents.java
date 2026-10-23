package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SylarEvents {
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { SylarStoryService.tick(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SylarStoryService.forget(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SylarStoryService.forget(player);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { SylarStoryService.clear(event.getServer()); }
}
