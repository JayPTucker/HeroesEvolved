package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class CombatTrackingEvents {
    private CombatTrackingEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        player.setData(
                ModDataAttachments.PLAYER_COMBAT.get(),
                new PlayerCombatData(player.serverLevel().getGameTime())
        );
    }
}