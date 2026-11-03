package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.CryoRideStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class CryoRideEvents {
    private CryoRideEvents() {}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p) CryoRideService.tick(p);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer rider)
            PacketDistributor.sendToPlayer(viewer, new CryoRideStatePayload(rider.getId(), CryoRideService.isRiding(rider),
                    CryoRideService.speedStep(rider), (float) CryoRideService.rideSpeed(rider)));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) CryoRideService.forget(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) CryoRideService.forget(p);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) CryoRideService.forget(p);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { CryoRideService.clear(); }
}
