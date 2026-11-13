package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/** Time, telekinesis, speed, density and invisibility. */
final class MotionArt {
    private MotionArt() {}

    private static final float[] GOLD = rgb(0xFFD27A), VIOLET = rgb(0x9A7CFF), DEEP = rgb(0x5B3FD1), PALE = rgb(0xF4EBFF), AMBER = rgb(0xE8A43C);
    private static final float[] PSI = rgb(0xB48CFF), PSI_DEEP = rgb(0x6A3FD8), ROCK = rgb(0x7D7268), ROCK_DARK = rgb(0x5A5048);
    private static final float[] BOLT = rgb(0xFFE14A), BOLT_RED = rgb(0xFF5A2A), WHITE = {1, 1, 1};
    private static final float[] GHOST = rgb(0x7FF4FF), DENSE = rgb(0x2C3E5A), DENSE_RIM = rgb(0x8FB4E8);
    private static final float[] GLASS = rgb(0xE8F4FF);

    /** The clock angle {@code a} (0 straight ahead, turning clockwise seen from above) as a ring angle about UP. */
    private static float ringAngle(Vec3 facing, float clock) { return (float) Math.atan2(facing.x, facing.z) - clock; }

    private static Vec3 around(Vec3 at, float ringAngle, float radius, double up) {
        return at.add(Mth.sin(ringAngle) * radius, up, Mth.cos(ringAngle) * radius);
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    /** A clock face opens round them and its hour hand, a blade of gold light, sweeps once all the way round. */
    static void hourHand(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, r = a.scale(), f = fade(t, life, 3, 10);
        Vec3 o = a.origin().add(0, 0.05, 0), f0 = a.facing();
        float open = smooth(0, 4, t);
        c.ring(o, r * open, 0.08F, GOLD, 0.95F * f);
        c.ring(o, r * open, 0.4F, VIOLET, 0.3F * f);
        c.ring(o, r * 0.86F * open, 0.04F, GOLD, 0.7F * f);
        for (int i = 0; i < 12; i++) c.spoke(o, UP, i * Mth.TWO_PI / 12, r * (i % 3 == 0 ? 0.64F : 0.74F) * open, r * 0.84F * open, i % 3 == 0 ? 0.08F : 0.05F, i % 3 == 0 ? PALE : GOLD, 0.9F * f);
        float sweep = Mth.TWO_PI * smooth(4, 20, t);
        float blade = ringAngle(f0, sweep);
        Vec3 hub = o.add(0, 1.0, 0);
        c.ring(hub, UP, r * 0.62F, r * 0.38F, blade, blade + Math.min(1.4F, sweep), GOLD, 0.35F * f * (t < 22 ? 1 : 0.5F));
        Vec3 tip = around(hub, blade, r, 0);
        c.line(around(hub, blade, 0.4F, 0), tip, 0.32F, GOLD, 0.95F * f, 0.6F * f);
        c.line(around(hub, blade, 0.4F, 0), tip, 0.1F, WHITE, 0.9F * f, 0.3F * f);
        c.glow(hub, 0.8F, PALE, 0.8F * f);
        c.disc(o, UP, r * open, DEEP, 0.18F * f, 0.02F * f);
    }

    /** Afterimages of them circle as the loop runs, over a spiral winding in; when it closes everything folds back into them. */
    static void timeLoop(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 6, 4), close = smooth(life - 12, life, t);
        Vec3 o = a.at(c);
        if (c.soft()) for (int k = 0; k < 3; k++) {
            float ang = k * Mth.TWO_PI / 3 + t * 0.09F, rr = 1.7F * (1 - close);
            Vec3 p = o.add(Mth.cos(ang) * rr, 0.95, Mth.sin(ang) * rr);
            c.shell(p, 0.33F, 0.95F, 0.33F, Mth.PI, VIOLET, 0.32F * f, 0.2F, PLAIN, 0);
        }
        if (!c.glow()) return;
        Vec3 floor = o.add(0, 0.05, 0);
        int steps = 90;
        Vec3[] spiral = new Vec3[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float k = i / (float) steps, ang = k * 5 * Mth.TWO_PI - t * 0.12F;
            float rr = 2.6F * (float) Math.exp(-k * 2.2F) * (1 - close * 0.9F);
            spiral[i] = floor.add(Mth.cos(ang) * rr, 0, Mth.sin(ang) * rr);
        }
        c.ribbon(spiral, 0.07F, GOLD, 0.9F * f, 0.2F * f);
        for (int k = 0; k < 3; k++) {
            float ang = k * Mth.TWO_PI / 3 + t * 0.09F, rr = 1.7F * (1 - close);
            Vec3 p = o.add(Mth.cos(ang) * rr, 0.95, Mth.sin(ang) * rr);
            c.line(p.add(0, -0.9, 0), p.add(0, 0.9, 0), 0.12F, PSI, 0.4F * f, 0.1F * f);
            c.line(o.add(0, 1, 0), p, 0.03F, GOLD, 0.3F * f, 0.05F * f);
        }
        c.ring(floor, 2.6F * (1 - close), 0.07F, GOLD, 0.6F * f);
        if (close > 0) {
            c.glow(o.add(0, 1, 0), 2.5F * close, PALE, close);
            c.ring(o.add(0, 1, 0), 3 * (1 - close), 0.15F, GOLD, close);
        }
    }

