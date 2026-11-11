package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SpeedEvents {
    @SubscribeEvent public static void eat(LivingEntityUseItemEvent.Tick e) {
        if (e.getEntity() instanceof net.minecraft.world.entity.player.Player player
                && e.getItem().has(net.minecraft.core.component.DataComponents.FOOD)
                && e.getItem().getUseAnimation() == net.minecraft.world.item.UseAnim.EAT
                && com.jayptucker.heroesevolved.speed.SpeedMovement.accelerating(player)
                && e.getDuration() > 1)
            // Vanilla consumes the final tick and applies hunger, effects, and stack changes once.
            e.setDuration(Math.max(1, e.getDuration() - 3));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) { SpeedService.tick(e.getServer()); }
    @SubscribeEvent(priority = EventPriority.HIGH) public static void hurt(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && SpeedService.dodge(p, e.getSource())) { e.setCanceled(true); return; }
        if (e.getSource().getDirectEntity() instanceof ServerPlayer attacker
                && e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) && !SpeedService.inPowerHit())
            e.setAmount(SpeedService.punch(attacker, e.getEntity(), e.getAmount()));
    }
    // A speedster under Accelerate lands on their feet from any height.
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && SpeedService.active(p)) { e.setDistance(0); e.setDamageMultiplier(0); }
    }
    @SubscribeEvent public static void attack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) SpeedService.beforePunch(p, e.getTarget());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) SpeedService.forget(p); }
    // A death or a portal ends the run but not the minute behind it: Overrun can still go back into it.
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) SpeedService.interrupt(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) SpeedService.interrupt(p); }
    @SubscribeEvent public static void stop(ServerStoppingEvent e) { SpeedService.clear(); }
}
