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

/** Conceals the complete player render; optical layers and faint owner hands are rendered separately. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class UnseenClient {
    private static final Set<Integer> HIDDEN = new HashSet<>();

    private UnseenClient() {}

    public static void update(int entity, boolean hidden) { update(entity, hidden, true); }
    public static void update(int entity, boolean hidden, boolean animate) {
        boolean changed = HIDDEN.contains(entity) != hidden;
        if (changed && animate) OpticalClient.transition(entity, hidden);
        if (hidden) HIDDEN.add(entity); else HIDDEN.remove(entity);
    }

    public static boolean hidden(int entity) { return HIDDEN.contains(entity); }

    @SubscribeEvent public static void render(RenderLivingEvent.Pre<?, ?> event) {
        var mc = Minecraft.getInstance();
        if (HIDDEN.contains(event.getEntity().getId())) event.setCanceled(true);
    }

    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) HIDDEN.clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { HIDDEN.clear(); }
}
