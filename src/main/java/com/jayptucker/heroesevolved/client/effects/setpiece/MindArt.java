package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/** Minds, life, foresight, empathy, machines and assembled matter. */
final class MindArt {
    private MindArt() {}

    private static final float[] MIND = rgb(0xC07CFF), MIND_DEEP = rgb(0x5A2AA8), MIND_PALE = rgb(0xF0DCFF), WHITE = {1, 1, 1};
    private static final float[] BLOOD = rgb(0xE0303C), LIFE_GOLD = rgb(0xFFD98A), LEAF = rgb(0x8CFF8A), BLOOM = rgb(0xFFB8E0);
    private static final float[] PAINT = rgb(0xD8452A), INK = rgb(0x5A1A12), CANVAS = rgb(0xEDE3CF), FUTURE = rgb(0x7FE8FF);
    private static final float[] HOLO = rgb(0x5CFFD0), HOLO_DEEP = rgb(0x1E9C8A);
    private static final float[] CRYSTAL = rgb(0xDCE8F8), CRYSTAL_EDGE = rgb(0x9FD0FF), LATTICE = rgb(0xFFE6A8);

    // ------------------------------------------------------------------
    // Telepathy
    // ------------------------------------------------------------------

    /** A storm of thought over the point: strands of violet wheeling round a bright core, synapses firing through it. */
    static void psychicStorm(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 12, 20), r = a.scale();
        Vec3 o = a.target();
        for (int k = 0; k < 7; k++) c.helix(o, 4.2F, r, 0.4F, 1.4F, k * Mth.TWO_PI / 7 - t * 0.12F, 0.09F, k % 2 == 0 ? MIND : MIND_PALE, 0.7F * f, 30);
        c.glow(o.add(0, 3.6, 0), 1.6F + 0.3F * Mth.sin(t * 0.5F), MIND_PALE, 0.8F * f);
        int flick = (int) (t / 3);
        for (int k = 0; k < 4; k++) {
            float ang = hash(a.seed() + flick, k) * Mth.TWO_PI, rr = r * hash(a.seed() + flick, k + 4);
            Vec3 p = o.add(Mth.cos(ang) * rr, 0.5 + hash(a.seed() + flick, k + 8) * 3, Mth.sin(ang) * rr);
            c.bolt(o.add(0, 3.6, 0), p, a.seed() + flick * 17 + k, 0.4F, 0.04F, MIND, 0.7F * f);
        }
        c.disc(o.add(0, 0.05, 0), UP, r, MIND_DEEP, 0.3F * f, 0);
        c.ring(o.add(0, 0.06, 0), r, 0.12F, MIND, 0.5F * f);
    }

    /** They slip out of every mind around them: rings of thought spread off them and a fading copy of them is left behind. */
    static void blindSpot(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = 1 - t / life;
        Vec3 o = a.at(c);
        if (c.soft()) c.shell(a.origin().add(0, 0.95, 0), 0.34F, 0.95F, 0.34F, Mth.PI, MIND_PALE, 0.35F * f, 0.3F, PLAIN, 0);
        if (!c.glow()) return;
        for (int k = 0; k < 3; k++) {
            float p = Mth.clamp((t - k * 5) / 18, 0, 1);
            if (p > 0) c.ring(o.add(0, 0.6 + k * 0.5, 0), 0.5F + p * 9, 0.15F, MIND, 0.7F * (1 - p));
        }
    }

    /** A thread of thought joins a friend's head to the hive above where it was made, pulses running along it. */
    static void hiveMind(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 10, 20);
        Vec3 head = a.at(c).add(0, 2.0, 0), hub = a.origin().add(0, 3.2, 0);
        Vec3 mid = head.lerp(hub, 0.5).add(0, 0.8, 0);
        Vec3[] curve = new Vec3[13];
        for (int i = 0; i <= 12; i++) {
            float k = i / 12F;
            curve[i] = head.scale((1 - k) * (1 - k)).add(mid.scale(2 * k * (1 - k))).add(hub.scale(k * k));
        }
        c.ribbon(curve, 0.05F, MIND, 0.7F * f, 0.7F * f);
        float p = (t * 0.05F) % 1;
        c.glow(curve[Math.min(12, (int) (p * 12))], 0.25F, MIND_PALE, f);
        c.ring(head.add(0, -0.1, 0), UP, 0.32F, 0.04F, t * 0.1F, t * 0.1F + Mth.PI * 1.6F, MIND_PALE, 0.8F * f);
        c.glow(hub, 0.7F, MIND, 0.6F * f);
        c.ring(hub, UP, 0.6F, 0.05F, -t * 0.08F, -t * 0.08F + Mth.PI * 1.5F, MIND_PALE, 0.6F * f);
    }

    // ------------------------------------------------------------------
    // Regeneration
    // ------------------------------------------------------------------

    /** A heartbeat of light, twice: crimson shells spreading off them, veins of it running out through the ground. */
    static void vitalBurst(Canvas c, Active a, float t) {
        float r = a.scale();
        Vec3 o = a.origin();
        if (c.soft()) for (int beat = 0; beat < 2; beat++) {
            float p = Mth.clamp((t - beat * 6) / 14, 0, 1);
            if (p > 0) c.shell(o.add(0, 1, 0), r * p, r * p * 0.8F, r * p, Mth.PI, BLOOD, 0.35F * (1 - p), 0, PLAIN, 0);
        }
        if (!c.glow()) return;
        float f = 1 - t / a.piece().ticks;
        for (int i = 0; i < 10; i++) {
            float ang = i * Mth.TWO_PI / 10 + hash(a.seed(), i), len = r * Mth.clamp(t / 8, 0, 1);
            Vec3[] p = new Vec3[7];
            for (int k = 0; k <= 6; k++) {
                float kk = k / 6F, wob = (hash(a.seed(), i * 7 + k) - 0.5F) * 0.6F;
                p[k] = o.add(Mth.cos(ang + wob * kk) * len * kk, 0.05, Mth.sin(ang + wob * kk) * len * kk);
            }
            c.ribbon(p, 0.1F, BLOOD, 0.9F * f, 0.1F * f);
        }
        if (t > 8) for (int i = 0; i < 12; i++) {
            float k = Mth.clamp((t - 8 - i) / 10, 0, 1), ang = i * Mth.TWO_PI / 12;
            if (k > 0 && k < 1) c.glow(o.add(Mth.cos(ang) * r * (1 - k), 1 + k * 0.2, Mth.sin(ang) * r * (1 - k)), 0.2F, LIFE_GOLD, 1 - k);
        }
        if (t < 4) c.glow(o.add(0, 1, 0), 3, BLOOD, 1 - t / 4);
    }

    /** White-gold strands wind tight round them like a cocoon, under a halo. */
    static void undying(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 8, 16);
        Vec3 o = a.at(c);
        float wrap = smooth(0, 14, t);
        for (int k = 0; k < 6; k++) c.helix(o, 2.1F, 1.4F - wrap * 0.75F, 0.45F, 1.6F, k * Mth.TWO_PI / 6 + t * 0.07F * (k % 2 == 0 ? 1 : -1), 0.07F,
                k % 2 == 0 ? LIFE_GOLD : WHITE, 0.7F * f, 26);
        c.ring(o.add(0, 2.35, 0), UP, 0.38F, 0.05F, 0, Mth.TWO_PI, LIFE_GOLD, 0.9F * f);
        c.glow(o.add(0, 2.35, 0), 0.7F, LIFE_GOLD, 0.4F * f);
        c.glow(o.add(0, 1, 0), 1.8F, LIFE_GOLD, 0.2F * f);
    }

    /** A tree of light grows where they stand, branch by branch, petals drifting off it. */
    static void lifebloom(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 4, 30), grow = smooth(0, 34, t), r = a.scale();
        Vec3 o = a.origin();
        List<Vec3> tips = new ArrayList<>();
        branch(c, a.seed(), o, new Vec3(0, 1, 0), 1.8F, 0, grow, f, tips);
        for (int i = 0; i < tips.size(); i++) c.glow(tips.get(i), 0.22F, i % 3 == 0 ? BLOOM : LEAF, 0.8F * f * grow);
        for (int i = 0; i < 18; i++) {
            float k = (t * 0.008F + hash(a.seed(), i)) % 1, ang = hash(a.seed(), i + 30) * Mth.TWO_PI, rr = 0.5F + hash(a.seed(), i + 60) * 2.5F;
            c.glow(o.add(Mth.cos(ang + k * 2) * rr, 4.2 * (1 - k), Mth.sin(ang + k * 2) * rr), 0.08F, BLOOM, f * grow);
        }
        c.ring(o.add(0, 0.06, 0), r, 0.1F, LEAF, 0.45F * f);
        c.disc(o.add(0, 0.05, 0), UP, r, LEAF, 0.12F * f, 0);
    }

    private static void branch(Canvas c, int seed, Vec3 from, Vec3 dir, float length, int depth, float grow, float f, List<Vec3> tips) {
        float start = depth * 0.2F, k = Mth.clamp((grow - start) / 0.25F, 0, 1);
        if (k <= 0) return;
        Vec3 to = from.add(dir.scale(length * k));
        c.line(from, to, 0.14F * (1 - depth * 0.2F), depth < 2 ? LIFE_GOLD : LEAF, 0.85F * f, 0.85F * f);
        if (depth >= 3 || k < 1) { if (depth >= 3) tips.add(to); return; }
        for (int i = 0; i < 3; i++) {
            float yaw = hash(seed, depth * 10 + i) * Mth.TWO_PI, tilt = 0.45F + hash(seed, depth * 10 + i + 5) * 0.4F;
            Vec3 d = dir.scale(Mth.cos(tilt)).add(Mth.cos(yaw) * Mth.sin(tilt), 0, Mth.sin(yaw) * Mth.sin(tilt)).normalize();
            branch(c, seed * 31 + i + depth, to, d, length * 0.68F, depth + 1, grow, f, tips);
        }
    }

    // ------------------------------------------------------------------
    // Precognition
    // ------------------------------------------------------------------

    /** A ring is painted on the ground and crossed out; when the painting comes true, it bursts in ink. */
    static void foretold(Canvas c, Active a, float t) {
        float r = a.scale(), strike = 40;
        Vec3 o = a.target().add(0, 0.06, 0);
        float fade = t < strike ? 1 : Mth.clamp(1 - (t - strike) / 20, 0, 1);
        if (c.glow() || c.soft()) {
            float drawn = smooth(0, 14, t);
            int steps = 48;
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i <= steps * drawn; i++) {
                float ang = i * Mth.TWO_PI / steps * 1.08F, rr = r * (1 + 0.04F * Mth.sin(i * 0.9F));
                pts.add(o.add(Mth.cos(ang) * rr, 0, Mth.sin(ang) * rr));
            }
            if (pts.size() > 1 && c.soft()) c.ribbon(pts.toArray(Vec3[]::new), 0.22F, PAINT, 0.95F * fade, 0.7F * fade);
            float x = smooth(14, 24, t);
            if (x > 0 && c.soft()) {
                c.line(o.add(-r * 0.6, 0.01, -r * 0.6), o.add(-r * 0.6 + r * 1.2 * x, 0.01, -r * 0.6 + r * 1.2 * x), 0.2F, PAINT, 0.95F * fade, 0.8F * fade);
                float y = smooth(19, 28, t);
                if (y > 0) c.line(o.add(r * 0.6, 0.01, -r * 0.6), o.add(r * 0.6 - r * 1.2 * y, 0.01, -r * 0.6 + r * 1.2 * y), 0.2F, PAINT, 0.95F * fade, 0.8F * fade);
            }
            if (c.glow() && t < strike) c.ring(o, r, 0.08F, PAINT, 0.3F + 0.3F * Mth.sin(t * 0.6F));
        }
        if (t >= strike) {
            float s = t - strike;
            if (c.glow()) {
                if (s < 5) c.glow(o.add(0, 1.5, 0), 5, WHITE, 1 - s / 5);
                c.ring(o, r * (1 + s * 0.1F), 0.4F, PAINT, 0.8F * fade);
            }
            if (c.soft()) for (int i = 0; i < 10; i++) {
                float ang = i * Mth.TWO_PI / 10, p = Mth.clamp(s / 14, 0, 1);
                c.cloud(o.add(Mth.cos(ang) * r * p, 0.5 + p * 1.5, Mth.sin(ang) * r * p), 0.7F, 0.5F, 0.7F, a.seed() + i, i, 0.6F * (1 - p),
                        Shades.flat(i % 2 == 0 ? PAINT : INK));
            }
        }
    }

    /** Three futures of them stand round them, pale cyan, threads of fate running back to the one that is real. */
    static void branching(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 8, 14);
        Vec3 o = a.at(c);
        for (int k = 0; k < 3; k++) {
            float ang = k * Mth.TWO_PI / 3 + 0.4F + Mth.sin(t * 0.04F + k) * 0.15F;
            Vec3 p = o.add(Mth.cos(ang) * 2.5, 0.95, Mth.sin(ang) * 2.5);
            if (c.soft()) c.shell(p, 0.33F, 0.95F, 0.33F, Mth.PI, FUTURE, 0.28F * f * (0.7F + 0.3F * Mth.sin(t * 0.3F + k)), 0.15F, PLAIN, 0);
            if (c.glow()) {
                c.line(o.add(0, 1, 0), p, 0.03F, FUTURE, 0.5F * f, 0.2F * f);
                c.ring(p.add(0, -0.9, 0), 0.5F, 0.05F, FUTURE, 0.5F * f);
            }
        }
    }

    /** Painted canvases hang in the air above them, turning, brush strokes swirling between them. */
    static void prophecy(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 10, 20);
        Vec3 o = a.origin();
        for (int i = 0; i < 5; i++) {
            float ang = i * Mth.TWO_PI / 5 + t * 0.02F;
            Vec3 p = o.add(Mth.cos(ang) * 2.4, 3 + Mth.sin(t * 0.05F + i) * 0.2, Mth.sin(ang) * 2.4);
            if (c.solid() && f > 0.3F) c.box(p, 0.55F * f, 0.42F * f, 0.03F, -ang + Mth.HALF_PI, 0, 0, CANVAS, 1);
            if (c.glow()) {
                Vec3 face = new Vec3(Mth.cos(ang), 0, Mth.sin(ang)).scale(0.05);
                Vec3 side = new Vec3(-Mth.sin(ang), 0, Mth.cos(ang));
                for (int s = 0; s < 3; s++) {
                    float hh = -0.25F + s * 0.25F;
                    c.line(p.add(face).add(side.scale(-0.4)).add(0, hh, 0), p.add(face).add(side.scale(0.4)).add(0, hh + 0.1, 0), 0.05F,
                            hue(i * 0.17F + s * 0.1F), 0.7F * f, 0.7F * f);
                }
            }
        }
        if (c.glow()) for (int k = 0; k < 3; k++) c.helix(o.add(0, 1.4, 0), 2.2F, 2.6F, 1.2F, 1.1F, k * 2.1F + t * 0.06F, 0.06F, hue(k * 0.3F), 0.5F * f, 24);
    }

    // ------------------------------------------------------------------
    // Empathic Mimicry
    // ------------------------------------------------------------------

    /** Every power they remember at once: arms of rainbow light spiral out of them, rings of colour after. */
    static void overflow(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = 1 - t / life, r = a.scale(), out = Mth.clamp(t / 14, 0, 1);
        Vec3 o = a.origin().add(0, 1, 0);
        for (int arm = 0; arm < 8; arm++) {
            Vec3[] p = new Vec3[17];
            for (int i = 0; i <= 16; i++) {
                float k = i / 16F * out, ang = arm * Mth.TWO_PI / 8 + k * 3;
                p[i] = o.add(Mth.cos(ang) * r * k, Mth.sin(k * Mth.PI) * 0.6, Mth.sin(ang) * r * k);
            }
            c.ribbon(p, 0.18F, hue(arm / 8F), 0.1F * f, 0.9F * f);
        }
        for (int k = 0; k < 3; k++) {
            float p = Mth.clamp((t - k * 4) / 18, 0, 1);
            if (p > 0) c.ring(o.add(0, -0.9, 0), r * p, 0.25F, hue(k / 3F + t * 0.01F), 0.8F * (1 - p));
        }
        if (t < 5) c.glow(o, 3.5F, WHITE, 1 - t / 5);
    }

    /** Three sigils of borrowed colour circle their chest, each a ring round a star. */
    static void ward(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 8, 16);
        Vec3 o = a.at(c).add(0, 1.2, 0);
        for (int k = 0; k < 3; k++) {
            float ang = k * Mth.TWO_PI / 3 + t * 0.07F;
            Vec3 p = o.add(Mth.cos(ang) * 1.3, Mth.sin(t * 0.08F + k) * 0.25, Mth.sin(ang) * 1.3);
            Vec3 axis = p.subtract(o).normalize();
            float[] col = hue(k / 3F + 0.05F);
            c.ring(p, axis, 0.35F, 0.04F, 0, Mth.TWO_PI, col, 0.9F * f);
            for (int s = 0; s < 5; s++) c.spoke(p, axis, s * Mth.TWO_PI / 5 + t * 0.1F, 0, 0.3F, 0.03F, col, 0.8F * f);
            c.glow(p, 0.5F, col, 0.4F * f);
        }
    }

    /** Halos in every colour rise up a friend, one after another. */
    static void kindred(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks;
        Vec3 o = a.at(c);
        for (int k = 0; k < 4; k++) {
            float p = Mth.clamp((t - k * 5) / 22, 0, 1);
            if (p <= 0 || p >= 1) continue;
            c.ring(o.add(0, p * 2.6, 0), 0.75F - p * 0.25F, 0.07F, hue(k / 4F), 0.9F * Mth.sin(p * Mth.PI));
        }
        c.glow(o.add(0, 1, 0), 1.5F, WHITE, 0.25F * (1 - t / life));
    }

    // ------------------------------------------------------------------
    // Technopathy
    // ------------------------------------------------------------------

    private static Vec3 drone(Vec3 o, int i, float t) {
        float ang = i * Mth.TWO_PI / 6 + t * 0.09F * (i % 2 == 0 ? 1 : -1), rr = 1.6F + (i % 3) * 0.3F;
        return o.add(Mth.cos(ang) * rr, 1.7 + (i % 2) * 0.5 + Mth.sin(t * 0.2F + i) * 0.12, Mth.sin(ang) * rr);
    }

    /** Six little drones of light wheel round them, trailing. */
    static void drones(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 8, 14);
        Vec3 o = a.at(c);
        for (int i = 0; i < 6; i++) {
            Vec3 p = drone(o, i, t);
            Vec3 ahead = drone(o, i, t + 1).subtract(p).normalize().scale(0.22);
            Vec3 side = ahead.cross(UP).normalize().scale(0.13);
            c.tri(p.add(ahead), p.subtract(ahead.scale(0.6)).add(side), p.subtract(ahead.scale(0.6)).subtract(side), HOLO, f, 0.6F * f, 0.6F * f);
            c.glow(p, 0.3F, HOLO, 0.5F * f);
            Vec3[] trail = new Vec3[6];
            for (int k = 0; k < 6; k++) trail[k] = drone(o, i, t - k * 0.8F);
            c.ribbon(trail, 0.04F, HOLO, 0.6F * f, 0);
        }
    }

    /** One drone's shot: a thin line of light and a spark where it lands. */
    static void droneShot(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = 1 - t / a.piece().ticks;
        c.line(a.origin(), a.target(), 0.05F, HOLO, f, f);
        c.glow(a.target(), 0.6F, WHITE, f);
    }

    /** A hologram wall stands before them: a lit grid, glyphs scrolling down it, a hexagon frame. */
    static void firewall(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 6, 14);
        float flicker = t < 8 && hash(a.seed(), (int) t) < 0.4F ? 0.3F : 1;
        Vec3 d = a.facing(), side = new Vec3(-d.z, 0, d.x);
        Vec3 centre = a.origin().add(d.scale(2.5)).add(0, 1.5, 0);
        float w = 2.5F, h = 1.5F, al = f * flicker;
        for (int i = 0; i <= 10; i++) {
            float u = -w + i * w * 2 / 10;
            c.line(centre.add(side.scale(u)).add(0, -h, 0), centre.add(side.scale(u)).add(0, h, 0), 0.025F, HOLO, 0.5F * al, 0.5F * al);
        }
        for (int j = 0; j <= 6; j++) {
            float v = -h + j * h * 2 / 6;
            c.line(centre.add(side.scale(-w)).add(0, v, 0), centre.add(side.scale(w)).add(0, v, 0), 0.025F, HOLO, 0.5F * al, 0.5F * al);
        }
        for (int col = 0; col < 12; col++) {
            float u = -w + (col + 0.5F) * w * 2 / 12, speed = 0.04F + hash(a.seed(), col) * 0.05F;
            for (int g = 0; g < 3; g++) {
                float v = h - ((t * speed + g / 3F + hash(a.seed(), col + 20)) % 1) * h * 2;
                c.line(centre.add(side.scale(u)).add(0, v, 0), centre.add(side.scale(u)).add(0, v - 0.18, 0), 0.04F, WHITE, 0.8F * al, 0.2F * al);
            }
        }
        Vec3[] frame = {centre.add(side.scale(-w)).add(0, -h, 0), centre.add(side.scale(w)).add(0, -h, 0), centre.add(side.scale(w)).add(0, h, 0),
                centre.add(side.scale(-w)).add(0, h, 0), centre.add(side.scale(-w)).add(0, -h, 0)};
        c.ribbon(frame, 0.07F, HOLO, al, al);
        c.ribbon(frame, 0.3F, HOLO_DEEP, 0.4F * al, 0.4F * al);
    }

    /** Rings of data turn round a friend, segment by segment, numbers rising off them. */
    static void overclock(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 6, 16);
        Vec3 o = a.at(c).add(0, 1, 0);
        for (int k = 0; k < 2; k++) {
            Vec3 axis = new Vec3(Mth.sin(k * 1.3F + 0.4F), 1, Mth.cos(k * 1.3F)).normalize();
            float spin = t * (k == 0 ? 0.12F : -0.09F);
            for (int s = 0; s < 6; s++) c.ring(o, axis, 0.95F + k * 0.15F, 0.04F, spin + s * Mth.TWO_PI / 6, spin + s * Mth.TWO_PI / 6 + 0.7F, HOLO, 0.85F * f);
        }
        for (int i = 0; i < 8; i++) {
            float k = (t * 0.03F + hash(a.seed(), i)) % 1, ang = hash(a.seed(), i + 10) * Mth.TWO_PI;
            Vec3 p = o.add(Mth.cos(ang) * 0.7, -0.8 + k * 2.6, Mth.sin(ang) * 0.7);
            c.line(p, p.add(0, 0.12, 0), 0.03F, WHITE, f * (1 - k), f * (1 - k));
        }
    }

    // ------------------------------------------------------------------
    // Matter Assembly
    // ------------------------------------------------------------------

    private static Vec3 muzzleDir(Active a) {
        Vec3 d = a.target().subtract(a.origin().add(0, 1.6, 0));
        return d.lengthSqr() < 1e-4 ? a.facing() : d.normalize();
    }

    /** Shards fly in and lock into a barrel over their shoulder; its muzzle flashes with every shot. */
    static void cannon(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 2, 8);
        Vec3 dir = muzzleDir(a), side = dir.cross(UP);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        Vec3 base = a.at(c).add(0, 1.9, 0).add(side.scale(0.7)).add(up.scale(0.15));
        float lock = smooth(0, 7, t), apart = Mth.clamp((t - (life - 8)) / 8, 0, 1);
        for (int i = 0; i < 8; i++) {
            float ang = i * Mth.TWO_PI / 8;
            Vec3 offset = side.scale(Mth.cos(ang) * 0.22).add(up.scale(Mth.sin(ang) * 0.22));
            Vec3 scatter = offset.normalize().scale((1 - lock) * 2.5 + apart * 1.5);
            Vec3 p0 = base.add(offset).add(scatter), p1 = p0.add(dir.scale(1.2));
            if (c.solid() && f > 0.2F) c.shard(p0, p1, 0.09F, 4, ang, CRYSTAL, 1);
        }
        if (c.glow()) {
            Vec3 muzzle = base.add(dir.scale(1.3));
            for (int shot = 0; shot < 5; shot++) {
                float s = t - (8 + shot * 5);
                if (s >= 0 && s < 3) c.glow(muzzle, 0.9F, WHITE, 1 - s / 3);
            }
            c.ring(muzzle, dir, 0.3F, 0.04F, 0, Mth.TWO_PI, CRYSTAL_EDGE, 0.6F * f * lock);
        }
    }

    /** One crystal slug, out of the barrel and into the target. */
    static void slug(Canvas c, Active a, float t) {
        float k = Mth.clamp(t / 3, 0, 1);
        Vec3 from = a.origin(), to = a.target(), p = from.lerp(to, k), d = to.subtract(from).normalize();
        if (k < 1 && c.solid()) c.shard(p.subtract(d.scale(0.3)), p.add(d.scale(0.3)), 0.1F, 4, t, CRYSTAL, 1);
        if (c.glow()) {
            c.line(from.lerp(to, Math.max(0, k - 0.4F)), p, 0.05F, CRYSTAL_EDGE, 0, 0.9F);
            if (k >= 1) {
                float s = (t - 3) / 5;
                for (int i = 0; i < 6; i++) {
                    float ang = i * Mth.TWO_PI / 6 + a.seed();
                    c.line(to, to.add(Mth.cos(ang) * s, Mth.sin(ang * 2) * s * 0.6, Mth.sin(ang) * s), 0.04F, CRYSTAL_EDGE, 1 - s, 0);
                }
            }
        }
    }

    /** Crystal panels fly in and lock into a shell round them, turning slowly, their edges lit. */
    static void lattice(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 2, 12);
        Vec3 o = a.at(c).add(0, 1, 0);
        float g = (1 + Mth.sqrt(5)) / 2, r = 1.55F, spin = t * 0.015F, lock = smooth(0, 12, t);
        float[][] base = {{-1, g, 0}, {1, g, 0}, {-1, -g, 0}, {1, -g, 0}, {0, -1, g}, {0, 1, g}, {0, -1, -g}, {0, 1, -g},
                {g, 0, -1}, {g, 0, 1}, {-g, 0, -1}, {-g, 0, 1}};
        int[][] faces = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        Vec3[] v = new Vec3[12];
        float cs = Mth.cos(spin), sn = Mth.sin(spin);
        for (int i = 0; i < 12; i++) {
            float l = Mth.sqrt(base[i][0] * base[i][0] + base[i][1] * base[i][1] + base[i][2] * base[i][2]);
            float x = base[i][0] / l, z = base[i][2] / l;
            v[i] = new Vec3((x * cs - z * sn) * r, base[i][1] / l * r * 1.2, (x * sn + z * cs) * r);
        }
        for (int i = 0; i < faces.length; i++) {
            Vec3 mid = v[faces[i][0]].add(v[faces[i][1]]).add(v[faces[i][2]]).scale(1 / 3.0);
            float k = Mth.clamp(lock * 1.4F - hash(a.seed(), i) * 0.4F, 0, 1);
            Vec3 push = mid.normalize().scale((1 - k) * 3);
            Vec3 a0 = o.add(v[faces[i][0]].subtract(mid).scale(0.92).add(mid)).add(push), a1 = o.add(v[faces[i][1]].subtract(mid).scale(0.92).add(mid)).add(push),
                    a2 = o.add(v[faces[i][2]].subtract(mid).scale(0.92).add(mid)).add(push);
            if (c.soft()) c.tri(a0, a1, a2, CRYSTAL, 0.18F * f, 0.18F * f, 0.18F * f);
            if (c.glow()) c.ribbon(new Vec3[] {a0, a1, a2, a0}, 0.03F, CRYSTAL_EDGE, 0.7F * f * k, 0.7F * f * k);
        }
    }

    /** Lines of crystal lace wind up round a friend into a lattice, then fade. */
    static void mending(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 6, 20), grow = smooth(0, 20, t);
        Vec3 o = a.at(c);
        for (int k = 0; k < 4; k++) {
            c.helix(o, 2.1F * grow, 0.7F, 0.6F, 1, k * Mth.HALF_PI, 0.04F, LATTICE, 0.8F * f, 20);
            c.helix(o, 2.1F * grow, 0.7F, 0.6F, -1, k * Mth.HALF_PI, 0.04F, CRYSTAL_EDGE, 0.8F * f, 20);
        }
        c.glow(o.add(0, 1, 0), 1.4F, LATTICE, 0.25F * f);
    }
}
