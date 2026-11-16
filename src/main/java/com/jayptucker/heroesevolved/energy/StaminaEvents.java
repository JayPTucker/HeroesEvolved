package com.jayptucker.heroesevolved.energy;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Ticks held drains, and forgets a player's stamina bookkeeping when they leave. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class StaminaEvents {
    private StaminaEvents() {}

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        StaminaDrain.tick(event.getServer());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StaminaDrain.forget(player);
            PlayerEnergyService.forget(player);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PlayerEnergyService.sendNow(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        StaminaDrain.clear();
    }
}
