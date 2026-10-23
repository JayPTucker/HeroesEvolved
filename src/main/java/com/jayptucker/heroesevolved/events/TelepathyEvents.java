package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.telepathy.TelepathyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TelepathyEvents {
    private TelepathyEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TelepathyService.tick(event.getServer());
        PrivateGlow.tick(event.getServer());
    }

    /**
     * Every target change goes through here, from vanilla AI or anyone
     * else, so suggestions and puppets cannot be undone by the mob's goals.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (TelepathyService.isForbiddenTarget(mob, event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
            return;
        }

        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer telepath
                && mob instanceof Enemy
                && mob.getTarget() != telepath
                && hasAwakenedTelepathy(telepath)
                && !EclipseService.arePowersSuppressed(telepath)) {
            TelepathyService.hearHostileThoughts(telepath, mob);
        }
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getNewDamage() > 0.0F) {
            TelepathyService.onLinkedMemberHurt(victim, event.getNewDamage());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TelepathyService.stopAll(player);
            com.jayptucker.heroesevolved.telepathy.FutureVisions.forget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TelepathyService.stopAll(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TelepathyService.stopAll(player);
        }
    }

    private static boolean hasAwakenedTelepathy(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(ModAbilities.TELEPATHY_ID)
                        && entry.getValue().isUnlocked())
                .isPresent();
    }
}
