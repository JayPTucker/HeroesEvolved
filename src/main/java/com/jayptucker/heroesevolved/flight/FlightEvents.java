package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class FlightEvents {
    private FlightEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightService.tick(player);
        }
    }
}