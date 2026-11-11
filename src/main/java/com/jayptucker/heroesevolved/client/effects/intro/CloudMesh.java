package com.jayptucker.heroesevolved.client.effects.intro;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Billows: icospheres whose surfaces are pushed in and out by noise baked once per variant, with
 * normals that follow the bumps, so a puff of smoke is lit like a lumpy three-dimensional mass and
 * not a flat sprite or a polished ball. Three levels of detail; the caller picks by size on screen.
 */
public final class CloudMesh {
    /** A sphere: unit vertex directions, triangles, and per variant a bump and a normal for each vertex. */
    public record Sphere(float[] v, int[] tris, float[][] bumps, float[][] normals) {}

    public static final int VARIANTS = 12;
    public static final Sphere SUPER = build(4), ULTRA = build(3), FINE = build(2), COARSE = build(1);

    /** Shade for one vertex: n is the outward unit normal, bump the displacement, world the vertex position. */
    public interface Shade { void color(float nx, float ny, float nz, float bump, float wx, float wy, float wz, float[] rgb); }

    /** Scratch space for one billow at a time; drawing happens on the render thread only. */
    private static final int MOST = 2562;
    private static final float[] PX = new float[MOST], PY = new float[MOST], PZ = new float[MOST],
            R = new float[MOST], G = new float[MOST], B = new float[MOST], RGB = new float[3];

    private CloudMesh() {}

    /** The level of detail for a billow of {@code radius} seen from {@code distance}. */
    public static Sphere detail(float radius, double distance) {
        double size = radius / Math.max(distance, 1);
        return size > 0.5 ? SUPER : size > 0.22 ? ULTRA : size > 0.06 ? FINE : COARSE;
    }

