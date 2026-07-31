package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.TimeStopService;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TimeStopEvents {
    private TimeStopEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TimeStopService.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (TimeStopService.shouldStopEntityTick(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
