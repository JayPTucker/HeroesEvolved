package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.vibe.VibeService;
import com.jayptucker.heroesevolved.vibe.VibeSight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class VibeEvents {
    private VibeEvents() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { VibeService.tick(event.getServer()); }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { VibeService.clear(); }

    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getSource().getEntity() instanceof LivingEntity attacker) {
            VibeSight.record(event.getEntity(), attacker);
            VibeSight.wounded(event.getEntity(), attacker);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeProjectileTick(EntityTickEvent.Pre event) {
        var entity = event.getEntity();
        if (VibeService.hasGuard() && entity instanceof Projectile shot
                && shot.level() instanceof ServerLevel level
                && VibeService.intercept(level, shot, shot.position(), shot.position().add(shot.getDeltaMovement()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void projectile(ProjectileImpactEvent event) {
        var shot = event.getProjectile();
        if (!(shot.level() instanceof ServerLevel level)) return;
        var to = event.getRayTraceResult().getLocation();
        if (VibeService.intercept(level, shot, shot.position(), to)) event.setCanceled(true);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) VibeService.forget(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) VibeService.forget(player);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) VibeService.forget(player);
    }
}
