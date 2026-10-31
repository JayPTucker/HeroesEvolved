package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ClientDensity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Under the ground, a phaser sees only a few blocks of dark, grainy rock: no suffocation overlay, and
 * thick fog so travelling underground never becomes a way to see ores or bases through the stone.
 * What they do get is tremor sense: the footsteps of everything walking above (see {@link DensityEffects}).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class DensityClient {
    private DensityClient() {}

    private static boolean sunk() {
        var player = Minecraft.getInstance().player;
        return player != null && ClientDensity.sunk() && Minecraft.getInstance().getCameraEntity() == player;
    }

    @SubscribeEvent public static void overlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK && ClientDensity.sunk()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        if (!sunk()) return;
        event.setNearPlaneDistance(0);
        event.setFarPlaneDistance(3.5F);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void color(ViewportEvent.ComputeFogColor event) {
        if (!sunk()) return;
        event.setRed(0.09F);
        event.setGreen(0.13F);
        event.setBlue(0.13F);
    }
}
