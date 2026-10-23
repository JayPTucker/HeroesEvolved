package com.jayptucker.heroesevolved.client.effects;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** A translucent hemisphere: strong frosted rim, softer crown, and drifting bands of cold. */
public final class ColdDomeGeometry {
    private ColdDomeGeometry() {}
    public static void draw(VertexConsumer out, Matrix4f matrix, float radius, float time, float opacity, boolean detail) {
        int rings = detail ? 8 : 6, sides = detail ? 32 : 16;
        for (int ring = 0; ring < rings; ring++) for (int side = 0; side < sides; side++) {
            double low = ring * Math.PI / (2 * rings), high = (ring + 1) * Math.PI / (2 * rings);
            double a = side * Math.PI * 2 / sides, b = (side + 1) * Math.PI * 2 / sides;
            vertex(out, matrix, radius, low, a, time, opacity);
            vertex(out, matrix, radius, low, b, time, opacity);
            vertex(out, matrix, radius, high, b, time, opacity);
            vertex(out, matrix, radius, high, a, time, opacity);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, float radius, double elevation, double angle,
                               float time, float opacity) {
        double horizontal = Math.cos(elevation);
        float wave = (float) Math.pow(.5 + .5 * Math.sin(angle * 7 + elevation * 12 - time * .35), 6);
        float rim = (float) Math.pow(horizontal, 10);
        float alpha = opacity * (.018F + rim * .065F + wave * .04F);
        out.addVertex(matrix, (float) (Math.cos(angle) * horizontal * radius),
                (float) (Math.sin(elevation) * radius) + .08F,
                (float) (Math.sin(angle) * horizontal * radius)).setColor(.64F, .85F, 1, alpha);
    }
}
