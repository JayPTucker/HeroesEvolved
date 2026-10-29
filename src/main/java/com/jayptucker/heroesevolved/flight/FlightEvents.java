package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class FlightEvents {
    private FlightEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightService.tick(player);
            FlightDynamicsService.tick(player);
            FlightSkillService.tick(player);
            DiveBombService.tick(player);
            // Momentum and maneuver flags are small, server-owned visual telemetry.
            if (player.tickCount % 5 == 0 && (FlightService.hasActiveSession(player)
                    || DiveBombService.isDiving(player))) {
                com.jayptucker.heroesevolved.network.FlightVisualSyncService.syncToTrackingPlayers(player);
            }
        }
    }

    /** A Dive Bomb's landing is the attack, not a fall. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && DiveBombService.consumeCushion(player)) {
            event.setDamageMultiplier(0.0F);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    private static void clear(ServerPlayer player) {
        FlightSkillService.forget(player);
        FlightSkillPassives.forget(player);
        DiveBombService.forget(player);
        FlightDynamicsService.forget(player);
        FlightService.stopForPowerRemoval(player);
        com.jayptucker.heroesevolved.entity.EntityCarryService.release(player);
        com.jayptucker.heroesevolved.network.FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        FlightSkillService.clear();
        FlightSkillPassives.clear();
        FlightDynamicsService.clear();
    }
}
