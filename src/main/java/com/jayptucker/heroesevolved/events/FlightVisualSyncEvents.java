package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.FlightVisualSyncService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class FlightVisualSyncEvents {
    private FlightVisualSyncEvents() {
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer receivingPlayer
                && event.getTarget() instanceof ServerPlayer flyingPlayer) {
            FlightVisualSyncService.syncToPlayer(
                    flyingPlayer,
                    receivingPlayer
            );
        }
    }
}
