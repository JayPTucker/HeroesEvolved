package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TelekinesisSkillEvents {
    private TelekinesisSkillEvents() {}

    /** Grounding's stored landing first, then Soft Landing's catch: a palm under a friend (or yourself) at the end of a long fall. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TelekinesisSkillService.cushion(player)
                || event.getDamageMultiplier() > 0 && TelekinesisSkillService.catchFall(event.getEntity(), event.getDistance()))
            event.setDamageMultiplier(0);
    }
}
