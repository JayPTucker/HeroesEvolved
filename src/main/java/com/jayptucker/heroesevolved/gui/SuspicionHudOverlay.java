package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.MorphClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Suspicion, as the shapeshifter sees it: a small almond eye just under the crosshair while they wear a shape. It fills
 * from the bottom in brass as they draw notice, opens wider as it fills, and pulses red when they are close to being
 * seen through. Hidden in their own body and when the HUD is hidden.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SuspicionHudOverlay {
    private static final int OUTLINE = 0xFF1F2A3D, EMPTY = 0xA03E6F86, FILL = 0xFFC29A5E, WARN = 0xFFD8553F, PUPIL = 0xFF101820;
    private static float value, shown;

    private SuspicionHudOverlay() {}

    public static void set(float suspicion) { value = Mth.clamp(suspicion, 0, 100); }

    public static float value() { return value; }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "suspicion"),
                SuspicionHudOverlay::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || MorphClient.form(mc.player.getId()) == null) { shown = 0; return; }
        shown += (value - shown) * .2F;
        float k = shown / 100F;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2 + 14;
        int halfWidth = 8, halfHeight = 2 + Math.round(2 * k);
        boolean warn = k > .7F && (mc.level.getGameTime() / 4) % 2 == 0;
        int fillTop = cy + halfHeight - Math.round((2 * halfHeight + 1) * k);
        for (int dy = -halfHeight; dy <= halfHeight; dy++) {
            float f = dy / (halfHeight + .5F);
            int w = Math.max(1, Math.round(halfWidth * Mth.sqrt(1 - f * f)));
            int y = cy + dy;
            int colour = y >= fillTop && k > 0 ? (warn ? WARN : FILL) : EMPTY;
            g.fill(cx - w, y, cx + w + 1, y + 1, colour);
            g.fill(cx - w - 1, y, cx - w, y + 1, OUTLINE);
            g.fill(cx + w + 1, y, cx + w + 2, y + 1, OUTLINE);
        }
        g.fill(cx - 2, cy - halfHeight - 1, cx + 3, cy - halfHeight, OUTLINE);
        g.fill(cx - 2, cy + halfHeight + 1, cx + 3, cy + halfHeight + 2, OUTLINE);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, PUPIL);
    }
}
