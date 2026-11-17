package com.jayptucker.heroesevolved.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Slow curtains of light on the flanks of the title sky. */
final class TitleAurora {
    private static final int SEGMENTS = 72;
    private static final float[] HEIGHTS = {-1.0F, -0.62F, -0.25F, 0, 0.09F, 0.36F};
    private static final float[] OPACITIES = {0, 0.26F, 0.57F, 1, 0.48F, 0};
    private static final int[] COLORS = {0x8479C0, 0x8479C0, 0x39A99D, 0x90F9CC, 0x45C5BA, 0x45C5BA};

    private record Fold(float x, float y, float height, float light) {}

    private TitleAurora() {}

    static void draw(GuiGraphics graphics, int width, int height, float age, float motion) {
        float strength = TitleMenu.ease(age, 4.6F, 7.1F);
        if (strength <= 0) return;

        TitleMenu.tint(graphics, 1);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float time = age * motion;
        curtain(vertices, matrix, width, height, false, time * 0.43F, 0.44F * strength);
        curtain(vertices, matrix, width, height, true, 1.8F - time * 0.36F, 0.40F * strength);
        BufferUploader.drawWithShader(vertices.buildOrThrow());
        RenderSystem.defaultBlendFunc();
    }

    private static void curtain(BufferBuilder vertices, Matrix4f matrix, int width, int height, boolean mirror,
                                float phase, float strength) {
        Fold previous = fold(0, phase, strength);
        for (int step = 1; step <= SEGMENTS; step++) {
            Fold next = fold(step / (float) SEGMENTS, phase, strength);
            Fold left = mirror ? next : previous;
            Fold right = mirror ? previous : next;
            for (int band = 0; band < HEIGHTS.length - 1; band++) {
                vertex(vertices, matrix, left, width, height, mirror, band);
                vertex(vertices, matrix, left, width, height, mirror, band + 1);
                vertex(vertices, matrix, right, width, height, mirror, band + 1);
                vertex(vertices, matrix, right, width, height, mirror, band);
            }
            previous = next;
        }
    }

    private static Fold fold(float along, float phase, float strength) {
        float x = -0.12F + 0.43F * along;
        float y = 0.79F - 0.68F * along + 0.061F * Mth.sin(7.0F * along + phase)
                + 0.026F * Mth.sin(16.0F * along - phase * 0.8F);
        float height = 0.22F + 0.074F * Mth.sin(9.0F * along - phase);
        float pleat = 0.5F + 0.5F * Mth.cos(88.0F * along + 2.4F * Mth.sin(along * 11.0F + phase));
        float envelope = TitleMenu.ease(along, 0, 0.25F) * (1 - TitleMenu.ease(along, 0.57F, 1));
        return new Fold(x, y, height, strength * envelope * (0.38F + 0.62F * pleat * pleat));
    }

    private static void vertex(BufferBuilder vertices, Matrix4f matrix, Fold fold, int width, int height,
                               boolean mirror, int band) {
        float x = fold.x + HEIGHTS[band] * 0.037F;
        int color = COLORS[band];
        vertices.addVertex(matrix, (mirror ? 1 - x : x) * width, (fold.y + HEIGHTS[band] * fold.height) * height, 0)
                .setColor((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F,
                        fold.light * OPACITIES[band]);
    }
}
