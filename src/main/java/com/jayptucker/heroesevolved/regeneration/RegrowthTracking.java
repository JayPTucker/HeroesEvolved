package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.RegrowthPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Someone who comes into view of a regenerator learns how much Regrowth glows in her veins (vanilla only syncs your own). */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class RegrowthTracking {
    private RegrowthTracking() {}

    @SubscribeEvent public static void track(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            int regrowth = RegenerationService.regrowth(target);
            if (regrowth > 0) PacketDistributor.sendToPlayer(viewer, new RegrowthPayload(target.getId(), regrowth));
        }
    }
}
