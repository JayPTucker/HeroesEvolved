package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Feeds fights into the adrenaline check and keeps a freshly awakened player alive. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class AwakeningEvents {
    private AwakeningEvents() {
    }

    /** Every blow counts as a fight, even one an intro or shield cancels. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void incoming(LivingIncomingDamageEvent event) {
        AwakeningService.recordCombat(event.getEntity(), event.getSource());

        if (event.getEntity() instanceof ServerPlayer player && AwakeningService.inGrace(player)) {
            event.setCanceled(true);
        }
    }

    /** Lowest priority, so it sees the damage every other reduction has already settled on. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setNewDamage(AwakeningService.interceptDamage(player, event.getSource(), event.getNewDamage()));
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AwakeningService.forget(player);
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AwakeningService.forget(player);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        AwakeningService.clear();
    }
}
