package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.regeneration.RegenerationService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class RegenerationEvents {
    private RegenerationEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RegenerationService.tick(event.getServer());
    }

    /**
     * Runs early so other mods see the player survive. The health must be
     * restored here: a cancelled death at zero health still plays out.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && hasAwakenedRegeneration(player)
                && !EclipseService.arePowersSuppressed(player)
                && RegenerationService.tryResurrect(
                        player,
                        PlayerProgressionService.getLevel(player),
                        event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDamaged(
            net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event
    ) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getNewDamage() > 0.0F
                && hasAwakenedRegeneration(player)) {
            RegenerationService.bleed(player, event.getNewDamage());
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && RegenerationService.isIndestructible(player)) {
            event.setDamageMultiplier(0.0F);
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && RegenerationService.isIndestructible(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RegenerationService.forget(player);
        }
    }

    private static boolean hasAwakenedRegeneration(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey()
                        .equals(ModAbilities.REGENERATION_ID)
                        && entry.getValue().isUnlocked())
                .isPresent();
    }
}
