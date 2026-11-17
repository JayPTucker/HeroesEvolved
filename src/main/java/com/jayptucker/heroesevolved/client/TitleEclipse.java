package com.jayptucker.heroesevolved.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** The cube's shadow and silhouette share the same projected corners as its lit faces. */
final class TitleEclipse {
    static final float DOCK_START = 3.65F;
    private static final float DOCK_END = 5.5F;
    private static final float TOTALITY = 3.25F;
    private static final float SUN_HALF = 13;
    private static final int HALF = 36;
    private static final int SHADOW_STEPS = 8;
    private static final float TEXEL_INSET = 0.5F / 256;
    private static final int[][] CORNERS = {
            {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
            {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}
    };
    private static final int[][] FACES = {{0, 3, 2, 1}, {4, 0, 1, 5}, {1, 2, 6, 5}};
    private static final int[] SILHOUETTE = {4, 0, 3, 2, 6, 5};
    private static final ResourceLocation SURFACE = TitleMenu.texture("eclipse_surface");
    private static final ResourceLocation HALO = TitleMenu.texture("eclipse_halo");
    private static final ResourceLocation CORONA = TitleMenu.texture("corona");
    private static final ResourceLocation SUN = TitleMenu.texture("eclipse_sun");
    private static final ResourceLocation FIRST_LIGHT = TitleMenu.texture("first_light");

    private record Point(float x, float y, float z) {
        float dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }
    }

    private TitleEclipse() {}

    static void draw(GuiGraphics graphics, int width, int height, float logoX, float logoY, float logoScale,
                     float age, float motion) {
        float arrival = TitleMenu.ease(age, DOCK_START, DOCK_END);
        float scale = Mth.lerp(arrival, Math.min(width, height) * 0.43F / (2 * HALF), logoScale);
        float opening = TitleMenu.ease(age, 0.14F, 1.25F);
        if (scale <= 0 || opening <= 0) return;

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(Mth.lerp(arrival, width / 2.0F, logoX), Mth.lerp(arrival, height / 2.0F, logoY), 0);
        pose.scale(scale, scale, 1);

        float lightTime = age * motion;
        if (arrival < 1) {
            drawCube(graphics, age, arrival, opening, lightTime, motion);
        } else {
            logoHalo(graphics, lightTime, 1);
            glow(graphics, CORONA, 0, 0, 1);
            logoGlare(graphics, lightTime, 1);
        }
        pose.popPose();
    }

    private static void drawCube(GuiGraphics graphics, float age, float arrival, float opening,
                                 float lightTime, float motion) {
        float turn = 1 - arrival;
        // Keep turning in the same direction into the logo's face, with a small roll as the world turns.
        float pitch = (0.28F + 0.095F * motion * Mth.sin(age * 1.25F + 0.55F)) * turn;
        float roll = 0.12F * motion * Mth.sin(age * 1.05F - 0.65F) * turn;
        Point[] points = project(yaw(age, motion), pitch, roll);

        float coverage = TitleMenu.ease(age, 0.46F, TOTALITY);
        float inset = Mth.lerp(coverage, 1.0F, SUN_HALF) + 9 * TitleMenu.ease(age, TOTALITY, 3.95F);
        Point sun = sunBehind(points, Mth.lerp(coverage, -0.74F, -0.5F), inset);
        float shadow = Mth.lerp(coverage, 0.26F, 1);
        float crown = TitleMenu.ease(coverage, 0.35F, 1);
        float flat = TitleMenu.ease(arrival, 0.87F, 1);

        float sunshine = TitleMenu.ease(age, 0.32F, 1.24F) * (1 - TitleMenu.ease(inset, SUN_HALF, SUN_HALF + 7));
        glow(graphics, SUN, sun.x, sun.y, sunshine);
        glow(graphics, FIRST_LIGHT, sun.x, sun.y, 0.48F * sunshine * (1 - coverage));
        glow(graphics, HALO, 0, 0, opening * crown * turn);
        if (flat > 0) {
            logoHalo(graphics, lightTime, flat);
            glow(graphics, CORONA, 0, 0, flat);
        }

        TitleMenu.tint(graphics, 1);
        Matrix4f matrix = graphics.pose().last().pose();
        drawRim(matrix, points, sun, opening * (0.20F + 0.80F * crown) * (1 - flat));
        drawTerrain(matrix, points, sun, shadow, opening, arrival);
        if (flat > 0) logoGlare(graphics, lightTime, flat);
    }

