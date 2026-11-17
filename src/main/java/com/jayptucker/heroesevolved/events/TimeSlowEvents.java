package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.PlayerTimeSlow;
import com.jayptucker.heroesevolved.time.ProjectileTimeService;
import com.jayptucker.heroesevolved.time.TaczAmmoRecovery;
import com.jayptucker.heroesevolved.time.TimeSlowService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TimeSlowEvents {
    private TimeSlowEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        com.jayptucker.heroesevolved.time.SpaceTimeService.tick(event.getServer());
        TimeSlowService.tick(event.getServer());
        com.jayptucker.heroesevolved.time.TimeVeil.tick(event.getServer());
        ProjectileTimeService.tick(event.getServer());
        com.jayptucker.heroesevolved.time.TimeRewindService.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void slowPlayers(ServerTickEvent.Post event) {
        com.jayptucker.heroesevolved.time.TemporalSharing.tick(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) PlayerTimeSlow.update(player);
        com.jayptucker.heroesevolved.time.TemporalVoiceState.publish(event.getServer());
        // After the drags are set, so the picture the clients draw from names this tick's movers.
        com.jayptucker.heroesevolved.time.TimeFlow.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) com.jayptucker.heroesevolved.time.TimeFlow.catchUp(player);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob mob && !mob.level().isClientSide) {
            com.jayptucker.heroesevolved.time.TemporalMobTime.brain(mob);
            if (!com.jayptucker.heroesevolved.time.TemporalMobTime.advances(mob)) event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.item.PrimedTnt tnt && tnt.level() instanceof ServerLevel level) {
            // Lit TNT in slowed time burns at least six times slower, so there is time to run (or carry it off).
            int interval = com.jayptucker.heroesevolved.time.TimeFlow.fuse(Math.max(TimeSlowService.mobInterval(tnt),
                    com.jayptucker.heroesevolved.expansion.AdvancedTechniques.perceptionInterval(tnt)));
            if (interval > 1 && Math.floorMod(tnt.getId(), interval) != Math.floorMod(level.getGameTime(), interval)) event.setCanceled(true);
            return;
        }
        if (TimeSlowService.shouldSkipEntityTick(event.getEntity())
                || com.jayptucker.heroesevolved.expansion.AdvancedTechniques.perceptionSkips(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Projectile shot && shot.level() instanceof ServerLevel)
            ProjectileTimeService.afterTick(shot);
    }

    @SubscribeEvent
    public static void onTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Projectile shot)
            ProjectileTimeService.sendToTracker(player, shot);
    }

    @SubscribeEvent
    public static void onProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Projectile shot)
            TaczAmmoRecovery.track(shot, level);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        ProjectileTimeService.clear();
        TaczAmmoRecovery.clear();
        com.jayptucker.heroesevolved.time.TimeFlow.clear();
    }
}
