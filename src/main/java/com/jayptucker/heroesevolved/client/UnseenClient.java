package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import java.util.HashSet;
import java.util.Set;

/** Skips rendering players who are fully unseen. You always still see yourself. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class UnseenClient {
    private static final Set<Integer> HIDDEN = new HashSet<>();

    private UnseenClient() {}

    public static void update(int entity, boolean hidden) {
        if (hidden) HIDDEN.add(entity); else HIDDEN.remove(entity);
    }

    @SubscribeEvent public static void render(RenderLivingEvent.Pre<?, ?> event) {
        var mc = Minecraft.getInstance();
        if (HIDDEN.contains(event.getEntity().getId()) && event.getEntity() != mc.player) event.setCanceled(true);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { HIDDEN.clear(); }
}