    static float yaw(float age, float motion) {
        return Mth.lerp(TitleMenu.ease(age, -0.6F, DOCK_END), Mth.lerp(motion, 1.02F, 0.08F), Mth.HALF_PI);
    }

    private static void logoHalo(GuiGraphics graphics, float time, float strength) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation(0.16F * Mth.sin(time * 0.34F)));
        pose.scale(1.35F, 1.35F, 1);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        glow(graphics, HALO, 0, 0, (0.59F + 0.14F * Mth.cos(time * 0.45F)) * strength);
        RenderSystem.defaultBlendFunc();
        pose.popPose();
    }

    private static void logoGlare(GuiGraphics graphics, float time, float strength) {
        float x = 29 + 2.8F * Mth.sin(time * 0.52F);
        float y = -34;
        float pulse = 0.80F + 0.17F * Mth.cos(time * 0.89F) + 0.03F * Mth.sin(time * 1.41F);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(2.45F + 0.28F * Mth.sin(time * 0.58F), 1.65F, 1);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        glow(graphics, FIRST_LIGHT, 0, 0, (0.22F + 0.36F * pulse) * strength);
        RenderSystem.defaultBlendFunc();
        pose.popPose();
        glow(graphics, FIRST_LIGHT, x, y, pulse * strength);
    }

    private static Point[] project(float yaw, float pitch, float roll) {
        float yawCos = Mth.cos(yaw), yawSin = Mth.sin(yaw);
        float pitchCos = Mth.cos(pitch), pitchSin = Mth.sin(pitch);
        float rollCos = Mth.cos(roll), rollSin = Mth.sin(roll);
        Point[] result = new Point[CORNERS.length];
        for (int i = 0; i < CORNERS.length; i++) {
            int[] corner = CORNERS[i];
            float x = HALF * (corner[0] * yawCos + corner[2] * yawSin);
            float afterYaw = HALF * (corner[2] * yawCos - corner[0] * yawSin);
            float y = HALF * corner[1] * pitchCos - afterYaw * pitchSin;
            float z = HALF * corner[1] * pitchSin + afterYaw * pitchCos;
            result[i] = new Point(x * rollCos - y * rollSin, x * rollSin + y * rollCos, z);
        }
        return result;
    }

    /** Follow a ray through the upper-right rim while the square sun slips further behind it. */
    private static Point sunBehind(Point[] points, float rise, float inset) {
        float distance = Float.POSITIVE_INFINITY;
        for (int i = 0; i < SILHOUETTE.length; i++) {
            Point first = points[SILHOUETTE[i]];
            Point next = points[SILHOUETTE[(i + 1) % SILHOUETTE.length]];
            float normalX = first.y - next.y, normalY = next.x - first.x;
            float alongRay = normalX + normalY * rise;
            if (alongRay > 0.001F) {
                float cornerExtent = Math.abs(normalX) + Math.abs(normalY);
                float limit = normalX * first.x + normalY * first.y - cornerExtent * inset;
                distance = Math.min(distance, limit / alongRay);
            }
        }
        return new Point(distance, distance * rise, 42);
    }

    private static void drawRim(Matrix4f matrix, Point[] points, Point sun, float strength) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < SILHOUETTE.length; i++) {
            Point first = points[SILHOUETTE[i]];
            Point next = points[SILHOUETTE[(i + 1) % SILHOUETTE.length]];
            rimVertex(vertices, matrix, first, sun, 1.19F, 0);
            rimVertex(vertices, matrix, next, sun, 1.19F, 0);
            rimVertex(vertices, matrix, next, sun, 1.04F, strength * 0.30F);
            rimVertex(vertices, matrix, first, sun, 1.04F, strength * 0.30F);
            rimVertex(vertices, matrix, first, sun, 1.04F, strength * 0.30F);
            rimVertex(vertices, matrix, next, sun, 1.04F, strength * 0.30F);
            rimVertex(vertices, matrix, next, sun, 0.995F, strength);
            rimVertex(vertices, matrix, first, sun, 0.995F, strength);
        }
        BufferUploader.drawWithShader(vertices.buildOrThrow());
    }

    private static void rimVertex(BufferBuilder vertices, Matrix4f matrix, Point point, Point sun, float radius, float alpha) {
        float dx = point.x - sun.x;
        float dy = point.y - sun.y;
        float heat = 1 / (1 + (dx * dx + dy * dy) / 850);
        vertices.addVertex(matrix, point.x * radius, point.y * radius, 0)
                .setColor(0.43F + 0.56F * heat, 0.69F + 0.24F * heat, 0.81F, alpha * (0.55F + 0.45F * heat));
    }

    private static void drawTerrain(Matrix4f matrix, Point[] points, Point sun, float shadow, float opening, float arrival) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SURFACE);
        float frame = shadow * (SHADOW_STEPS - 1);
        int index = (int) frame;
        float length = Mth.sqrt(sun.dot(sun));
        Point lightDirection = new Point(sun.x / length, sun.y / length, sun.z / length);
        // Fade the light on the opaque cube so the sun and stars cannot shine through it.
        terrainFrame(matrix, points, lightDirection, index, 1, opening, arrival);
        if (frame > index) terrainFrame(matrix, points, lightDirection, index + 1, frame - index, opening, arrival);
    }

    private static void terrainFrame(Matrix4f matrix, Point[] points, Point lightDirection, int frame,
                                     float opacity, float opening, float arrival) {
        BufferBuilder vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int face = 0; face < FACES.length; face++) {
            int[] corners = FACES[face];
            // Opposite corners average to the face center, one half-width along its normal.
            float facing = (points[corners[0]].dot(lightDirection) + points[corners[2]].dot(lightDirection)) / (2 * HALF);
            float direct = TitleMenu.ease(facing, -0.38F, 0.82F);
            for (int corner = 0; corner < 4; corner++) {
                Point point = points[corners[corner]];
                float u = (frame + (corner >= 2 ? 1 - TEXEL_INSET : TEXEL_INSET)) / SHADOW_STEPS;
                float v = (face + (corner == 1 || corner == 2 ? 1 - TEXEL_INSET : TEXEL_INSET)) / FACES.length;
                float sunward = TitleMenu.ease(point.dot(lightDirection) / HALF, -0.65F, 0.72F);
                float light = Math.min(1, 0.12F + 0.28F * sunward + (0.42F + 0.27F * sunward) * direct);
                float warmth = direct * (0.55F + 0.45F * sunward);
                // The lit rim is warm, while the dark side picks up a fraction of the blue sky.
                float red = opening * Mth.lerp(arrival, light * Mth.lerp(warmth, 0.72F, 1), 1);
                float green = opening * Mth.lerp(arrival, light * Mth.lerp(warmth, 0.87F, 0.94F), 1);
                float blue = opening * Mth.lerp(arrival, light * Mth.lerp(warmth, 1, 0.78F), 1);
                vertices.addVertex(matrix, point.x, point.y, 0).setUv(u, v).setColor(red, green, blue, opacity);
            }
        }
        BufferUploader.drawWithShader(vertices.buildOrThrow());
    }

    private static void glow(GuiGraphics graphics, ResourceLocation texture, float x, float y, float alpha) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        TitleMenu.tint(graphics, alpha);
        graphics.blit(texture, -112, -112, 224, 224, 0, 0, 512, 512, 512, 512);
        pose.popPose();
    }
}