    /** An hourglass of amber light over a friend's head, its sand running upward, a ring turning at their feet. */
    static void borrowedTime(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 8, 16);
        Vec3 o = a.at(c), top = o.add(0, 2.55, 0);
        float w = 0.32F, h = 0.38F;
        Vec3 tl = top.add(-w, h, 0), tr = top.add(w, h, 0), bl = top.add(-w, -h, 0), br = top.add(w, -h, 0);
        c.ribbon(new Vec3[] {tl, tr, bl, br, tl}, 0.04F, AMBER, 0.9F * f, 0.9F * f);
        c.glow(top, 0.5F, AMBER, 0.5F * f);
        for (int i = 0; i < 6; i++) {
            float k = ((t * 0.06F + i / 6F) % 1);
            c.glow(top.add(0, -h + k * 2 * h, 0), 0.06F, GOLD, f);
        }
        float spin = t * 0.08F;
        c.ring(o.add(0, 0.05, 0), UP, 0.9F, 0.05F, spin, spin + Mth.PI * 1.4F, AMBER, 0.8F * f);
        for (int i = 0; i < 12; i++) c.spoke(o.add(0, 0.05, 0), UP, i * Mth.TWO_PI / 12 + spin, 0.95F, 1.1F, 0.03F, GOLD, 0.7F * f);
    }

    // ------------------------------------------------------------------
    // Telekinesis
    // ------------------------------------------------------------------

    /** Space folds in on the point: rings of warped light close, rubble spirals in, then it all slams down. */
    static void implosion(Canvas c, Active a, float t) {
        float pull = 22, r = a.scale();
        Vec3 o = a.target().add(0, 1.2, 0);
        float in = Mth.clamp(t / pull, 0, 1);
        if (t < pull + 2) {
            if (c.solid()) for (int i = 0; i < 18; i++) {
                float k = Mth.clamp(in * (1 + hash(a.seed(), i) * 0.5F), 0, 1), ang = hash(a.seed(), i + 9) * Mth.TWO_PI + k * 4;
                float rr = Mth.lerp(k * k, r * (0.7F + 0.3F * hash(a.seed(), i + 3)), 0.5F), h = (hash(a.seed(), i + 5) - 0.3F) * 2 * (1 - k);
                c.box(o.add(Mth.cos(ang) * rr, h, Mth.sin(ang) * rr), 0.18F, 0.15F, 0.2F, ang * 2, k * 6 + i, i, i % 2 == 0 ? ROCK : ROCK_DARK, 1);
            }
            if (c.glow()) for (int k = 0; k < 3; k++) {
                float p = ((in * 2 + k / 3F) % 1);
                c.shell(o, r * (1 - p), r * (1 - p) * 0.8F, r * (1 - p), Mth.PI, PSI, 0.25F * p, 0.05F, PLAIN, 0);
                c.ring(o, UP, r * (1 - p), 0.1F, 0, Mth.TWO_PI, PSI_DEEP, 0.6F * p);
            }
            if (c.glow()) c.glow(o, 0.6F + in * 1.5F, PSI, 0.8F);
            return;
        }
        float s = t - pull;
        if (c.glow()) {
            if (s < 5) c.glow(o, 4, WHITE, 1 - s / 5);
            float p = Mth.clamp(s / 14, 0, 1);
            c.ring(a.target().add(0, 0.08, 0), r * 1.2F * p, 0.35F, PSI, 0.9F * (1 - p));
        }
        if (c.soft()) {
            float p = Mth.clamp(s / 20, 0, 1);
            for (int i = 0; i < 8; i++) {
                float ang = i * Mth.TWO_PI / 8;
                c.cloud(a.target().add(Mth.cos(ang) * r * p * 0.8, 0.4, Mth.sin(ang) * r * p * 0.8), 0.8F, 0.6F, 0.8F, a.seed() + i, i,
                        0.5F * (1 - p), Shades.smoke(c, ROCK, 0));
            }
        }
    }

    /** Two rings of rubble whirl round them in opposite directions, streaking. */
    static void debrisHalo(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 8, 14);
        Vec3 o = a.at(c);
        for (int ring = 0; ring < 2; ring++) {
            float rr = ring == 0 ? 2.0F : 2.6F, h = ring == 0 ? 0.8F : 1.6F, dir = ring == 0 ? 1 : -1;
            int n = ring == 0 ? 7 : 9;
            for (int i = 0; i < n; i++) {
                float ang = i * Mth.TWO_PI / n + dir * t * 0.22F;
                Vec3 p = o.add(Mth.cos(ang) * rr * f, h + Mth.sin(t * 0.1F + i) * 0.15, Mth.sin(ang) * rr * f);
                if (c.solid()) c.box(p, 0.2F, 0.17F, 0.22F, ang * 3, t * 0.2F + i, i, (i + ring) % 2 == 0 ? ROCK : ROCK_DARK, 1);
                if (c.soft()) c.ring(o.add(0, h, 0), UP, rr * f, 0.12F, -ang + (dir > 0 ? 0 : -0.6F), -ang + (dir > 0 ? 0.6F : 0), PALE, 0.25F * f);
            }
            if (c.glow()) c.ring(o.add(0, h, 0), rr * f, 0.05F, PSI, 0.35F * f);
        }
    }

    /** A tether of will hauls a friend in, and a bubble of force closes round them when they arrive. */
    static void gathering(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks;
        Vec3 friend = a.at(c).add(0, 1, 0), caster = a.target().add(0, 1.2, 0);
        if (t < 12) {
            float f = 1 - t / 12;
            c.bolt(caster, friend, a.seed() + (int) (t / 3), 0.2F, 0.08F, PSI, 0.9F * f);
            c.line(caster, friend, 0.3F, PSI_DEEP, 0.3F * f, 0.3F * f);
        }
        float bubble = fade(t - 8, life - 8, 4, 10);
        if (t > 8) {
            c.shell(friend, 1.1F, 1.25F, 1.1F, Mth.PI, PSI, 0.45F * bubble, 0.05F, hexes(5, 0.06F, 0.3F), t * 0.02F);
            c.glow(friend, 1.6F, PSI_DEEP, 0.25F * bubble);
        }
    }

    // ------------------------------------------------------------------
    // Speed
    // ------------------------------------------------------------------

    /** One leg of the flurry: a crackling streak from where they were to where they struck. */
    static void flurry(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = 1 - t / a.piece().ticks;
        Vec3 from = a.origin().add(0, 1, 0), to = a.target().add(0, 1, 0);
        c.bolt(from, to, a.seed() + (int) (t / 2), 0.35F, 0.12F, BOLT, f);
        c.line(from, to, 0.4F, BOLT_RED, 0.2F * f, 0.35F * f);
        c.glow(to, 1.4F * f, BOLT, f);
        if (t < 6) for (int k = 0; k < 6; k++) {
            float ang = k * Mth.TWO_PI / 6 + a.seed();
            c.line(to, to.add(Mth.cos(ang) * 1.2, Mth.sin(ang * 1.3F) * 0.8, Mth.sin(ang) * 1.2), 0.06F, WHITE, 1 - t / 6, 0);
        }
    }

    /**
     * Relay Run: a friend is fetched. A gold ribbon arcs from where they stood to their place in the ring round the
     * runner, their outline is left fading where they were, and a ring closes round them as they land.
     */
    static void relay(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 1, 8);
        Vec3 from = a.origin(), to = a.target();
        Vec3[] arc = new Vec3[13];
        for (int i = 0; i < arc.length; i++) {
            float u = i / 12F;
            arc[i] = from.lerp(to, u).add(0, .9 + Mth.sin(Mth.PI * u) * .9, 0);
        }
        float drawn = smooth(0, 3, t);
        int shown = Math.max(2, (int) (drawn * arc.length));
        Vec3[] part = java.util.Arrays.copyOf(arc, shown);
        if (c.soft()) {
            c.ribbon(part, .3F, BOLT_RED, .25F * f, .1F * f);
            c.shell(from.add(0, .95, 0), .3F, .92F, .3F, Mth.PI, BOLT, .3F * f * (1 - smooth(2, 10, t)), .2F, PLAIN, 0);
        }
        if (c.glow()) {
            c.ribbon(part, .12F, BOLT, .7F * f, .3F * f);
            c.ribbon(part, .04F, WHITE, f, .5F * f);
            if (drawn > .99F) {
                float close = smooth(3, 9, t);
                c.ring(to.add(0, .06, 0), new Vec3(0, 1, 0), 1.1F - .6F * close, .07F, 0, Mth.TWO_PI, BOLT, .8F * f);
                c.glow(to.add(0, 1, 0), .9F * (1 - close) + .1F, WHITE, f);
            }
        }
    }

    /** They blur: several of them jitter in and out round where they stand, crackling. */
    static void phaseBlur(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 6, 12);
        Vec3 o = a.at(c);
        int flick = (int) t;
        if (c.soft()) for (int k = 0; k < 3; k++) {
            float dx = (hash(a.seed() + flick, k) - 0.5F) * 0.9F, dz = (hash(a.seed() + flick, k + 5) - 0.5F) * 0.9F;
            c.shell(o.add(dx, 0.95, dz), 0.32F, 0.95F, 0.32F, Mth.PI, k == 0 ? BOLT_RED : BOLT, 0.25F * f, 0.2F, PLAIN, 0);
        }
        if (c.glow()) {
            for (int k = 0; k < 3; k++) {
                float ang = hash(a.seed() + flick, k + 9) * Mth.TWO_PI;
                Vec3 p = o.add(Mth.cos(ang) * 0.6, 0.3 + hash(a.seed() + flick, k + 11) * 1.6, Mth.sin(ang) * 0.6);
                c.bolt(p, p.add(Mth.cos(ang) * 0.5, 0.4, Mth.sin(ang) * 0.5), a.seed() + flick * 7 + k, 0.15F, 0.03F, BOLT, 0.9F * f);
            }
            c.ring(o.add(0, 0.06, 0), 1 + (t % 6) / 6 * 0.6F, 0.06F, BOLT, 0.5F * f);
        }
    }

    // ------------------------------------------------------------------
    // Density
    // ------------------------------------------------------------------

    /** Ripples where they sink, then the ground bursts upward under the point in rings of ghost-light and flung rock. */
    static void eruption(Canvas c, Active a, float t) {
        Vec3 from = a.origin().add(0, 0.05, 0), to = a.target().add(0, 0.05, 0);
        if (c.glow() && t < 12) for (int k = 0; k < 3; k++) {
            float p = Mth.clamp((t - k * 2) / 10, 0, 1);
            c.ring(from, 0.3F + p * 2, 0.08F, GHOST, 0.7F * (1 - p) * (p > 0 ? 1 : 0));
        }
        float s = t - 8;
        if (s < 0) return;
        if (c.glow()) {
            for (int k = 0; k < 4; k++) {
                float p = Mth.clamp((s - k * 2) / 12, 0, 1);
                if (p > 0) c.ring(to.add(0, p * 3.5, 0), 0.6F + p * a.scale(), 0.14F, GHOST, 0.8F * (1 - p));
            }
            if (s < 6) c.glow(to.add(0, 1, 0), 3, GHOST, 1 - s / 6);
            c.line(to, to.add(0, 4 * Mth.clamp(s / 4, 0, 1), 0), 0.6F, GHOST, 0.5F * Mth.clamp(1 - s / 20, 0, 1), 0);
        }
        if (c.solid()) for (int i = 0; i < 12; i++) {
            float ang = hash(a.seed(), i) * Mth.TWO_PI, v = 0.4F + hash(a.seed(), i + 4) * 0.4F, out = 0.12F + hash(a.seed(), i + 8) * 0.15F;
            double y = v * s - 0.04 * s * s;
            if (y < -0.2) continue;
            c.box(to.add(Mth.cos(ang) * out * s, y, Mth.sin(ang) * out * s), 0.16F, 0.14F, 0.18F, ang, s * 0.3F, i, i % 2 == 0 ? ROCK : ROCK_DARK, 1);
        }
    }

    /** A dark, heavy sheen closes over them and cracks run out through the ground under their weight. */
    static void dense(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 8, 14);
        Vec3 o = a.at(c);
        if (c.soft()) {
            c.shell(o.add(0, 0.95, 0), 0.55F, 1.1F, 0.55F, Mth.PI, DENSE, 0.45F * f, 0.15F, PLAIN, 0);
            for (int i = 0; i < 9; i++) {
                float ang = hash(a.seed(), i) * Mth.TWO_PI, len = (1.4F + hash(a.seed(), i + 3) * 1.6F) * smooth(0, 10, t);
                Vec3[] p = new Vec3[5];
                for (int k = 0; k <= 4; k++) {
                    float kk = k / 4F, wob = (hash(a.seed(), i * 5 + k) - 0.5F) * 0.5F;
                    p[k] = o.add(Mth.cos(ang + wob) * len * kk, 0.04, Mth.sin(ang + wob) * len * kk);
                }
                c.ribbon(p, 0.07F, DENSE, 0.8F * f, 0.2F * f);
            }
        }
        if (c.glow()) {
            c.shell(o.add(0, 0.95, 0), 0.6F, 1.15F, 0.6F, Mth.PI, DENSE_RIM, 0.4F * f, 0, PLAIN, 0);
            float p = (t % 20) / 20;
            c.ring(o.add(0, 0.06, 0), 0.6F + p * 1.6F, 0.08F, DENSE_RIM, 0.5F * f * (1 - p));
        }
    }

    /** A friend wavers like a ghost: a tube of scanlines sliding up round them. */
    static void phaseField(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 6, 14);
        Vec3 o = a.at(c);
        Pattern lines = (theta, h) -> 0.15F + 0.85F * Math.max(0, Mth.sin((h * 12 - t * 0.15F) * Mth.TWO_PI));
        c.tube(o, 0.62F, 2.1F, GHOST, 0.9F * f, 0.3F * f, lines, t * 0.05F);
        c.glow(o.add(0, 1, 0), 1.3F, GHOST, 0.25F * f);
        c.ring(o.add(0, 0.06, 0), 0.7F, 0.06F, GHOST, 0.7F * f);
    }

    // ------------------------------------------------------------------
    // Invisibility
    // ------------------------------------------------------------------

    /** Out of nowhere: a ripple in the air, then a slash split into its colours like light through a prism. */
    static void shadowStrike(Canvas c, Active a, float t) {
        float f = 1 - t / a.piece().ticks;
        Vec3 at = a.target().add(0, 1.1, 0), d = a.facing();
        if (c.soft()) c.ring(at, d, 0.3F + t * 0.12F, 0.12F, 0, Mth.TWO_PI, GLASS, 0.5F * f);
        if (!c.glow()) return;
        Vec3 side = new Vec3(-d.z, 0, d.x);
        for (int k = 0; k < 3; k++) {
            float[] col = k == 0 ? new float[] {1, 0.25F, 0.3F} : k == 1 ? new float[] {0.3F, 1, 0.45F} : new float[] {0.35F, 0.5F, 1};
            Vec3[] arc = new Vec3[13];
            for (int i = 0; i <= 12; i++) {
                float u = i / 12F - 0.5F;
                arc[i] = at.add(side.scale(u * 2.2)).add(0, -u * 1.4 + k * 0.05, 0).add(d.scale(-0.3 + Mth.cos(u * 2.5F) * 0.4 + k * 0.04));
            }
            c.ribbon(arc, 0.08F, col, 0.9F * f, 0.9F * f);
        }
        c.glow(at, 0.8F, WHITE, 0.6F * f);
    }

    /** Light bends round them: a lens of air with colour fringes at its rim. */
    static void refraction(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 8, 14);
        Vec3 o = a.at(c).add(0, 1, 0);
        float wob = 1 + 0.03F * Mth.sin(t * 0.3F);
        if (c.soft()) c.shell(o, 1.2F * wob, 1.45F * wob, 1.2F * wob, Mth.PI, GLASS, 0.12F * f, 0, PLAIN, t * 0.02F);
        if (c.glow()) {
            c.shell(o, 1.22F * wob, 1.47F * wob, 1.22F * wob, Mth.PI, new float[] {1, 0.2F, 0.2F}, 0.22F * f, 0, PLAIN, 0);
            c.shell(o, 1.28F * wob, 1.53F * wob, 1.28F * wob, Mth.PI, new float[] {0.2F, 1, 0.3F}, 0.18F * f, 0, PLAIN, 0);
            c.shell(o, 1.34F * wob, 1.59F * wob, 1.34F * wob, Mth.PI, new float[] {0.25F, 0.4F, 1}, 0.22F * f, 0, PLAIN, 0);
        }
    }

    /** A haze of broken light settles over the ground, rainbow caustics wandering through it. */
    static void prismHaze(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 16, 30), r = a.scale();
        Vec3 o = a.origin();
        if (c.soft()) for (int i = 0; i < 14; i++) {
            float ang = i * 2.39996F + t * 0.004F, rr = r * 0.85F * Mth.sqrt((i + 0.5F) / 14);
            float size = 1.6F + hash(a.seed(), i) * 0.8F;
            c.cloud(o.add(Mth.cos(ang) * rr, 0.9 + Mth.sin(t * 0.03F + i) * 0.2, Mth.sin(ang) * rr), size, size * 0.6F, size, a.seed() + i,
                    t * 0.01F + i, 0.16F * f, Shades.flat(hue(hash(a.seed(), i + 30) * 0.3F + 0.55F)));
        }
        if (!c.glow()) return;
        for (int k = 0; k < 5; k++) {
            Vec3[] p = new Vec3[33];
            float base = 0.3F + k * 0.45F;
            for (int i = 0; i <= 32; i++) {
                float ang = i * Mth.TWO_PI / 32, rr = r * (0.3F + 0.14F * k) + Mth.sin(ang * 3 + t * 0.07F + k) * 0.6F;
                p[i] = o.add(Mth.cos(ang) * rr, base + Mth.sin(ang * 2 + t * 0.05F) * 0.3, Mth.sin(ang) * rr);
            }
            c.ribbon(p, 0.08F, hue(k / 5F + t * 0.004F), 0.35F * f, 0.35F * f);
        }
        c.ring(o.add(0, 0.06, 0), r, 0.1F, GLASS, 0.35F * f);
    }
}
