package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.ProjectileTimeService;
import com.jayptucker.heroesevolved.time.TimeSlowService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TimeSlowEvents {
    private TimeSlowEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        com.jayptucker.heroesevolved.time.SpaceTimeService.tick(event.getServer());
        TimeSlowService.tick(event.getServer());
        ProjectileTimeService.tick(event.getServer());
        com.jayptucker.heroesevolved.time.TimeRewindService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (TimeSlowService.shouldSkipEntityTick(event.getEntity())
                || com.jayptucker.heroesevolved.expansion.AdvancedTechniques.perceptionSkips(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Projectile shot && shot.level() instanceof ServerLevel)
            ProjectileTimeService.afterTick(shot);
    }

    @SubscribeEvent
    public static void onTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Projectile shot)
            ProjectileTimeService.sendToTracker(player, shot);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        ProjectileTimeService.clear();
    }
}
