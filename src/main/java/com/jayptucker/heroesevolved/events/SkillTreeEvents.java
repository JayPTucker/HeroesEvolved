package com.jayptucker.heroesevolved.events;
import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.skills.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SkillTreeEvents {
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || EclipseService.arePowersSuppressed(player)) return;
        PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked()).ifPresent(e ->
                event.setAmount(event.getAmount() * (float) (1 + SkillTreeService.bonus(player, e.getKey(), 1))));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { SkillActionService.tick(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SkillActionService.forget(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SkillActionService.forget(player);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SkillActionService.clear(); }
}
