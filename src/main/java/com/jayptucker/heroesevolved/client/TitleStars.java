package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.client.effects.setpiece.Canvas;
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

/** Distant points of light in the opening shot. */
final class TitleStars {
    private static final int SEED = 317;
    private static final int COLUMNS = 11;
    private static final int ROWS = 6;
    private static final int[] POINT_X = {1, 0, -1, 0};
    private static final int[] POINT_Y = {0, -1, 0, 1};

    private TitleStars() {}

    static void draw(GuiGraphics graphics, int width, int height, float age, float motion) {
        float fade = TitleMenu.ease(age, 0.38F, 1.28F) * (1 - TitleMenu.ease(age, 3.8F, 5.35F));
        if (fade <= 0) return;

        TitleMenu.tint(graphics, 1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var matrix = graphics.pose().last().pose();
        BufferBuilder stars = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float sweep = (TitleEclipse.yaw(age, motion) - TitleEclipse.yaw(0, motion)) * motion;
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            float depth = Canvas.hash(SEED + 2, i);
            float u = (i % COLUMNS + Canvas.hash(SEED, i)) / COLUMNS - sweep * (0.018F + 0.018F * depth);
            u -= Mth.floor(u);
            float v = (i / COLUMNS + Canvas.hash(SEED + 1, i)) / ROWS + sweep * (0.004F + 0.007F * depth);
            float border = TitleMenu.ease(Math.min(u, 1 - u), 0, 0.035F)
                    * TitleMenu.ease(Math.min(v, 1 - v), 0, 0.028F);
            float distance = Math.abs(u - 0.5F) + 0.72F * Math.abs(v - 0.5F);
            float opacity = fade * border * (0.30F + 0.32F * depth) * (0.56F + 0.44F * TitleMenu.ease(distance, 0.19F, 0.38F));
            float radius = height * (0.0026F + 0.0018F * depth);
            star(stars, matrix, width * u, height * v, radius, opacity);
        }
        BufferUploader.drawWithShader(stars.buildOrThrow());
    }

    private static void star(BufferBuilder stars, Matrix4f matrix, float x, float y, float radius, float opacity) {
        // Give each star a steady core and feather the edge as it drifts across pixels.
        float core = radius * 0.55F;
        for (int point = 0; point < 4; point++) {
            vertex(stars, matrix, x, y, point, core, opacity);
        }
        for (int point = 0; point < 4; point++) {
            int next = (point + 1) % 4;
            vertex(stars, matrix, x, y, point, core, opacity);
            vertex(stars, matrix, x, y, point, radius, 0);
            vertex(stars, matrix, x, y, next, radius, 0);
            vertex(stars, matrix, x, y, next, core, opacity);
        }
    }

    private static void vertex(BufferBuilder stars, Matrix4f matrix, float x, float y, int point, float radius, float opacity) {
        stars.addVertex(matrix, x + POINT_X[point] * radius, y + POINT_Y[point] * radius, 0)
                .setColor(0.83F, 0.90F, 1, opacity);
    }
}
