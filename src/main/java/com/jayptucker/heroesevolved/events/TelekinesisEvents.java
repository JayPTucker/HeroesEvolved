package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.telekinesis.TelekinesisService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TelekinesisEvents {
    private TelekinesisEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TelekinesisService.tick(event.getServer());
        com.jayptucker.heroesevolved.telekinesis.TelekineticArsenalService.tick(event.getServer());
        com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onSlamLanding(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (TelekinesisService.isSlammed(event.getEntity())) event.setDamageMultiplier(0);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TelekinesisService.stopAll(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TelekinesisService.stopAll(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        TelekinesisService.clear();
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TelekinesisService.stopAll(player);
        }
    }
}
