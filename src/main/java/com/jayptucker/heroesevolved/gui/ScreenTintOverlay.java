package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ClientScreenTintState;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Draws power-driven screen tints: flashes, vignettes and pulses. */
@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class ScreenTintOverlay {
    private static final int VIGNETTE_STEPS = 14;

    private ScreenTintOverlay() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(
                        HeroesEvolved.MOD_ID, "power_screen_tint"
                ),
                ScreenTintOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        for (ClientScreenTintState.ActiveTint tint : ClientScreenTintState.active()) {
            int peakAlpha = (tint.color() >>> 24) & 0xFF;
            int alpha = Math.round(peakAlpha * tint.strength());
            if (alpha <= 0) {
                continue;
            }

            int rgb = tint.color() & 0xFFFFFF;
            if (tint.style() == ScreenTintPayload.Style.FLASH
                    || tint.style() == ScreenTintPayload.Style.HOLD) {
                // Flat wash; every other style darkens the edges.
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                        (alpha << 24) | rgb);
            } else {
                drawVignette(graphics, rgb, alpha);
            }
        }
    }

    /** Nested frames that grow fainter toward the centre of the screen. */
    private static void drawVignette(GuiGraphics graphics, int rgb, int alpha) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int depth = Math.max(8, Math.min(width, height) / 5);
        int band = Math.max(1, depth / VIGNETTE_STEPS);

        for (int step = 0; step < VIGNETTE_STEPS; step++) {
            int stepAlpha = alpha * (VIGNETTE_STEPS - step) / VIGNETTE_STEPS;
            int color = (stepAlpha << 24) | rgb;
            int inset = step * band;
            graphics.fill(inset, inset, width - inset, inset + band, color);
            graphics.fill(inset, height - inset - band, width - inset,
                    height - inset, color);
            graphics.fill(inset, inset + band, inset + band,
                    height - inset - band, color);
            graphics.fill(width - inset - band, inset + band, width - inset,
                    height - inset - band, color);
        }
    }
}
