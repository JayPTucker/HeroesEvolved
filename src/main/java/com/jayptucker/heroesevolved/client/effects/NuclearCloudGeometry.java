package com.jayptucker.heroesevolved.client.effects;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Shared cinematic mesh: incandescent core, rising stem and rolling mushroom-cap lobes. */
public final class NuclearCloudGeometry {
    private NuclearCloudGeometry() {}
    public static void cloud(VertexConsumer out, Matrix4f matrix, float seconds, float opacity) {
        float growth = Mth.clamp(seconds / 2.8F, 0, 1);
        // An incandescent core, a rising stem, and rolling lobes form a readable mushroom cloud.
        sphere(out, matrix, 0, 10 + growth * 13, 0, 3 + growth * 22,
                3 + growth * 21, 3 + growth * 22, 1, 0.64F, 0.16F, 0.98F * opacity, 0);
        for (int i = 0; i < 10; i++) {
            float rise = Math.min(1, seconds / (0.35F + i * 0.15F));
            float y = 5 + i * 5.6F * rise;
            float radius = (7 + 2 * (float) Math.sin(i * 1.9)) * growth + 1;
            float heat = Math.max(0, 1 - seconds / 9 - i * 0.04F);
            sphere(out, matrix, (float) Math.sin(i * 2.4) * 2, y, 0,
                    radius, 7 * growth + 1, radius, 0.28F + heat * 0.55F,
                    0.23F + heat * 0.19F, 0.20F, 0.98F * opacity, i);
        }
        float capY = 32 + Math.min(seconds, 6.5F) * 5;
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 14;
            float ring = i < 14 ? 22 : 11;
            float rolling = (float) Math.sin(seconds * 1.4 + i * 2.3);
            float heat = Mth.clamp(1 - seconds * 0.09F - (i % 4) * 0.12F, 0, 1);
            sphere(out, matrix, (float) Math.cos(angle) * ring * growth,
                    capY + (i < 14 ? rolling * 3 : 9), (float) Math.sin(angle) * ring * growth,
                    (12 + rolling * 2) * growth + 0.3F, (9 + rolling) * growth + 0.3F,
                    (12 + rolling * 2) * growth + 0.3F,
                    0.25F + heat * 0.65F, 0.21F + heat * 0.29F, 0.18F + heat * 0.04F, 0.98F * opacity, i);
        }
    }
    public static void dust(VertexConsumer out, Matrix4f matrix, float seconds, float opacity) {
        shockwave(out, matrix, 8 + seconds * 18, opacity);
    }
    public static void shockwave(VertexConsumer out, Matrix4f matrix, float radius, float opacity) {
        ring(out, matrix, radius, radius + 4, 0.4F, 0.85F, 0.76F, 0.61F, 0.68F * opacity);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            sphere(out, matrix, (float) Math.cos(a) * radius, 4 + (float) Math.sin(i * 2.7) * 2,
                    (float) Math.sin(a) * radius, 5, 6, 5, 0.64F, 0.56F, 0.47F, 0.48F * opacity, i);
        }
    }

    private static void sphere(VertexConsumer b, Matrix4f m, float x, float y, float z,
                               float rx, float ry, float rz, float r, float g, float blue, float alpha, int seed) {
        for (int row = 0; row < 8; row++) {
            double p0 = -Math.PI / 2 + row * Math.PI / 8, p1 = p0 + Math.PI / 8;
            for (int col = 0; col < 12; col++) {
                double a0 = col * Math.PI * 2 / 12, a1 = a0 + Math.PI * 2 / 12;
                float shade = 0.65F + 0.35F * (float) (Math.sin(p1) * 0.5 + 0.5);
                point(b, m, x, y, z, rx, ry, rz, p0, a0, r * shade, g * shade, blue * shade, alpha, seed);
                point(b, m, x, y, z, rx, ry, rz, p0, a1, r * shade, g * shade, blue * shade, alpha, seed);
                point(b, m, x, y, z, rx, ry, rz, p1, a1, r * shade, g * shade, blue * shade, alpha, seed);
                point(b, m, x, y, z, rx, ry, rz, p1, a0, r * shade, g * shade, blue * shade, alpha, seed);
            }
        }
    }

    private static void point(VertexConsumer b, Matrix4f m, float x, float y, float z,
                              float rx, float ry, float rz, double p, double a,
                              float r, float g, float blue, float alpha, int seed) {
        float bump = 1 + 0.07F * (float) (Math.sin(a * 5 + seed) * Math.cos(p * 4 + seed));
        b.addVertex(m, x + (float) (Math.cos(a) * Math.cos(p)) * rx * bump,
                y + (float) Math.sin(p) * ry * bump,
                z + (float) (Math.sin(a) * Math.cos(p)) * rz * bump).setColor(r, g, blue, alpha);
    }

    private static void ring(VertexConsumer b, Matrix4f m, float inner, float outer, float y,
                             float r, float g, float blue, float alpha) {
        for (int i = 0; i < 128; i++) {
            double a = i * Math.PI / 64, next = (i + 1) * Math.PI / 64;
            b.addVertex(m, (float) Math.cos(a) * inner, y, (float) Math.sin(a) * inner).setColor(r, g, blue, alpha);
            b.addVertex(m, (float) Math.cos(a) * outer, y, (float) Math.sin(a) * outer).setColor(r, g, blue, alpha);
            b.addVertex(m, (float) Math.cos(next) * outer, y, (float) Math.sin(next) * outer).setColor(r, g, blue, alpha);
            b.addVertex(m, (float) Math.cos(next) * inner, y, (float) Math.sin(next) * inner).setColor(r, g, blue, alpha);
        }
    }

}
