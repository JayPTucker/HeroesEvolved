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

    /**
     * Epicenter ({@code quake/Epicenter}): the double-fisted strike flashes; eight faults race out to a jagged ring and
     * glow brighter as the plates load (the warning); at {@code HEAVE} every wedge of ground tips up and out in slabs while
     * a pressure ring rips outward; at {@code DROP} the slabs slam back and a dust ring rolls in to the centre. Origin the
     * centre on the ground, scale the radius; the timings are {@code Epicenter}'s.
     */
    static void epicenter(Canvas c, Active a, float t) {
        int heave = com.jayptucker.heroesevolved.quake.Epicenter.HEAVE, drop = com.jayptucker.heroesevolved.quake.Epicenter.DROP;
        int seams = com.jayptucker.heroesevolved.quake.Epicenter.SEAMS;
        float life = a.piece().ticks, r = Math.max(2, a.scale()), alpha = fade(t, life, .5F, 10);
        int seed = a.seed();
        Vec3 centre = a.origin(), floor = centre.add(0, .04, 0);
        // The strike itself.
        if (t < 8) {
            float p = t / 8;
            pressure(c, floor.add(0, .02, 0), UP, .3F + 2.4F * p, .1F, 1 - p, .1F, t);
            if (c.glow() && t < 3) c.glow(centre.add(0, .6, 0), 1.6F, CORE, .7F * (1 - t / 3));
            grit(c, centre.add(0, .2, 0), UP.scale(.5), minimal() ? 6 : 14, .8F, t, 10, seed);
        }
        // The faults: jagged seams racing out, then a ring joining their ends.
        float run = smooth(0, 10, t), warn = smooth(4, heave, t), settle = smooth(drop + 4, life, t);
        Vec3[] ends = new Vec3[seams];
        for (int i = 0; i < seams; i++) {
            float yaw = i * TWO_PI / seams + (hash(seed, i) - .5F) * .35F;
            Vec3 dir = new Vec3(cos(yaw), 0, sin(yaw));
            ends[i] = floor.add(dir.scale(r * (.93 + .1 * hash(seed, i + 40))));
            Vec3 prev = floor;
            for (int k = 1; k <= 5; k++) {
                float u = k / 5F;
                if (u > run + .001F) break;
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                Vec3 next = k == 5 ? ends[i] : floor.add(dir.scale(r * u)).add(side.scale((hash(seed, i * 7 + k) - .5) * .9));
                crack(c, prev, next, seed + i * 11 + k, .1F + .05F * warn, alpha * (1 - settle * .6F));
                if (c.glow() && t < drop + 2) {
                    float pulse = (.5F + .5F * sin(t * (.5F + 1.5F * warn) + i)) * (.35F + .65F * warn);
                    c.line(prev.add(0, .02, 0), next.add(0, .02, 0), .22F, DEEP, alpha * pulse * .6F, alpha * pulse * .6F);
                    c.line(prev.add(0, .03, 0), next.add(0, .03, 0), .07F, CORE, alpha * pulse, alpha * pulse);
                }
                prev = next;
            }
        }
        if (t > 8) for (int i = 0; i < seams; i++) {
            Vec3 a0 = ends[i], b0 = ends[(i + 1) % seams];
            Vec3 mid = a0.lerp(b0, .5).subtract(floor).scale(1.03).add(floor);
            float show = smooth(8, 14, t) * alpha * (1 - settle * .6F);
            crack(c, a0, mid, seed + 300 + i, .09F, show);
            crack(c, mid, b0, seed + 400 + i, .09F, show);
            if (c.glow() && t < heave) {
                float pulse = .3F + .7F * warn;
                c.line(a0.add(0, .02, 0), mid.add(0, .02, 0), .05F, WAVE, show * pulse, show * pulse);
                c.line(mid.add(0, .02, 0), b0.add(0, .02, 0), .05F, WAVE, show * pulse, show * pulse);
            }
        }
        // The plates loading: rumble rings creeping in from the rim.
        if (t < heave) for (int i = 0; i < 2; i++) {
            float u = frac(t / 12F + i * .5F);
            pressure(c, floor.add(0, .03, 0), UP, r * (1 - u) + .3F, .05F, alpha * warn * u * .6F, .04F, t + i);
        }
        // The heave: every wedge tips up and out in slabs, then they slam back at the drop.
        if (t >= heave - 1) {
            float up = smooth(heave - 1, heave + 4, t), down = smooth(drop - 1, drop + 3, t);
            float tilt = up * (1 - down), lift = tilt * .7F;
            if (c.solid()) for (int i = 0; i < seams; i++) {
                float yaw = (i + .5F) * TWO_PI / seams;
                Vec3 dir = new Vec3(cos(yaw), 0, sin(yaw));
                for (int k = 0; k < 4; k++) {
                    float rr = r * (.22F + .22F * k), size = .45F + .18F * k;
                    float wob = (hash(seed, i * 5 + k) - .5F) * .3F;
                    Vec3 at = floor.add(dir.scale(rr)).add(0, lift * (.6 + .25 * k) - .12 * down * (1 - smooth(drop + 3, drop + 10, t)) - .1, 0);
                    c.box(at, size, .22F, size * .8F, -yaw + wob, -(.6F + .5F * hash(seed, i + k * 9)) * tilt, wob * 2 * tilt, k % 2 == 0 ? STONE : CRACK, 1);
                }
            }
            if (t < heave + 12) {
                float p = smooth(heave, heave + 12, t);
                pressure(c, floor.add(0, .4, 0), UP, .5F + r * 1.15F * p, .12F, alpha * (1 - p), .08F, t);
                if (c.glow() && t < heave + 3) c.glow(centre.add(0, 1, 0), r * .5F, WAVE, .5F * (1 - (t - heave) / 3));
                for (int i = 0; i < seams; i++) {
                    float yaw = i * TWO_PI / seams;
                    grit(c, floor.add(cos(yaw) * r * .55, .2, sin(yaw) * r * .55), UP.scale(1.4), minimal() ? 3 : 7, r * .25F, t - heave, 16, seed + i);
                }
            }
        }
        // The drop: a dust ring rolling in to the centre.
        if (t >= drop) {
            float p = smooth(drop, drop + 12, t);
            pressure(c, floor.add(0, .1, 0), UP, r * (1 - p) + .3F, .1F, alpha * (1 - p * .7F), .1F, t);
            for (int i = 0; i < seams; i++) {
                float yaw = (i + .5F) * TWO_PI / seams;
                grit(c, floor.add(cos(yaw) * r * (1 - p) * .9, .15, sin(yaw) * r * (1 - p) * .9), UP.scale(.3), minimal() ? 2 : 5, .5F, t - drop, 12, seed + 50 + i);
            }
        }
    }

    /**
     * The alternate finishers ({@code quake/QuakeFinishers}). Fault Line: a stomp ring at her feet, a crack running along
     * the ground to the foe, slabs tilting up round it as it heaves, then folding in over it in dust. Shatter Point:
     * pressure rings shivering on the body, cracks of light spreading over it, then a burst into a ring of dust. Origin
     * the foe's feet, target hers, bound to the foe, scale its height.
     */
    static void alternates(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 1, 8);
        Vec3 feet = a.origin(), home = a.target();
        int seed = a.seed();
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_FAULT) {
            int stomp = com.jayptucker.heroesevolved.quake.QuakeFinishers.FAULT_STOMP, reach = com.jayptucker.heroesevolved.quake.QuakeFinishers.FAULT_REACH;
            int heave = com.jayptucker.heroesevolved.quake.QuakeFinishers.FAULT_HEAVE, land = com.jayptucker.heroesevolved.quake.QuakeFinishers.FAULT_LAND;
            if (t >= stomp) {
                float p = clamp((t - stomp) / 8, 0, 1);
                pressure(c, home.add(0, .06, 0), UP, .4F + 1.6F * p, .08F, f * (1 - p), .08F, t);
                // The crack runs to the foe in jagged steps and stays.
                float run = smooth(stomp, reach, t);
                int steps = 6;
                Vec3 prev = home.add(0, .03, 0);
                for (int i = 1; i <= steps; i++) {
                    float u = i / (float) steps;
                    if (u > run + .001F) break;
                    Vec3 dir = feet.subtract(home).multiply(1, 0, 1);
                    Vec3 side = dir.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(-dir.z, 0, dir.x).normalize();
                    Vec3 next = home.lerp(feet, u).add(side.scale((hash(seed, i) - .5) * .5 * (i == steps ? 0 : 1))).add(0, .03, 0);
                    crack(c, prev, next, seed + i, .09F, f);
                    if (c.glow() && t < reach + 3) c.line(prev, next, .03F, WAVE, f * .5F, f * .5F);
                    prev = next;
                }
            }
            // Slabs tilt up in a ring as the ground heaves, then fold in over the spot.
            if (t >= reach - 1) {
                float up = smooth(reach - 1, heave, t), fold = smooth(heave, land, t);
                int slabs = minimal() ? 5 : 8;
                float r = Math.max(.7F, h * .35F + .4F);
                if (c.solid()) for (int i = 0; i < slabs; i++) {
                    float yaw = i * TWO_PI / slabs + hash(seed, i + 20) * .3F;
                    Vec3 out = new Vec3(cos(yaw), 0, sin(yaw));
                    float tilt = up * (.9F + .2F * hash(seed, i + 30)) * (1 - fold) + fold * 1.45F;
                    float sink = smooth(land + 4, land + 12, t) * .5F;
                    Vec3 at = feet.add(out.scale(r * (1 - .45 * fold))).add(0, .25 * up - sink, 0);
                    c.box(at, .32F, .1F, .26F, -yaw, -tilt, 0, STONE, 1);
                }
                if (t >= land) {
                    dust(c, feet.add(0, .3, 0), UP, t - land, 1.4F, seed);
                    grit(c, feet.add(0, .2, 0), UP.scale(.6), minimal() ? 8 : 18, 1.2F, t - land, 14, seed + 5);
                }
            }
            return;
        }
        // Shatter Point.
        int touch = com.jayptucker.heroesevolved.quake.QuakeFinishers.SHATTER_TOUCH, land = com.jayptucker.heroesevolved.quake.QuakeFinishers.SHATTER_LAND;
        Vec3 mid = feet.add(0, h * .5, 0);
        if (t < land) {
            float build = smooth(touch, land, t);
            for (int i = 0; i < 3; i++)
                pressure(c, feet.add(0, h * (.2 + .3 * i), 0), UP, Math.max(.35F, h * .26F) * (1 + .05F * sin(t * 3 + i)), .04F,
                        f * (.3F + .5F * build), .15F + .1F * build, t * 5 + i);
            // Cracks of light spreading over the body, more every few ticks.
            int cracks = (int) (build * 9);
            for (int i = 0; i < cracks; i++) {
                float yaw = hash(seed, i) * TWO_PI, y = hash(seed, i + 30);
                float r = Math.max(.3F, h * .25F);
                Vec3 from = feet.add(cos(yaw) * r, h * y, sin(yaw) * r);
                Vec3 to = feet.add(cos(yaw + .5F) * r, h * Math.min(1, y + .2F), sin(yaw + .5F) * r);
                crack(c, from, to, seed + i, .03F, f);
                if (c.glow()) c.bolt(from, to, seed + i, .12F, .012F, CORE, f * .8F * build);
            }
            if (c.glow()) c.glow(mid, .4F + .6F * build, WAVE, f * .3F * build);
        } else {
            shatter(c, mid, h, t - land + 4, seed);
            dust(c, feet.add(0, .3, 0), UP, t - land, 1.6F, seed + 3);
        }
    }

    /**
     * Counterbeat (the finisher, {@code quake/Counterbeat}): each heartbeat the foe's body shows is a double ring of
     * pressure opening at its chest, coming faster and faster; the counter-pulse is rings closing in on the chest from
     * her side; then one flat ring spreads over the ground and no more beats come. Bound to the foe, Quake's entity id in
     * the seed; origin its feet; scale its height.
     */
    static void finisher(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = fade(t, life, 1, 6);
        net.minecraft.world.entity.Entity foe = a.bound(c), owner = c.level.getEntity(a.seed());
        Vec3 feet = foe != null ? foe.getPosition(c.partial) : a.origin();
        Vec3 chest = feet.add(0, h * .6, 0);
        Vec3 toward = owner != null ? owner.getPosition(c.partial).subtract(feet).multiply(1, 0, 1) : new Vec3(0, 0, 1);
        Vec3 axis = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize();
        float phase = (c.level.getGameTime() % 2400 + c.partial) * .3F;
        int counter = com.jayptucker.heroesevolved.quake.Counterbeat.COUNTER, land = com.jayptucker.heroesevolved.quake.Counterbeat.LAND;
        // The heartbeats: a double pulse at each, the second a moment after the first.
        for (int beat : com.jayptucker.heroesevolved.quake.Counterbeat.BEATS) {
            for (int twice = 0; twice < 2; twice++) {
                float age = t - beat - twice * 1.2F;
                if (age < 0 || age > 5) continue;
                float k = age / 5;
                pressure(c, chest, axis, .15F + .55F * k, .05F, f * (1 - k), .2F, phase);
            }
        }
        // The counter-pulse: rings falling in on the chest.
        if (t >= counter && t < land + 1) {
            for (int i = 0; i < 3; i++) {
                float k = smooth(counter + i * .6F, land, t);
                if (k <= 0 || k >= 1) continue;
                pressure(c, chest.add(axis.scale(.3 * (1 - k))), axis, 1.2F * (1 - k) + .05F, .07F, f * (1 - k * .5F), .4F, phase);
            }
        }
        // Silence: one flat ring over the ground.
        if (t >= land) {
            float k = smooth(land, land + 8, t);
            pressure(c, feet.add(0, .06, 0), UP, .4F + 3.6F * k, .08F, f * (1 - k), .1F, phase);
            grit(c, feet, UP.scale(.2), 8, 1.2F, t - land, 10, a.seed());
        }
    }

    // ------------------------------------------------------------------
    // Ultimates (quake/QuakeUltimates)
    // ------------------------------------------------------------------

    /** The cost on the body: red and white strain. */
    private static final float[] STRAIN = rgb(0xFF5A5A);

    /**
     * Heartquake ({@code QuakeUltimates.Heartquake}): a ring locking round the foe and a column of rings lifting it,
     * rings shivering up and down it as it shakes, strain cracking red and white up her forearms as she draws back, the
     * tube of rings that blasts it along the ground (each ring a moment after the last, so the wave is seen to travel),
     * the fault ploughed behind it, and the fault erupting along its length.
     */
    static void heart(Canvas c, Active a, float t) {
        if (UltArt.cut(c, a)) return;
        Entity caster = a.bound(c), foe = UltArt.foe(c, a);
        Vec3 base = a.target(), facing = a.facing();
        Vec3 side = new Vec3(-facing.z, 0, facing.x);
        float h = Math.max(.8F, a.scale()), phase = c.time() * .9F;
        int seed = a.seed() * 5 + 7;
        int lock = com.jayptucker.heroesevolved.quake.QuakeUltimates.Heartquake.LOCK,
                shake = com.jayptucker.heroesevolved.quake.QuakeUltimates.Heartquake.SHAKE,
                draw = com.jayptucker.heroesevolved.quake.QuakeUltimates.Heartquake.DRAW,
                blast = com.jayptucker.heroesevolved.quake.QuakeUltimates.Heartquake.BLAST,
                blasted = com.jayptucker.heroesevolved.quake.QuakeUltimates.Heartquake.BLASTED,
                erupt = com.jayptucker.heroesevolved.quake.QuakeUltimates.Heartquake.ERUPT;
        double throwLen = com.jayptucker.heroesevolved.quake.QuakeUltimates.Heartquake.THROW;
        Vec3 landing = base.add(facing.scale(throwLen));
        Vec3 foeAt = foe != null ? foe.getPosition(c.partial) : base;
        // The lock: a ring closing round the foe's middle.
        float locked = UltArt.window(t, 0, blast + 1, 4, 1);
        if (locked > 0) {
            float close = smooth(0, lock, t);
            pressure(c, foeAt.add(0, h * .55, 0), UP, 2.6F - 1.8F * close, .07F, locked, .03F, phase);
        }
        // The shaking: rings running up and down the body and a column of them lifting it off the ground.
        float shaking = UltArt.window(t, shake, blast + 1, 3, 1);
        if (shaking > 0) {
            for (int i = 0; i < 4; i++) {
                float y = (float) (.5 + .5 * Math.sin(phase * .7F + i * 1.6F));
                pressure(c, foeAt.add(0, h * y, 0), UP, .55F + .1F * i, .04F, shaking * .8F, .08F, phase + i);
            }
            for (int i = 0; i < 5; i++) {
                double y = foeAt.y - base.y;
                if (y < .2) break;
                pressure(c, base.add(0, y * i / 5 + .05, 0), UP, .7F - .05F * i, .05F, shaking * .6F, .05F, phase * 1.3F + i);
            }
        }
        // The strain: red and white cracks running up her forearms as she draws back.
        float strain = UltArt.window(t, draw, blast + 8, 4, 6);
        if (strain > 0 && caster != null && c.glow()) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 hand = UltArt.hand(caster, c.partial, s, .5), elbow = UltArt.hand(caster, c.partial, s, .15).add(0, -.1, 0);
                c.bolt(hand, elbow, seed + s * 7 + (int) (t / 3), .06F, .03F, STRAIN, strain);
                c.bolt(hand, elbow, seed + s * 11 + (int) (t / 3), .05F, .012F, CORE, strain);
            }
        }
        // The blast: a tube of rings opening along the ground from her palms to where the foe lands.
        if (t >= blast && t < blast + 22) {
            Vec3 palms = a.origin().add(0, 1.2, 0).add(facing.scale(.8));
            float age = t - blast, f = 1 - smooth(10, 22, age);
            int rings = minimal() ? 6 : 12;
            for (int i = 0; i < rings; i++) {
                float d = (float) (i * (throwLen + 2) / rings), delay = d / 3, k = age - delay;
                if (k < 0) continue;
                float life = 1 - smooth(4, 12, k);
                pressure(c, palms.add(facing.scale(d)), facing, .18F + .55F * (float) Math.sqrt(d) * smooth(0, 3, k), .06F, f * life, .04F, phase + i);
            }
            if (c.glow() && age < 4) c.glow(palms, 1.2F * (1 - age / 4), CORE, 1 - age / 4);
        }
        // The fault ploughed behind the foe, and the dust it throws.
        if (t > blast) {
            float run = smooth(blast, blasted, t), f = fade(t - blast, a.piece().ticks - blast, 0, 14);
            Vec3 from = base.add(0, .05, 0), to = from.lerp(landing.add(0, .05, 0), run);
            crack(c, from, to, seed, .14F, f);
            crack(c, from.add(side.scale(.25)), to.add(side.scale(.2)), seed + 1, .05F, f * .7F);
            if (t < blasted + 6) grit(c, to, facing.scale(-1.2).add(0, .8, 0), 14, .6F, (t - blast) % 8, 8, seed + (int) ((t - blast) / 8));
        }
        // The eruption: pressure bursting up all along the fault, slabs of dust thrown.
        if (t >= erupt) {
            float age = t - erupt, f = fade(age, a.piece().ticks - erupt, 0, 14);
            int n = minimal() ? 4 : 8;
            for (int i = 0; i < n; i++) {
                float u = (i + .5F) / n, k = age - u * 4;
                if (k < 0) continue;
                Vec3 at = base.lerp(landing, u).add(0, .1, 0);
                pressure(c, at, UP, .3F + 2.4F * smooth(0, 10, k), .08F, f * (1 - smooth(6, 14, k)), .05F, phase + i);
                grit(c, at, new Vec3(0, 1.6, 0), 10, .9F, k, 14, seed + i * 9);
                dust(c, at, at.subtract(facing), k, 1.6F, seed + i);
            }
        }
    }

    /**
     * Pressure Barrier, one second (re-sent each second; the second in the seed, -1 its pop): a sphere of shimmering
     * rings round the body at the scale's radius, each turning on its own axis; the pop throws them outward.
     */
    static void barrier(Canvas c, Active a, float t) {
        if (UltArt.cut(c, a)) return;
        Entity body = a.bound(c);
        Vec3 centre = (body != null ? body.getPosition(c.partial) : a.origin()).add(0, 1, 0);
        int second = a.seed();
        float r = a.scale(), phase = c.time() * .4F;
        if (second < 0) {
            float k = smooth(0, 10, t), f = 1 - smooth(4, a.piece().ticks, t);
            for (int i = 0; i < 4; i++) pressure(c, centre, new Vec3(cos(i * 1.3F), .6, sin(i * 1.3F)), r * (1 + 1.6F * k), .1F, f, .05F, phase);
            return;
        }
        boolean last = second >= com.jayptucker.heroesevolved.quake.QuakeUltimates.PressureBarrier.TICKS / 20 - 1;
        float f = Math.clamp(t / 2 + (second > 0 ? .5F : 0), 0, 1) * (last ? 1 - smooth(10, 22, t) : Math.clamp((a.piece().ticks - t) / 2 + .3F, 0, 1));
        float grow = second == 0 ? smooth(0, 8, t) : 1;
        int rings = minimal() ? 3 : 6;
        for (int i = 0; i < rings; i++) {
            float ang = phase * (.5F + .1F * i) + i * 1.05F;
            Vec3 axis = new Vec3(cos(ang), .4 + .3 * sin(ang * .7F + i), sin(ang));
            pressure(c, centre, axis, r * grow, .05F, f * .8F, .025F, phase * 2 + i);
        }
        if (c.soft()) c.shell(centre, r * grow, r * grow, r * grow, PI, WAVE, f * .05F, .2F, PLAIN, 0);
    }

    /** A ripple across a blow: a pressure ring on the plane across it, opening (scale its size). */
    static void ripple(Canvas c, Active a, float t) {
        Vec3 axis = a.origin().subtract(a.target());
        float k = smooth(0, 8, t), f = 1 - smooth(3, a.piece().ticks, t);
        pressure(c, a.origin(), axis, (.2F + 1.2F * k) * Math.max(.4F, a.scale()), .05F, f, .04F, t);
    }

    /** Groundswell on a friend (bound; re-sent every two seconds): a ring running out over the ground from their feet, rings round their fists. */
    static void swell(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        Vec3 feet = body.getPosition(c.partial).add(0, .06, 0);
        float k = smooth(0, 20, t), f = fade(t, a.piece().ticks, 2, 10), phase = c.time() * .6F;
        pressure(c, feet, UP, .4F + 2.4F * k, .06F, f * (1 - k), .04F, phase);
        pressure(c, feet, UP, .7F, .04F, f * .5F, .03F, phase);
        for (int s = -1; s <= 1; s += 2)
            pressure(c, UltArt.hand(body, c.partial, s, .5), UP, .18F, .02F, f * .6F, .04F, phase + s);
    }
}
