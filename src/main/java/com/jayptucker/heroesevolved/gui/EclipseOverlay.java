package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import com.jayptucker.heroesevolved.data.EclipseTimeline.Phase;
import com.jayptucker.heroesevolved.network.ClientEclipseState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The Eclipse's shadow on anyone standing outside. Its strength follows the eclipse and how much
 * open sky is above the player; at the start and end of totality the umbra visibly sweeps
 * across the view.
 */
@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class EclipseOverlay {
    private static final int SHADOW_RGB = 0x030A1A;
    private static final float MAX_ALPHA = 0.32F;
    /** Width of the soft umbra edge as a fraction of the screen. */
    private static final float EDGE = 0.35F;
    private static final int EDGE_STRIPS = 24;

    /** Smoothed sky exposure, so walking under a tree does not flicker the shadow. */
    private static float exposure;

    private EclipseOverlay() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(
                        HeroesEvolved.MOD_ID,
                        "eclipse_tint"
                ),
                EclipseOverlay::render
        );
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        double now = ClientEclipseState.now(partialTick);

        if (now < 0 || minecraft.player == null || minecraft.level == null) {
            exposure = 0;
            return;
        }

        BlockPos eyes = BlockPos.containing(minecraft.player.getEyePosition(partialTick));
        float sky = minecraft.level.getBrightness(LightLayer.SKY, eyes) / 15.0F;
        exposure = Mth.lerp(0.05F, exposure, sky);

        EclipseTimeline timeline = ClientEclipseState.timeline();
        float alpha = timeline.darkness(now) * exposure * MAX_ALPHA;

        if (alpha <= 0.004F) {
            return;
        }

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        double sinceStart = now - timeline.totalityStart();
        double untilEnd = timeline.totalityEnd() - now;
        boolean arriving = timeline.phaseAt(now) == Phase.TOTALITY && sinceStart < EclipseTimeline.UMBRA_SWEEP_TICKS;
        boolean leaving = timeline.phaseAt(now) == Phase.TOTALITY && untilEnd < EclipseTimeline.UMBRA_SWEEP_TICKS;

        if (!arriving && !leaving) {
            guiGraphics.fill(0, 0, width, height, color(alpha));
            return;
        }

        // The umbra's edge crosses the screen left to right, arriving and leaving alike: dark
        // behind it on the way in, dark ahead of it on the way out.
        float sweep = (float) ((arriving ? sinceStart : EclipseTimeline.UMBRA_SWEEP_TICKS - untilEnd)
                / EclipseTimeline.UMBRA_SWEEP_TICKS);
        float base = alpha * 0.45F;
        float extra = alpha - base;
        float band = EDGE * width;
        float edge = (sweep * (1 + EDGE) - EDGE) * width;
        guiGraphics.fill(0, 0, width, height, color(base));

        if (arriving) {
            guiGraphics.fill(0, 0, Mth.clamp(Mth.floor(edge), 0, width), height, color(extra));
        } else {
            guiGraphics.fill(Mth.clamp(Mth.floor(edge + band), 0, width), 0, width, height, color(extra));
        }

        for (int strip = 0; strip < EDGE_STRIPS; strip++) {
            int left = Mth.floor(edge + strip * band / EDGE_STRIPS);
            int right = Mth.floor(edge + (strip + 1) * band / EDGE_STRIPS);
            float across = (strip + 0.5F) / EDGE_STRIPS;
            if (right > 0 && left < width) {
                guiGraphics.fill(Math.max(0, left), 0, Math.min(width, right), height,
                        color(extra * (arriving ? 1 - across : across)));
            }
        }
    }

    private static int color(float alpha) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | SHADOW_RGB;
    }
}
