package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.hydro.HydroService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Hydrokinesis's passives and lifecycle: the reservoir, water's shelter, Spirit Water, and cleanup. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class HydroEvents {
    private HydroEvents() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { HydroService.tick(event.getServer()); }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && HydroService.isHydrokinetic(player)) HydroService.playerTick(player);
    }

    /** Octopus Form's shell takes a third off every blow. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HydroService.octopus(player)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            event.setAmount(HydroService.shelter(player, event.getAmount()));
    }

    /** Spirit Water: a killing blow turned aside by the water carried. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void lethal(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && HydroService.isHydrokinetic(player)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            event.setNewDamage(HydroService.spiritWater(player, event.getNewDamage()));
    }

    /** Someone new sees a hydrokinetic: tell them how much water they carry and what they are doing. */
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer bender && HydroService.isHydrokinetic(bender)) HydroService.sync(bender, true);
    }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HydroService.isHydrokinetic(player)) HydroService.respawned(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HydroService.leave(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HydroService.forget(player);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HydroService.forget(player);
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) { HydroService.clear(); }
}
