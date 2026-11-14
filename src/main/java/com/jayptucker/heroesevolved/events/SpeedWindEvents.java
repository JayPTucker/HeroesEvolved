package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedWindArms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Wind Arms catching shots. A bullet can cross the whole funnel inside one tick, so each shot's step is checked before
 * it moves, and again when it is about to hit something; TaCZ rounds are checked a third time in {@code TaczLink}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SpeedWindEvents {
    private SpeedWindEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void beforeProjectileTick(EntityTickEvent.Pre event) {
        if (SpeedWindArms.any() && event.getEntity() instanceof Projectile shot && shot.level() instanceof ServerLevel level)
            SpeedWindArms.catchShot(level, shot, shot.position(), shot.position().add(shot.getDeltaMovement()));
    }

    @SubscribeEvent public static void projectile(ProjectileImpactEvent event) {
        var shot = event.getProjectile();
        if (!SpeedWindArms.any() || !(shot.level() instanceof ServerLevel level)) return;
        if (SpeedWindArms.catchShot(level, shot, shot.position(), event.getRayTraceResult().getLocation())) event.setCanceled(true);
    }
}
