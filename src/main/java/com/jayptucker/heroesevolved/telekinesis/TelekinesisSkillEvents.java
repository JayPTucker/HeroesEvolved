package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TelekinesisSkillEvents {
    private TelekinesisSkillEvents() {}
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TelekinesisSkillService.cushion(player)) {
            event.setDamageMultiplier(0);
        }
    }
}
