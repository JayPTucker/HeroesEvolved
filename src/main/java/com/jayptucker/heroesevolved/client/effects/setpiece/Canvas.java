package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.intro.CloudMesh;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Where set pieces draw: soft-edged ribbons, rings, discs, glows, shells, columns, shards, rocks, lightning
 * and billowing cloud, all as triangles relative to the camera. Each frame is drawn in three passes:
 * {@link Pass#SOLID} (lit, opaque, writes depth), {@link Pass#SOFT} (ordinary transparency) and
 * {@link Pass#GLOW} (additive light). A piece draws each part in the pass it belongs to.
 */
public final class Canvas {
    public enum Pass { SOLID, SOFT, GLOW }

    Pass pass;
    BufferBuilder out;
    final Matrix4f m = new Matrix4f();
    double cx, cy, cz;
    /** 1 at noon, about 0.2 at midnight: how much the sun lights solid things. */
    float daylight = 1;
    float partial;
    ClientLevel level;

    public boolean solid() { return pass == Pass.SOLID; }
    public boolean soft() { return pass == Pass.SOFT; }
    public boolean glow() { return pass == Pass.GLOW; }
    public Vec3 camera() { return new Vec3(cx, cy, cz); }
    /** How far into the current tick this frame is. */
    public float partial() { return partial; }

    // ------------------------------------------------------------------
    // Colour
    // ------------------------------------------------------------------

    public static float[] rgb(int color) {
        return new float[] {(color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F};
    }

    public static float[] mix(float[] a, float[] b, float k) {
        return new float[] {Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])};
    }

    /** A rainbow hue, 0 to 1 round the wheel. */
    public static float[] hue(float h) {
        h = h - (float) Math.floor(h);
        float r = Mth.clamp(Math.abs(h * 6 - 3) - 1, 0, 1), g = Mth.clamp(2 - Math.abs(h * 6 - 2), 0, 1), b = Mth.clamp(2 - Math.abs(h * 6 - 4), 0, 1);
        return new float[] {r, g, b};
    }

    private static final float LX = 0.36F, LY = 0.84F, LZ = 0.41F;

    /** {@code base} lit from above by the sun and the sky, for faces with this normal. */
    public float[] lit(float nx, float ny, float nz, float[] base, float[] into) {
        float sun = Math.max(0, nx * LX + ny * LY + nz * LZ);
        float k = 0.22F + 0.28F * daylight + 0.55F * daylight * sun + 0.08F * Math.max(0, -ny);
        into[0] = Mth.clamp(base[0] * k, 0, 1); into[1] = Mth.clamp(base[1] * k, 0, 1); into[2] = Mth.clamp(base[2] * k, 0, 1);
        return into;
    }

    // ------------------------------------------------------------------
    // Vertices
    // ------------------------------------------------------------------

    void v(double x, double y, double z, float r, float g, float b, float a) {
        out.addVertex(m, (float) (x - cx), (float) (y - cy), (float) (z - cz)).setColor(r, g, b, Math.min(1, Math.max(0, a)));
    }

    void v(double x, double y, double z, float[] c, float a) { v(x, y, z, c[0], c[1], c[2], a); }

    public void tri(Vec3 a, Vec3 b, Vec3 c, float[] col, float aa, float ab, float ac) {
        v(a.x, a.y, a.z, col, aa); v(b.x, b.y, b.z, col, ab); v(c.x, c.y, c.z, col, ac);
    }

    private void quad(double[] p0, double[] p1, double[] p2, double[] p3, float[] c, float a0, float a1, float a2, float a3) {
        v(p0[0], p0[1], p0[2], c, a0); v(p1[0], p1[1], p1[2], c, a1); v(p2[0], p2[1], p2[2], c, a2);
        v(p0[0], p0[1], p0[2], c, a0); v(p2[0], p2[1], p2[2], c, a2); v(p3[0], p3[1], p3[2], c, a3);
    }

    // ------------------------------------------------------------------
    // Light
    // ------------------------------------------------------------------

    /** A disc of light facing the camera, bright in the middle and fading to nothing at its rim. */
    public void glow(Vec3 at, float radius, float[] c, float alpha) {
        if (alpha <= 0.003F || radius <= 0) return;
        float dx = (float) (cx - at.x), dy = (float) (cy - at.y), dz = (float) (cz - at.z), l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4F;
        dx /= l; dy /= l; dz /= l;
        float ux = 0, uy = 1, uz = 0;
        if (Math.abs(dy) > 0.95F) { ux = 1; uy = 0; }
        float ax = uy * dz - uz * dy, ay = uz * dx - ux * dz, az = ux * dy - uy * dx, al = Mth.sqrt(ax * ax + ay * ay + az * az);
        ax /= al; ay /= al; az /= al;
        float bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax;
        int sides = radius > 3 ? 24 : 12;
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides, a1 = (i + 1) * Mth.TWO_PI / sides;
            v(at.x, at.y, at.z, c, alpha);
            v(at.x + (ax * Mth.cos(a0) + bx * Mth.sin(a0)) * radius, at.y + (ay * Mth.cos(a0) + by * Mth.sin(a0)) * radius,
                    at.z + (az * Mth.cos(a0) + bz * Mth.sin(a0)) * radius, c, 0);
            v(at.x + (ax * Mth.cos(a1) + bx * Mth.sin(a1)) * radius, at.y + (ay * Mth.cos(a1) + by * Mth.sin(a1)) * radius,
                    at.z + (az * Mth.cos(a1) + bz * Mth.sin(a1)) * radius, c, 0);
        }
    }

    // ------------------------------------------------------------------
    // Ribbons
    // ------------------------------------------------------------------

    /**
     * A soft band along a line through these world points, turned to face the camera: bright along the line,
     * fading to nothing at its edges. {@code alpha} and {@code width} are per point.
     */
    public void ribbon(double[] px, double[] py, double[] pz, float[] alpha, float[] width, float[] c) {
        int n = px.length;
        if (n < 2) return;
        double[][] left = new double[n][], mid = new double[n][], right = new double[n][];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double tx = px[b] - px[a], ty = py[b] - py[a], tz = pz[b] - pz[a];
            double vx = px[i] - cx, vy = py[i] - cy, vz = pz[i] - cz;
            double sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
            double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (l < 1e-6) { sx = 1; sy = 0; sz = 0; l = 1; }
            double w = width[i] / l;
            mid[i] = new double[] {px[i], py[i], pz[i]};
            left[i] = new double[] {px[i] - sx * w, py[i] - sy * w, pz[i] - sz * w};
            right[i] = new double[] {px[i] + sx * w, py[i] + sy * w, pz[i] + sz * w};
        }
        for (int i = 0; i + 1 < n; i++) {
            if (alpha[i] <= 0.003F && alpha[i + 1] <= 0.003F) continue;
            quad(left[i], mid[i], mid[i + 1], left[i + 1], c, 0, alpha[i], alpha[i + 1], 0);
            quad(mid[i], right[i], right[i + 1], mid[i + 1], c, alpha[i], 0, 0, alpha[i + 1]);
        }
    }

    /** A ribbon of even width through points, its alpha from {@code from} at the start to {@code to} at the end. */
    public void ribbon(Vec3[] points, float width, float[] c, float from, float to) {
        int n = points.length;
        float[] a = new float[n], w = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = Mth.lerp(n == 1 ? 0 : i / (float) (n - 1), from, to);
            w[i] = width;
        }
        ribbon(points, w, c, a);
    }

    public void ribbon(Vec3[] points, float[] width, float[] c, float[] alpha) {
        int n = points.length;
        double[] x = new double[n], y = new double[n], z = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = points[i].x; y[i] = points[i].y; z[i] = points[i].z;
        }
        ribbon(x, y, z, alpha, width, c);
    }

    public void line(Vec3 a, Vec3 b, float width, float[] c, float alphaA, float alphaB) {
        ribbon(new Vec3[] {a, b}, width, c, alphaA, alphaB);
    }

    /** A jagged bolt from {@code a} to {@code b}; the same seed gives the same bolt. */
    public void bolt(Vec3 a, Vec3 b, int seed, float jitter, float width, float[] c, float alpha) {
        int segments = Math.max(4, (int) (a.distanceTo(b) * 2));
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(d).normalize();
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float k = i / (float) segments, taper = Mth.sin(k * Mth.PI);
            float ox = (hash(seed, i * 2) - 0.5F) * 2 * jitter * taper, oy = (hash(seed, i * 2 + 1) - 0.5F) * 2 * jitter * taper;
            points[i] = a.add(d.scale(k)).add(side.scale(ox)).add(up.scale(oy));
        }
        ribbon(points, width * 3, c, alpha * 0.35F, alpha * 0.35F);
        ribbon(points, width, new float[] {Math.min(1, c[0] + 0.5F), Math.min(1, c[1] + 0.5F), Math.min(1, c[2] + 0.5F)}, alpha, alpha);
    }

    /** A spiral of {@code turns} round an upright axis from {@code base}, between two radii, as a ribbon. */
    public void helix(Vec3 base, float height, float r0, float r1, float turns, float phase, float width, float[] c, float alpha, int steps) {
        double[] x = new double[steps + 1], y = new double[steps + 1], z = new double[steps + 1];
        float[] a = new float[steps + 1], w = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float k = i / (float) steps, ang = phase + k * turns * Mth.TWO_PI, r = Mth.lerp(k, r0, r1);
            x[i] = base.x + Mth.cos(ang) * r; y[i] = base.y + k * height; z[i] = base.z + Mth.sin(ang) * r;
            a[i] = alpha * Mth.sin(k * Mth.PI); w[i] = width;
        }
        ribbon(x, y, z, a, w, c);
    }

    // ------------------------------------------------------------------
    // Flat shapes
    // ------------------------------------------------------------------

    /** Two directions across the plane whose normal is {@code axis}. */
    private static Vec3[] basis(Vec3 axis) {
        Vec3 n = axis.normalize();
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0).cross(n) : new Vec3(0, 1, 0).cross(n);
        u = u.normalize();
        return new Vec3[] {u, n.cross(u).normalize()};
    }

    public static final Vec3 UP = new Vec3(0, 1, 0);

    /** A soft band round a circle in the plane across {@code axis}, from one angle to another. */
    public void ring(Vec3 at, Vec3 axis, float radius, float width, float from, float to, float[] c, float alpha) {
        if (alpha <= 0.003F || radius <= 0) return;
        Vec3[] b = basis(axis);
        int n = Math.max(6, (int) Math.ceil(Math.abs(to - from) / Mth.TWO_PI * Math.min(96, 24 + radius * 6)));
        float in = Math.max(0, radius - width), outR = radius + width;
        for (int i = 0; i < n; i++) {
            float a0 = from + (to - from) * i / n, a1 = from + (to - from) * (i + 1) / n;
            double[] i0 = on(at, b, a0, in), m0 = on(at, b, a0, radius), o0 = on(at, b, a0, outR);
            double[] i1 = on(at, b, a1, in), m1 = on(at, b, a1, radius), o1 = on(at, b, a1, outR);
            quad(i0, m0, m1, i1, c, 0, alpha, alpha, 0);
            quad(m0, o0, o1, m1, c, alpha, 0, 0, alpha);
        }
    }

    public void ring(Vec3 at, float radius, float width, float[] c, float alpha) { ring(at, UP, radius, width, 0, Mth.TWO_PI, c, alpha); }

    private static double[] on(Vec3 at, Vec3[] b, float angle, float r) {
        double cs = Mth.cos(angle) * r, sn = Mth.sin(angle) * r;
        return new double[] {at.x + b[0].x * cs + b[1].x * sn, at.y + b[0].y * cs + b[1].y * sn, at.z + b[0].z * cs + b[1].z * sn};
    }

    /** A filled disc across {@code axis}: {@code centre} alpha in the middle to {@code edge} at the rim. */
    public void disc(Vec3 at, Vec3 axis, float radius, float[] c, float centre, float edge) {
        if (Math.max(centre, edge) <= 0.003F || radius <= 0) return;
        Vec3[] b = basis(axis);
        int n = 32;
        for (int i = 0; i < n; i++) {
            double[] p0 = on(at, b, i * Mth.TWO_PI / n, radius), p1 = on(at, b, (i + 1) * Mth.TWO_PI / n, radius);
            v(at.x, at.y, at.z, c, centre); v(p0[0], p0[1], p0[2], c, edge); v(p1[0], p1[1], p1[2], c, edge);
        }
    }

    /** A soft radial bar on a plane: along {@code angle} from radius {@code r0} to {@code r1}. */
    public void spoke(Vec3 at, Vec3 axis, float angle, float r0, float r1, float width, float[] c, float alpha) {
        Vec3[] b = basis(axis);
        double[] p0 = on(at, b, angle, r0), p1 = on(at, b, angle, r1);
        line(new Vec3(p0[0], p0[1], p0[2]), new Vec3(p1[0], p1[1], p1[2]), width, c, alpha, alpha);
    }

    // ------------------------------------------------------------------
    // Shells and columns
    // ------------------------------------------------------------------

    /** Brightness over a shell, by its angles round (theta) and up (phi, 0 at the top). */
    public interface Pattern { float at(float theta, float phi); }
    public static final Pattern PLAIN = (theta, phi) -> 1;

    /** A hexagonal lattice over a shell: 1 on its seams, low inside the cells. */
    public static Pattern hexes(float cells, float seam, float inside) {
        return (theta, phi) -> {
            float u = theta * cells / Mth.TWO_PI * 2, w = phi * cells / Mth.PI * 1.15F;
            float qx = u - w * 0.5F, qy = w;
            float d = Math.min(edge(qx), Math.min(edge(qy), edge(qx + qy)));
            return d < seam ? 1 : inside;
        };
    }

    private static float edge(float v) { float f = v - (float) Math.floor(v); return Math.min(f, 1 - f); }

    /**
     * A see-through shell: brightest at its rim where you see it edge-on, faint where you look straight
     * through it. {@code top} to {@code bottom} are its angles from the top (PI for the full sphere, PI/2 for a dome).
     */
    public void shell(Vec3 at, float rx, float ry, float rz, float bottom, float[] c, float alpha, float face, Pattern pattern, float spin) {
        if (alpha <= 0.003F || rx <= 0) return;
        int rows = 16, cols = 36;
        double[][] p = new double[(rows + 1) * (cols + 1)][];
        float[] a = new float[p.length];
        for (int i = 0; i <= rows; i++) {
            float phi = bottom * i / rows;
            for (int j = 0; j <= cols; j++) {
                float theta = j * Mth.TWO_PI / cols + spin;
                float nx = Mth.sin(phi) * Mth.cos(theta), ny = Mth.cos(phi), nz = Mth.sin(phi) * Mth.sin(theta);
                double x = at.x + nx * rx, y = at.y + ny * ry, z = at.z + nz * rz;
                double vx = cx - x, vy = cy - y, vz = cz - z, l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;
                float facing = (float) Math.abs((nx * vx + ny * vy + nz * vz) / l);
                float rim = Mth.lerp(face, (1 - facing) * (1 - facing), 1);
                int k = i * (cols + 1) + j;
                p[k] = new double[] {x, y, z};
                a[k] = alpha * Math.max(rim, 0.08F) * pattern.at(theta, phi);
            }
        }
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                int k0 = i * (cols + 1) + j, k1 = k0 + 1, k2 = k0 + cols + 2, k3 = k0 + cols + 1;
                quad(p[k0], p[k1], p[k2], p[k3], c, a[k0], a[k1], a[k2], a[k3]);
            }
    }

    public void sphere(Vec3 at, float radius, float[] c, float alpha) { shell(at, radius, radius, radius, Mth.PI, c, alpha, 0.1F, PLAIN, 0); }

    /** An open upright tube from {@code base}: alpha {@code low} at the foot to {@code high} at the top, twisted by {@code twist}. */
    public void tube(Vec3 base, float radius, float height, float[] c, float low, float high, Pattern pattern, float spin) {
        if (Math.max(low, high) <= 0.003F || radius <= 0) return;
        int rows = 8, cols = 32;
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                float t0 = j * Mth.TWO_PI / cols + spin, t1 = (j + 1) * Mth.TWO_PI / cols + spin;
                float h0 = i / (float) rows, h1 = (i + 1) / (float) rows;
                double[] p0 = {base.x + Mth.cos(t0) * radius, base.y + h0 * height, base.z + Mth.sin(t0) * radius};
                double[] p1 = {base.x + Mth.cos(t1) * radius, base.y + h0 * height, base.z + Mth.sin(t1) * radius};
                double[] p2 = {base.x + Mth.cos(t1) * radius, base.y + h1 * height, base.z + Mth.sin(t1) * radius};
                double[] p3 = {base.x + Mth.cos(t0) * radius, base.y + h1 * height, base.z + Mth.sin(t0) * radius};
                float a0 = Mth.lerp(h0, low, high), a1 = Mth.lerp(h1, low, high);
                quad(p0, p1, p2, p3, c, a0 * pattern.at(t0, h0), a0 * pattern.at(t1, h0), a1 * pattern.at(t1, h1), a1 * pattern.at(t0, h1));
            }
    }

    /** A radius for each height, as a fraction (0 at the foot, 1 at the top). */
    public interface Profile { float at(float u); }

    /**
     * A see-through surface turned round an upright axis from {@code base}, {@code height} tall, its radius
     * from {@code profile}: brightest where you see it edge-on, like {@link #shell}, its alpha shaped by
     * {@code pattern} (theta round, and the height fraction for phi).
     */
    public void revolve(Vec3 base, float height, Profile profile, float[] c, float alpha, Pattern pattern) {
        if (alpha <= 0.003F || height <= 0) return;
        int rows = 22, cols = 40;
        double[][] p = new double[(rows + 1) * (cols + 1)][];
        float[] a = new float[p.length];
        for (int i = 0; i <= rows; i++) {
            float u = i / (float) rows, r = profile.at(u);
            for (int j = 0; j <= cols; j++) {
                float theta = j * Mth.TWO_PI / cols, nx = Mth.cos(theta), nz = Mth.sin(theta);
                double x = base.x + nx * r, y = base.y + u * height, z = base.z + nz * r;
                double vx = cx - x, vy = cy - y, vz = cz - z, l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;
                float facing = (float) Math.abs((nx * vx + nz * vz) / l);
                int k = i * (cols + 1) + j;
                p[k] = new double[] {x, y, z};
                a[k] = alpha * Math.max((1 - facing) * (1 - facing), 0.12F) * pattern.at(theta, u);
            }
        }
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                int k0 = i * (cols + 1) + j, k1 = k0 + 1, k2 = k0 + cols + 2, k3 = k0 + cols + 1;
                quad(p[k0], p[k1], p[k2], p[k3], c, a[k0], a[k1], a[k2], a[k3]);
            }
    }

    // ------------------------------------------------------------------
    // Solid things
    // ------------------------------------------------------------------

    private final float[] shadeOut = new float[3];

    private void litFace(double[] a, double[] b, double[] c, double[] d, float[] base, float alpha) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = d[0] - a[0], wy = d[1] - a[1], wz = d[2] - a[2];
        double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx, l = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
        float[] col = lit((float) (nx / l), (float) (ny / l), (float) (nz / l), base, shadeOut);
        quad(a, b, c, d, col, alpha, alpha, alpha, alpha);
    }

    /**
     * A shard: a {@code sides}-sided crystal from {@code base} to {@code tip}, widest a third of the way up, lit
     * by the sun. Drawn as a solid in the SOLID pass, or with {@code alpha} as glass in the others.
     */
    public void shard(Vec3 base, Vec3 tip, float radius, int sides, float twist, float[] c, float alpha) {
        Vec3 axis = tip.subtract(base);
        if (axis.lengthSqr() < 1e-6 || radius <= 0) return;
        Vec3[] b = basis(axis);
        Vec3 waist = base.add(axis.scale(0.3));
        for (int i = 0; i < sides; i++) {
            float a0 = twist + i * Mth.TWO_PI / sides, a1 = twist + (i + 1) * Mth.TWO_PI / sides;
            double[] w0 = on(waist, b, a0, radius), w1 = on(waist, b, a1, radius);
            double[] t = {tip.x, tip.y, tip.z}, s = {base.x, base.y, base.z};
            litFace(w0, w1, t, t, c, alpha);
            litFace(w1, w0, s, s, c, alpha);
        }
    }

    /** A box turned by {@code yaw} about the vertical and tipped by {@code pitch}: a rock, a slab, a panel. */
    public void box(Vec3 at, float sx, float sy, float sz, float yaw, float pitch, float roll, float[] c, float alpha) {
        float cy0 = Mth.cos(yaw), sy0 = Mth.sin(yaw), cp = Mth.cos(pitch), sp = Mth.sin(pitch), cr = Mth.cos(roll), sr = Mth.sin(roll);
        double[][] corner = new double[8][];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? -sx : sx, y = (i & 2) == 0 ? -sy : sy, z = (i & 4) == 0 ? -sz : sz;
            float x1 = x * cr - y * sr, y1 = x * sr + y * cr;
            float y2 = y1 * cp - z * sp, z2 = y1 * sp + z * cp;
            float x3 = x1 * cy0 - z2 * sy0, z3 = x1 * sy0 + z2 * cy0;
            corner[i] = new double[] {at.x + x3, at.y + y2, at.z + z3};
        }
        int[][] faces = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}};
        for (int[] f : faces) litFace(corner[f[0]], corner[f[1]], corner[f[2]], corner[f[3]], c, alpha);
    }

    // ------------------------------------------------------------------
    // Cloud
    // ------------------------------------------------------------------

    /** One lumpy billow of smoke, dust, fire or mist, shaded by {@code shade} (see {@link Shades}). */
    public void cloud(Vec3 at, float rx, float ry, float rz, int variant, float spin, float alpha, CloudMesh.Shade shade) {
        if (rx < 0.05F || alpha <= 0.003F) return;
        double distance = Math.sqrt((at.x - cx) * (at.x - cx) + (at.y - cy) * (at.y - cy) + (at.z - cz) * (at.z - cz));
        CloudMesh.puff(out, m, CloudMesh.detail(rx, distance), (float) (at.x - cx), (float) (at.y - cy), (float) (at.z - cz),
                rx, ry, rz, variant, spin, 1, alpha, (float) at.x, (float) at.y, (float) at.z, shade);
    }

    // ------------------------------------------------------------------

    /** A repeatable random number from 0 to 1. */
    public static float hash(int seed, int i) {
        int h = seed * 374761393 + i * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    public static float smooth(float a, float b, float x) {
        float k = Mth.clamp((x - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }

    /** 0 to 1 over the first {@code in} ticks, 1 to 0 over the last {@code out} of {@code life}. */
    public static float fade(float age, float life, float in, float out) {
        return Mth.clamp(age / Math.max(in, 1e-3F), 0, 1) * Mth.clamp((life - age) / Math.max(out, 1e-3F), 0, 1);
    }
}