    private static Sphere build(int subdivisions) {
        float t = (1 + Mth.sqrt(5)) / 2;
        List<float[]> verts = new ArrayList<>();
        float[][] base = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        for (float[] b : base) verts.add(normalize(b));
        List<int[]> faces = new ArrayList<>(List.of(new int[][] {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}}));
        for (int s = 0; s < subdivisions; s++) {
            Map<Long, Integer> mids = new HashMap<>();
            List<int[]> next = new ArrayList<>();
            for (int[] f : faces) {
                int a = mid(verts, mids, f[0], f[1]), b = mid(verts, mids, f[1], f[2]), c = mid(verts, mids, f[2], f[0]);
                next.add(new int[] {f[0], a, c});
                next.add(new int[] {f[1], b, a});
                next.add(new int[] {f[2], c, b});
                next.add(new int[] {a, b, c});
            }
            faces = next;
        }
        int n = verts.size();
        float[] v = new float[n * 3];
        for (int i = 0; i < n; i++) System.arraycopy(verts.get(i), 0, v, i * 3, 3);
        int[] tris = new int[faces.size() * 3];
        for (int i = 0; i < faces.size(); i++) System.arraycopy(faces.get(i), 0, tris, i * 3, 3);
        float[][] bumps = new float[VARIANTS][n], normals = new float[VARIANTS][n * 3];
        for (int k = 0; k < VARIANTS; k++) {
            ImprovedNoise noise = new ImprovedNoise(RandomSource.create(9173L + k * 31L));
            for (int i = 0; i < n; i++) {
                float x = v[i * 3], y = v[i * 3 + 1], z = v[i * 3 + 2];
                // Rounded lumps, not ridges: two smooth octaves, kept small against the radius.
                double b = noise.noise(x * 1.5, y * 1.5, z * 1.5) * 0.34 + noise.noise(x * 3.1 + 5, y * 3.1, z * 3.1) * 0.13;
                bumps[k][i] = (float) Mth.clamp(b, -0.3, 0.3);
            }
            // Normals of the bumped surface, so lumps catch the light on one side and shade on the other.
            float[] p = new float[n * 3], nm = normals[k];
            for (int i = 0; i < n; i++) for (int a = 0; a < 3; a++) p[i * 3 + a] = v[i * 3 + a] * (1 + bumps[k][i]);
            for (int f = 0; f < tris.length; f += 3) {
                int a = tris[f], b = tris[f + 1], c = tris[f + 2];
                float ux = p[b * 3] - p[a * 3], uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
                float wx = p[c * 3] - p[a * 3], wy = p[c * 3 + 1] - p[a * 3 + 1], wz = p[c * 3 + 2] - p[a * 3 + 2];
                float cx = uy * wz - uz * wy, cy = uz * wx - ux * wz, cz = ux * wy - uy * wx;
                for (int j : new int[] {a, b, c}) { nm[j * 3] += cx; nm[j * 3 + 1] += cy; nm[j * 3 + 2] += cz; }
            }
            for (int i = 0; i < n; i++) {
                float nx = nm[i * 3], ny = nm[i * 3 + 1], nz = nm[i * 3 + 2];
                float l = Mth.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6F;
                // Point outward whichever way the faces happen to wind.
                if (nx * v[i * 3] + ny * v[i * 3 + 1] + nz * v[i * 3 + 2] < 0) l = -l;
                nm[i * 3] = nx / l; nm[i * 3 + 1] = ny / l; nm[i * 3 + 2] = nz / l;
            }
        }
        return new Sphere(v, tris, bumps, normals);
    }

    private static int mid(List<float[]> verts, Map<Long, Integer> mids, int a, int b) {
        long key = Math.min(a, b) * 100000L + Math.max(a, b);
        Integer found = mids.get(key);
        if (found != null) return found;
        float[] p = verts.get(a), q = verts.get(b);
        verts.add(normalize(new float[] {(p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2}));
        mids.put(key, verts.size() - 1);
        return verts.size() - 1;
    }

    private static float[] normalize(float[] p) {
        float l = Mth.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return new float[] {p[0] / l, p[1] / l, p[2] / l};
    }

    /**
     * One billow. Centre and radii are relative to the camera; {@code wx, wy, wz} is the centre in the
     * world, for the shader. {@code spin} turns the lumps about the vertical so a billow seems to roll.
     */
    public static void puff(VertexConsumer out, Matrix4f matrix, Sphere sphere, float cx, float cy, float cz, float rx, float ry, float rz,
                     int variant, float spin, float bumpScale, float alpha, float wx, float wy, float wz, Shade shade) {
        float[] v = sphere.v;
        int k = Math.floorMod(variant, VARIANTS);
        float[] bump = sphere.bumps[k], normal = sphere.normals[k];
        int n = v.length / 3;
        float cos = Mth.cos(spin), sin = Mth.sin(spin);
        float bend = Math.min(bumpScale, 1);
        float[] px = PX, py = PY, pz = PZ, r = R, g = G, b = B, rgb = RGB;
        for (int i = 0; i < n; i++) {
            float x = v[i * 3], y = v[i * 3 + 1], z = v[i * 3 + 2];
            float sx = x * cos - z * sin, sz = x * sin + z * cos;
            float d = 1 + bump[i] * bumpScale;
            float ox = sx * rx * d, oy = y * ry * d, oz = sz * rz * d;
            px[i] = cx + ox; py[i] = cy + oy; pz[i] = cz + oz;
            // The bumped normal, turned with the lumps and squashed with the billow.
            float nx0 = Mth.lerp(bend, x, normal[i * 3]), ny0 = Mth.lerp(bend, y, normal[i * 3 + 1]), nz0 = Mth.lerp(bend, z, normal[i * 3 + 2]);
            float nx = (nx0 * cos - nz0 * sin) / rx, ny = ny0 / ry, nz = (nx0 * sin + nz0 * cos) / rz;
            float l = Mth.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6F;
            shade.color(nx / l, ny / l, nz / l, bump[i] * bumpScale, wx + ox, wy + oy, wz + oz, rgb);
            r[i] = rgb[0]; g[i] = rgb[1]; b[i] = rgb[2];
        }
        int[] tris = sphere.tris;
        for (int t = 0; t < tris.length; t++) {
            int i = tris[t];
            out.addVertex(matrix, px[i], py[i], pz[i]).setColor(r[i], g[i], b[i], alpha);
        }
    }
}
