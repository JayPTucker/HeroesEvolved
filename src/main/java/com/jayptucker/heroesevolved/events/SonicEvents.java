package com.jayptucker.heroesevolved.events;
import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.sonic.SonicService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SonicEvents {
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { SonicService.tick(event.getServer()); }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SonicService.cushion(player, event.getDistance())) event.setDamageMultiplier(0);
    }
    /** Explosions are the loudest thing there is: every singer nearby gains Resonance. */
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            SonicService.loudSound(level, event.getExplosion().center(), 15);
    }
    /** A mob drawn to a Resonant Note does not pick a new target until the note is gone. */
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() != null && SonicService.lured(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SonicService.forget(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SonicService.forget(player);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { SonicService.clear(); }
}
