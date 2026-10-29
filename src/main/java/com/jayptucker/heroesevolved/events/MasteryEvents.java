package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.progression.MasteryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MasteryEvents {
    private MasteryEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy)) {
            return;
        }
        // Powers often kill indirectly (a slam, a fall, a fire): the last
        // player to hurt it gets the credit.
        ServerPlayer player = event.getSource().getEntity() instanceof ServerPlayer direct
                ? direct
                : dead.getKillCredit() instanceof ServerPlayer credited ? credited : null;
        if (player != null) {
            MasteryService.awardHostileMobKill(player, dead);
        }
    }

    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || target == player) {
            return;
        }
        if (target instanceof Enemy || target instanceof ServerPlayer) {
            MasteryService.awardPowerHit(player, event.getNewDamage());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MasteryService.forgetPlayer(player.getUUID());
        }
    }
}
