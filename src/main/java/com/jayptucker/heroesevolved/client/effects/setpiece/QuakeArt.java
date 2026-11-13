package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Daisy Johnson's vibrations as set pieces, in the look her blasts already have ({@code QuakeEffects}): clear,
 * shimmering pressure rings, with the ground answering in dark cracks and grit. Three primitives draw every piece:
 * <ul>
 *   <li>{@link #pressure}: a ring of warped air whose edge wobbles;</li>
 *   <li>{@link #crack}: a dark jagged fracture, drawn with normal blending so black shows;</li>
 *   <li>{@link #grit}: flecks of dust and stone thrown out and falling.</li>
 * </ul>
 * Palette: {@code CFE6FF} wave, {@code FFFFFF} core, {@code 7FA8D8} deep, {@code A8946E} dust, {@code 2A2622} crack.
 */
final class QuakeArt {
    private static final float[] WAVE = rgb(0xCFE6FF), CORE = rgb(0xFFFFFF), DEEP = rgb(0x7FA8D8), DUST = rgb(0xA8946E),
            CRACK = rgb(0x2A2622), STONE = rgb(0x6E675E);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private QuakeArt() {}

    static void draw(Canvas c, Active a, float t) {
        Vec3 at = a.origin(), end = a.target();
        Entity bound = a.bound(c);
        float life = a.piece().ticks, size = a.scale();
        float clock = (c.level.getGameTime() % 2400 + c.partial);
        int seed = a.seed();
        switch (a.piece()) {
            case QUAKE_DUST -> dust(c, at, end.subtract(at), t, size, seed);
            case QUAKE_SHATTER -> shatter(c, at, size, t, seed);
            case QUAKE_LANCE -> lance(c, at, end, size >= 1.5F, t, seed);
            case QUAKE_SHELL -> {
                Vec3 o = bound == null ? at : bound.getPosition(c.partial);
                float alpha = fade(t, life, 2, 2);
                for (int i = 0; i < 6; i++) {
                    float lat = (i + 0.5F) / 6 * HALF_PI;
                    pressure(c, o.add(0, 0.05 + size * sin(lat) * 0.85F, 0), UP, size * cos(lat) + 0.05F, 0.035F,
                            alpha * (0.32F - i * 0.03F), 0.03F + 0.015F * sin(clock * 1.7F + i), clock * 0.9F + i);
                }
                if (c.soft()) c.disc(o.add(0, 0.04, 0), UP, size, DEEP, 0.1F * alpha, 0);
            }
            case QUAKE_BRACE -> {
                if (bound == null) return;
                Vec3 o = bound.getPosition(c.partial);
                float alpha = fade(t, life, 2, 2), stored = clamp(size, 0, 1);
                for (int i = 0; i < 3; i++) {
                    float k = frac(clock * 0.08F + i / 3F);
                    // Rings drawn in to the crossed forearms, closing faster the more is stored.
                    pressure(c, o.add(0, 1.1, 0), UP, 1.4F - k * 1.0F, 0.04F, alpha * k * (0.35F + 0.4F * stored), 0.06F, clock + i);
                }
                if (c.glow()) c.glow(o.add(0, 1.15, 0), 0.25F + 0.4F * stored, CORE, alpha * (0.15F + 0.45F * stored));
            }
            case QUAKE_DASH -> {
                Vec3 dir = end.subtract(at).normalize();
                float alpha = fade(t, life, 0.5F, 7);
                for (int i = 0; i < 5; i++) {
                    float d = i * 1.6F, show = clamp(t * 2.2F - i, 0, 1);
                    pressure(c, at.add(dir.scale(d)).add(0, 1, 0), dir, 0.5F + i * 0.06F, 0.05F, alpha * show * (1 - i * 0.12F), 0.05F, d);
                }
                float p = clamp(t / 9, 0, 1);
                pressure(c, at.add(0, 0.06, 0), UP, 0.5F + p * 2.5F, 0.08F, alpha * (1 - p), 0.05F, t);
            }
            case QUAKE_SEAM -> seam(c, at, end.subtract(at).normalize(), size, t, life, seed);
            case QUAKE_TRACE -> {
                // One step of a written fault: a hot seam in the ground that brightens until it answers.
                float fuse = Math.max(4, size);
                if (t > fuse) return;
                float warn = t / fuse, alpha = fade(t, fuse, 2, 1) * (0.45F + 0.55F * warn);
                Vec3 a0 = at.add(0, 0.05, 0), b0 = end.add(0, 0.05, 0);
                crack(c, a0, b0, seed, 0.14F, alpha);
                if (c.glow()) {
                    float pulse = 0.6F + 0.4F * sin(t * (0.6F + warn * 1.6F));
                    c.line(a0.add(0, 0.02, 0), b0.add(0, 0.02, 0), 0.2F, DEEP, alpha * pulse * 0.8F, alpha * pulse * 0.8F);
                    c.line(a0.add(0, 0.03, 0), b0.add(0, 0.03, 0), 0.06F, CORE, alpha * pulse, alpha * pulse);
                }
            }
            default -> { }
        }
    }

    /** A bullet meets the wave: a quick ring of warped air where it was, and it drifts on as a puff of grit. */
    private static void dust(Canvas c, Vec3 at, Vec3 from, float t, float size, int seed) {
        Vec3 dir = from.lengthSqr() < 1.0E-6 ? UP : from.normalize();
        float p = clamp(t / 5, 0, 1), open = 1 - (1 - p) * (1 - p);
        pressure(c, at, dir, (0.15F + 1.1F * open) * size, 0.07F, (1 - p), 0.1F, t);
        pressure(c, at, UP, (0.1F + 0.8F * open) * size, 0.05F, (1 - p) * 0.7F, 0.1F, t + 2);
        if (c.glow() && t < 4) c.glow(at, 0.7F * size, CORE, 0.8F * (1 - t / 4));
        grit(c, at, dir.scale(0.8), minimal() ? 8 : 20, 0.8F * size, t, 14, seed);
    }

    /** A resonance collapsing: rings squeeze in on the body, then break outward with cracks and grit. */
    private static void shatter(Canvas c, Vec3 mid, float height, float t, int seed) {
        float h = Math.max(1, height);
        if (t < 4) {
            float k = t / 4;
            for (int i = 0; i < 4; i++)
                pressure(c, mid.add(0, (i - 1.5) * h * 0.25, 0), UP, (0.9F - 0.5F * k) * h * 0.5F, 0.05F, 0.4F + 0.5F * k, 0.12F, t * 4 + i);
            return;
        }
        float s = t - 4, p = clamp(s / 10, 0, 1), open = 1 - (1 - p) * (1 - p) * (1 - p);
        for (int i = 0; i < 3; i++)
            pressure(c, mid.add(0, (i - 1) * h * 0.3, 0), UP, (0.4F + 1.8F * open) * h * 0.5F, 0.07F, (1 - p) * 0.8F, 0.08F, s + i);
        if (c.soft()) for (int i = 0; i < (minimal() ? 3 : 6); i++) {
            float yaw = hash(seed, i) * TWO_PI, pitch = (hash(seed, i + 9) - 0.5F) * 2;
            Vec3 d = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
            crack(c, mid.add(d.scale(0.2)), mid.add(d.scale(0.2 + 0.7 * h * open)), seed + i, 0.05F, (1 - p) * 0.9F);
        }
        if (c.glow() && s < 3) c.glow(mid, 0.9F * h, CORE, 0.6F * (1 - s / 3));
        grit(c, mid, UP.scale(0.2), minimal() ? 6 : 16, 0.8F * h, s, 12, seed);
    }

    /** Seismic Lance: a column of earth bursting up under one enemy, or (smashing down) rings slamming onto the spot. */
    private static void lance(Canvas c, Vec3 base, Vec3 top, boolean down, float t, int seed) {
        float alpha = fade(t, 24, 0.5F, 10);
        Vec3 floor = base.add(0, 0.05, 0);
        if (down) {
            float drop = clamp(t / 3, 0, 1);
            for (int i = 0; i < 4; i++)
                pressure(c, base.add(0, 3.5 - drop * 3.5 + i * 0.6, 0), UP, 0.5F + i * 0.1F, 0.05F, alpha * (1 - drop * 0.5F), 0.06F, t + i);
        } else {
            float rise = 1 - (float) Math.pow(1 - clamp(t / 4, 0, 1), 3), height = (float) top.subtract(base).y * rise;
            for (int i = 0; i < 6; i++) {
                float k = (i + 0.5F) / 6;
                pressure(c, base.add(0, height * k, 0), UP, 0.65F - 0.25F * k, 0.06F, alpha * (1 - k * 0.5F), 0.1F, t * 2 + i);
            }
            if (c.solid()) for (int i = 0; i < (minimal() ? 3 : 7); i++) {
                // Slabs of rock thrown up the column and tumbling off it.
                float yaw = hash(seed, i) * TWO_PI, r = 0.3F + hash(seed, i + 5) * 0.5F, up = 1.5F + hash(seed, i + 11) * 2;
                float y = up * clamp(t / 6, 0, 1) - 0.05F * Math.max(0, t - 6) * Math.max(0, t - 6);
                if (y < -0.2F) continue;
                c.box(base.add(cos(yaw) * r * (1 + t * 0.05F), y + 0.2, sin(yaw) * r * (1 + t * 0.05F)), 0.16F, 0.12F, 0.14F,
                        yaw + t * 0.3F, t * 0.25F, i, STONE, 1);
            }
        }
        float p = clamp(t / 10, 0, 1);
        pressure(c, floor, UP, 0.6F + 2.2F * (1 - (1 - p) * (1 - p)), 0.09F, alpha * (1 - p), 0.05F, t);
        if (c.soft()) for (int i = 0; i < 5; i++) {
            float yaw = i * TWO_PI / 5 + hash(seed, i) * 0.6F;
            Vec3 d = new Vec3(cos(yaw), 0, sin(yaw));
            crack(c, floor.add(d.scale(0.3)), floor.add(d.scale(0.3 + 1.6 * clamp(t / 5, 0, 1))), seed + i, 0.06F, alpha * 0.9F);
        }
        grit(c, base.add(0, 0.2, 0), UP.scale(0.5), minimal() ? 6 : 14, 0.9F, t, 18, seed);
    }

    /** Surgical Fracture: a palm-print of warped air on the wall, and cracks running out across the face of it. */
    private static void seam(Canvas c, Vec3 at, Vec3 normal, float size, float t, float life, int seed) {
        float alpha = fade(t, life, 1, 12), grow = 1 - (float) Math.pow(1 - clamp(t / 10, 0, 1), 2);
        Vec3 face = at.add(normal.scale(0.03));
        float p = clamp(t / 6, 0, 1);
        pressure(c, face, normal, 0.2F + 0.9F * p * size, 0.05F, alpha * (1 - p), 0.1F, t);
        Vec3 u = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : normal.cross(UP).normalize(), v = u.cross(normal).normalize();
        if (c.soft()) for (int i = 0; i < (minimal() ? 4 : 8); i++) {
            float ang = i * TWO_PI / 8 + hash(seed, i) * 0.5F, reach = (0.6F + hash(seed, i + 30) * 0.9F) * size * grow;
            Vec3 d = u.scale(cos(ang)).add(v.scale(sin(ang)));
            Vec3 mid = face.add(d.scale(reach * 0.55));
            crack(c, face, mid, seed + i, 0.05F, alpha);
            Vec3 branch = d.add(u.scale(-sin(ang) * 0.5)).add(v.scale(cos(ang) * 0.5)).normalize();
            crack(c, mid, mid.add(branch.scale(reach * 0.45)), seed + i + 50, 0.035F, alpha * 0.8F);
        }
        grit(c, face, normal.scale(0.4), minimal() ? 4 : 10, 0.4F * size, t, 16, seed);
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    /** A ring of warped air across {@code axis}: a wobbling bright edge, a white core, a soft blue halo. */
    static void pressure(Canvas c, Vec3 at, Vec3 axis, float radius, float width, float alpha, float wobble, float phase) {
        if (alpha < 0.01F || radius < 0.03F) return;
        Vec3 n = axis.lengthSqr() < 1.0E-6 ? UP : axis.normalize();
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0).cross(n).normalize() : UP.cross(n).normalize(), v = n.cross(u).normalize();
        int points = Math.max(24, Math.min(80, (int) (radius * 20)));
        Vec3[] ring = new Vec3[points + 1];
        for (int i = 0; i <= points; i++) {
            float ang = i * TWO_PI / points, r = radius * (1 + wobble * sin(ang * 6 + phase));
            ring[i] = at.add(u.scale(cos(ang) * r)).add(v.scale(sin(ang) * r));
        }
        if (c.soft()) c.ribbon(ring, width * 3, DEEP, alpha * 0.18F, alpha * 0.18F);
        if (c.glow()) {
            c.ribbon(ring, width, WAVE, alpha * 0.75F, alpha * 0.75F);
            c.ribbon(ring, width * 0.35F, CORE, alpha * 0.6F, alpha * 0.6F);
        }
    }

    /** A dark jagged fracture from one point to another (normal blending, so it shows dark). */
    static void crack(Canvas c, Vec3 from, Vec3 to, int seed, float width, float alpha) {
        if (!c.soft() || alpha < 0.01F) return;
        c.bolt(from, to, seed, 0.12F, width, CRACK, alpha);
    }

    /** Flecks of dust and stone thrown along {@code push} with some spread, slowing and falling over {@code life} ticks. */
    static void grit(Canvas c, Vec3 at, Vec3 push, int count, float spread, float t, float life, int seed) {
        if (!c.soft() || t > life) return;
        float k = t / life, alpha = (1 - k) * 0.85F;
        for (int i = 0; i < count; i++) {
            Vec3 d = new Vec3(hash(seed, i) - 0.5, hash(seed, i + 100) - 0.3, hash(seed, i + 200) - 0.5).scale(spread * 2).add(push);
            Vec3 p = at.add(d.scale(1 - (1 - k) * (1 - k))).add(0, -0.4 * k * k, 0);
            Vec3 tail = p.subtract(d.normalize().scale(0.08));
            c.line(tail, p, 0.035F, i % 3 == 0 ? STONE : DUST, alpha, alpha * 0.6F);
        }
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
