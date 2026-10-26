package com.jayptucker.heroesevolved.events;
import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.sonic.SonicService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SonicEvents {
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { SonicService.tick(event.getServer()); }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SonicService.cushion(player)) event.setDamageMultiplier(0);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SonicService.forget(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SonicService.forget(player);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { SonicService.clear(); }
}
