package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.client.effects.intro.CloudMesh;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * Induced Radioactivity drawn as heat and contamination. One idea runs through it: a reactor's light. Heat is
 * orange going white-hot (the cone, the beam, the blasts), sickness and contaminated ground are a sickly
 * yellow-green, the EMP is a cold electric blue. Every blast is the same shape at a different size: a white
 * flash, a hot shock ring racing over the ground, a green ring following it, smoke and grit thrown up.
 */
final class RadiationArt {
    private static final float[] EMBER = rgb(0xFF4A0C), HEAT = rgb(0xFF9330), WHITE_HOT = rgb(0xFFF2D2),
            TOXIC = rgb(0xC4F23C), TOXIC_DEEP = rgb(0x5FAE1E), EMP = rgb(0x9ADCFF), EMP_PALE = rgb(0xE6F6FF),
            SMOKE = {0.33F, 0.3F, 0.27F}, DUST = {0.62F, 0.56F, 0.48F};
    private static final float[][] DEBRIS = {{0.42F, 0.3F, 0.2F}, {0.5F, 0.5F, 0.5F}, {0.36F, 0.36F, 0.34F}, {0.2F, 0.18F, 0.16F}};

    private RadiationArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case RAD_HEAT -> heat(c, a, t);
            case RAD_LEAP -> blast(c, a.origin(), a.scale(), t, a.piece().ticks, a.seed(), false);
            case RAD_SLAM -> blast(c, a.origin(), a.scale(), t, a.piece().ticks, a.seed(), true);
            case RAD_ZONE -> zone(c, a, t);
            case RAD_EMP -> emp(c, a, t);
            case RAD_BEAM -> beam(c, a, t);
            case RAD_CHARGE -> charge(c, a, t);
            case RAD_LANCE -> lance(c, a, t);
            default -> { }
        }
    }

    private static float now(Canvas c, float t) { return c.level == null ? t : (c.level.getGameTime() % 24000L) + c.partial; }

    /** Two directions across the plane whose normal is {@code n}. */
    private static Vec3[] across(Vec3 n) {
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0).cross(n) : UP.cross(n);
        u = u.normalize();
        return new Vec3[] {u, n.cross(u).normalize()};
    }

    /** Fast out, slow to settle. */
    private static float out(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }

    // ------------------------------------------------------------------
    // Radiant Heat: a cone of heat from the palms
    // ------------------------------------------------------------------

    private static void heat(Canvas c, Active a, float t) {
        Vec3 hand = a.origin(), span = a.target().subtract(hand);
        float length = (float) span.length();
        if (length < 0.1F) return;
        Vec3 dir = span.scale(1 / length);
        Vec3[] b = across(dir);
        float s = a.scale(), life = a.piece().ticks, f = Mth.clamp((life - t) / 6, 0, 1);
        int seed = a.seed();
        if (c.glow()) {
            // The palms flare white.
            c.glow(hand, (0.7F + 0.5F * s) * (1 - smooth(0, 5, t)) + 0.2F, WHITE_HOT, 0.9F * f);
            // Staggered rings of heat race out and widen into the cone.
            for (int i = 0; i < 6; i++) {
                float ti = t - i * 1.1F;
                if (ti <= 0) continue;
                float d = length * out(ti / 7), r = 0.15F + d * 0.4F, fade = (1 - smooth(4, 10, ti)) * f;
                c.ring(hand.add(dir.scale(d)), dir, r, 0.07F + d * 0.02F, 0, TWO_PI, i == 0 ? WHITE_HOT : HEAT, 0.75F * fade);
                c.ring(hand.add(dir.scale(d)), dir, r * 1.08F, 0.18F + d * 0.03F, 0, TWO_PI, EMBER, 0.25F * fade);
            }
            // Rays of white heat along the cone, flickering.
            for (int i = 0; i < 9; i++) {
                float ang = hash(seed, i) * TWO_PI, spread = 0.35F + 0.55F * hash(seed, i + 20);
                float reach = length * out(t / 5) * (0.75F + 0.25F * hash(seed, i + 40));
                Vec3 tip = hand.add(dir.scale(reach)).add(b[0].scale(cos(ang) * reach * spread * 0.4F)).add(b[1].scale(sin(ang) * reach * spread * 0.4F));
                c.line(hand.add(dir.scale(0.2)), tip, 0.05F * s, i % 3 == 0 ? WHITE_HOT : HEAT, 0.7F * f, 0);
            }
            // Embers carried on, cooling.
            for (int i = 0; i < 12; i++) {
                float go = out((t + hash(seed, i + 60) * 3) / 10) * length * (0.6F + 0.5F * hash(seed, i + 70));
                float ang = hash(seed, i + 80) * TWO_PI, r = go * 0.38F * hash(seed, i + 90);
                Vec3 p = hand.add(dir.scale(go)).add(b[0].scale(cos(ang) * r)).add(b[1].scale(sin(ang) * r)).add(0, t * 0.03, 0);
                c.glow(p, 0.12F, hash(seed, i) < 0.3F ? WHITE_HOT : EMBER, 0.8F * f);
            }
        }
        if (c.soft()) {
            // Heat haze: thin smoke peeling off the end of the cone.
            for (int i = 0; i < 4; i++) {
                float k = smooth(2, 14, t), d = length * (0.55F + 0.12F * i);
                float ang = hash(seed, i + 100) * TWO_PI, r = d * 0.3F;
                Vec3 p = hand.add(dir.scale(d)).add(b[0].scale(cos(ang) * r)).add(b[1].scale(sin(ang) * r)).add(0, k * 0.8, 0);
                c.cloud(p, 0.5F + k * 0.6F, 0.35F + k * 0.4F, 0.5F + k * 0.6F, i, ang, 0.22F * k * f, Shades.smoke(c, SMOKE, 0.3F * (1 - k)));
            }
        }
    }

    // ------------------------------------------------------------------
    // Fission Leap's launch, and its slam: one blast at two sizes
    // ------------------------------------------------------------------

    /**
     * A blast on the ground at {@code o}, {@code radius} blocks across: a white flash, a hot shock ring racing out
     * over the ground with a green ring of sickness behind it, a ball of fire boiling up into smoke, dust rolling out
     * and stones thrown. The launch adds a column of thrust up the path; the slam adds a green pool that lingers.
     */
    private static void blast(Canvas c, Vec3 o, float radius, float t, float life, int seed, boolean slam) {
        float f = Mth.clamp((life - t) / 8, 0, 1);
        Vec3 ground = o.add(0, 0.06, 0);
        float reach = radius * 1.25F;
        if (c.glow()) {
            if (t < 5) {
                c.glow(o.add(0, 0.8, 0), (slam ? 4.5F : 3) * (1 - t / 5) + 1, WHITE_HOT, 0.95F * (1 - t / 5));
                c.glow(o.add(0, 0.4, 0), (slam ? 9 : 6) * (0.4F + t / 8), HEAT, 0.45F * (1 - t / 5));
            }
            float r = reach * out(t / 6);
            float hot = 1 - smooth(3, 14, t);
            c.ring(ground, UP, r, 0.25F + radius * 0.05F, 0, TWO_PI, WHITE_HOT, 0.8F * hot);
            c.ring(ground, UP, r * 1.04F, 0.6F + radius * 0.08F, 0, TWO_PI, HEAT, 0.45F * hot);
            float g = reach * 0.85F * out((t - 2) / 9);
            if (t > 2) c.ring(ground.add(0, 0.03, 0), UP, g, 0.3F, 0, TWO_PI, TOXIC, 0.6F * (1 - smooth(10, life, t)));
            if (!slam) {
                // The thrust that throws the caster: a column of light up the launch path.
                Vec3 up = new Vec3(0, 1, 0);
                float k = 1 - smooth(0, 10, t);
                c.line(o, o.add(up.scale(2 + 7 * out(t / 4))), 0.5F, HEAT, 0.5F * k, 0);
                c.line(o, o.add(up.scale(2 + 7 * out(t / 4))), 0.15F, WHITE_HOT, 0.9F * k, 0);
            } else {
                // A short pillar of light where it struck.
                float k = 1 - smooth(0, 8, t);
                c.line(o, o.add(0, 3 + radius * 1.5 * out(t / 3), 0), 0.9F, HEAT, 0.4F * k, 0);
                c.line(o, o.add(0, 3 + radius * 1.5 * out(t / 3), 0), 0.25F, WHITE_HOT, 0.9F * k, 0);
                // The ground left glowing green.
                c.glow(o.add(0, 0.2, 0), radius * 0.9F, TOXIC, 0.18F * smooth(4, 10, t) * f);
            }
            // Cracks of heat running out over the ground.
            for (int i = 0; i < 7; i++) {
                float ang = (i + hash(seed, i)) * TWO_PI / 7, len = radius * (0.5F + 0.5F * hash(seed, i + 7)) * out(t / 4);
                Vec3 end = ground.add(cos(ang) * len, 0, sin(ang) * len);
                c.bolt(ground, end, seed + i, 0.25F, 0.035F, EMBER, 0.7F * (1 - smooth(6, 20, t)));
            }
        }
        if (c.soft()) {
            // The fireball: billows boiling up out of the blast, burning out into smoke.
            int balls = slam ? 7 : 4;
            for (int i = 0; i < balls; i++) {
                float ang = hash(seed, i + 30) * TWO_PI, spread = radius * 0.35F * hash(seed, i + 31);
                float rise = (slam ? 2.2F : 1.2F) * out(t / 14) * (0.6F + 0.8F * hash(seed, i + 32)) + t * 0.02F;
                float size = (slam ? 0.7F : 0.45F) * (0.7F + 0.6F * hash(seed, i + 33)) * (0.6F + 0.8F * out(t / 6));
                float heat = 0.8F * (float) Math.exp(-t / 4);
                Vec3 at = o.add(cos(ang) * spread, size * 0.6F + rise, sin(ang) * spread);
                CloudMesh.Shade shade = heat > 0.35F ? Shades.fire(heat) : Shades.smoke(c, SMOKE, heat);
                c.cloud(at, size, size * 0.85F, size, i, t * 0.03F + i, 0.7F * f, shade);
            }
            // Dust rolling out with the shock and settling.
            int around = slam ? 18 : 12;
            float roll = reach * (0.8F + 0.35F * out(t / 10));
            for (int i = 0; i < around; i++) {
                float ang = (i + hash(seed, i + 50) * 0.6F) * TWO_PI / around;
                float size = (0.45F + 0.4F * hash(seed, i + 51)) * (0.7F + 0.6F * smooth(0, 12, t)) * Math.max(1, radius / 4.5F);
                Vec3 at = o.add(cos(ang) * roll, size * 0.4F + t * 0.015F, sin(ang) * roll);
                c.cloud(at, size * 1.3F, size * 0.6F, size * 1.3F, 40 + i, ang + t * 0.02F,
                        0.3F * smooth(1, 4, t) * (1 - smooth(12, life, t)), Shades.smoke(c, DUST, 0));
            }
            if (slam) c.disc(o.add(0, 0.05, 0), UP, radius, TOXIC_DEEP, 0.3F * smooth(3, 8, t) * f, 0);
        }
        if (c.solid()) {
            // Stones and grit thrown out of it.
            for (int i = 0; i < (slam ? 12 : 7); i++) {
                float ang = hash(seed, i + 70) * TWO_PI, pace = 0.25F + 0.3F * hash(seed, i + 71);
                float d = pace * t * radius / 4, y = 0.2F + t * (0.25F + 0.2F * hash(seed, i + 72)) - t * t * 0.022F;
                if (y < 0.05F) y = 0.05F;
                Vec3 at = o.add(cos(ang) * d, y, sin(ang) * d);
                c.box(at, 0.12F, 0.1F, 0.12F, i, t * 0.4F + i, i, DEBRIS[i % DEBRIS.length], f > 0.05F ? 1 : 0);
            }
        }
    }

    // ------------------------------------------------------------------
    // Contaminated ground
    // ------------------------------------------------------------------

    /** A sickly glow on the ground with a slowly turning edge, motes rising and bubbles of light popping. */
    private static void zone(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = Mth.clamp(Math.min(t / 2, (life - t) / 2), 0, 1);
        if (f <= 0) return;
        Vec3 o = a.origin().add(0, 0.05, 0);
        float r = a.scale(), now = now(c, t);
        int seed = (int) Math.floor(o.x * 31 + o.z * 17);
        if (c.soft()) c.disc(o, UP, r, TOXIC_DEEP, 0.22F * f, 0.05F * f);
        if (c.glow()) {
            for (int k = 0; k < 3; k++) {
                float from = now * 0.03F + k * TWO_PI / 3;
                c.ring(o, UP, r, 0.09F, from, from + 1.6F, TOXIC, 0.65F * f);
            }
            for (int i = 0; i < 10; i++) {
                float cycle = (now * 0.03F + hash(seed, i)) % 1;
                float ang = hash(seed, i + 10) * TWO_PI, d = r * (float) Math.sqrt(hash(seed, i + 20));
                Vec3 p = o.add(cos(ang) * d, cycle * 2.2F, sin(ang) * d);
                c.glow(p, 0.13F, i % 3 == 0 ? WHITE_HOT : TOXIC, 0.75F * f * sin(cycle * Mth.PI));
            }
            for (int i = 0; i < 4; i++) {
                float cycle = (now * 0.05F + hash(seed, i + 40)) % 1;
                float ang = hash(seed, i + 50) * TWO_PI, d = r * 0.8F * hash(seed, i + 60);
                c.ring(o.add(cos(ang) * d, 0.02, sin(ang) * d), UP, 0.15F + cycle * 0.6F, 0.04F, 0, TWO_PI, TOXIC, 0.6F * f * (1 - cycle));
            }
        }
    }

    // ------------------------------------------------------------------
    // EMP
    // ------------------------------------------------------------------

    /** A cold blue shell bursting out, a ring racing over the ground to the pulse's edge, current crackling along it. */
    private static void emp(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        float reach = a.scale(), life = a.piece().ticks;
        int seed = a.seed();
        float r = reach * out(t / 7), fade = 1 - smooth(6, life, t);
        if (c.soft()) {
            float shell = Math.min(10, reach) * out(t / 5);
            c.shell(o.add(0, 0.9, 0), shell, shell * 0.8F, shell, Mth.PI, EMP_PALE, 0.32F * (1 - smooth(2, 12, t)), 0.12F, Canvas.PLAIN, 0);
        }
        if (c.glow()) {
            if (t < 4) c.glow(o.add(0, 1, 0), 2.5F + t, EMP_PALE, 0.9F * (1 - t / 4));
            c.ring(o.add(0, 0.06, 0), UP, r, 0.18F, 0, TWO_PI, EMP_PALE, 0.8F * fade);
            c.ring(o.add(0, 0.06, 0), UP, r * 1.03F, 0.5F, 0, TWO_PI, EMP, 0.35F * fade);
            // Current jumping along the ring.
            for (int i = 0; i < 10; i++) {
                float ang = (i + hash(seed, i)) * TWO_PI / 10, span = 0.35F + 0.25F * hash(seed, i + 10);
                Vec3 p0 = o.add(cos(ang) * r, 0.15 + hash(seed, i + 20) * 0.5, sin(ang) * r);
                Vec3 p1 = o.add(cos(ang + span) * r, 0.15 + hash(seed, i + 30) * 0.5, sin(ang + span) * r);
                if ((int) (t * 2 + i) % 3 != 0) c.bolt(p0, p1, seed + i + (int) t, 0.2F, 0.03F, i % 2 == 0 ? EMP_PALE : EMP, 0.8F * fade);
            }
            // Arcs reaching up from the caster in the first moments.
            for (int i = 0; i < 4 && t < 6; i++) {
                float ang = hash(seed, i + 40) * TWO_PI;
                c.bolt(o.add(0, 1.2, 0), o.add(cos(ang) * 1.1, 1.6 + hash(seed, i + 50) * 0.6, sin(ang) * 1.1), seed + i * 7 + (int) t, 0.2F, 0.025F,
                        EMP_PALE, 0.7F * (1 - t / 6));
            }
        }
    }

    // ------------------------------------------------------------------
    // Meltdown Beam
    // ------------------------------------------------------------------

    /** A thick beam of heat: a wide orange glow, a hot body and a white core, rings of heat running along it. */
    private static void beam(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target(), span = to.subtract(from);
        float length = (float) span.length();
        if (length < 0.1F) return;
        Vec3 dir = span.scale(1 / length);
        float s = a.scale(), now = now(c, t);
        // Re-sent every two ticks for four: two copies overlap, each drawn at about half strength.
        float f = 0.55F * Mth.clamp((a.piece().ticks - t) / 1.5F, 0, 1);
        if (c.glow()) {
            c.line(from, to, 0.5F * s, EMBER, 0.35F * f, 0.25F * f);
            c.line(from, to, 0.22F * s, HEAT, 0.9F * f, 0.7F * f);
            c.line(from, to, 0.07F * s, WHITE_HOT, 1F * f, 0.9F * f);
            for (int i = 0; i < 6; i++) {
                float d = (now * 1.8F + i * length / 6) % length;
                c.ring(from.add(dir.scale(d)), dir, 0.3F * s + 0.05F * (float) Math.sin(now + i), 0.06F, 0, TWO_PI, HEAT, 0.7F * f);
            }
            c.glow(from, 0.6F * s, WHITE_HOT, 0.8F * f);
            c.glow(to, 1.3F * s, HEAT, 0.7F * f);
            c.glow(to, 0.6F * s, WHITE_HOT, 0.9F * f);
        }
        if (c.soft()) {
            int seed = (int) now;
            float k = (now % 4) / 4;
            c.cloud(to.add(0, k * 0.8, 0), 0.4F + k * 0.4F, 0.3F + k * 0.3F, 0.4F + k * 0.4F, seed % 8, now * 0.1F, 0.35F * f * (1 - k), Shades.smoke(c, SMOKE, 0.2F));
        }
    }

    // ------------------------------------------------------------------
    // Gamma Lance
    // ------------------------------------------------------------------

    /**
     * A bolt of gamma light: a ring snaps open at the hand, a bright head races down the line dragging a green-white
     * trail (it reads even from straight behind, as a receding star), and a flash and ring burst where it ends. A faint
     * afterglow hangs along the whole line, then fades.
     */
    private static void lance(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target(), span = to.subtract(from);
        float length = (float) span.length();
        if (length < 0.1F || !c.glow()) return;
        Vec3 dir = span.scale(1 / length);
        float s = a.scale(), life = a.piece().ticks, f = Mth.clamp((life - t) / 5, 0, 1);
        float travel = out(t / 3);
        Vec3 head = from.add(dir.scale(length * travel));
        // The muzzle: rings opening at the hand, facing along the shot.
        for (int i = 0; i < 2; i++) {
            float ti = t - i * 1.2F;
            if (ti < 0) continue;
            float k = smooth(0, 6, ti);
            c.ring(from.add(dir.scale(0.3 + k * 0.8)), dir, 0.2F + k * (0.9F + 0.4F * s), 0.06F, 0, TWO_PI, i == 0 ? WHITE_HOT : TOXIC, 0.85F * (1 - k));
        }
        c.glow(from, 0.5F + 0.4F * s, TOXIC, 0.7F * (1 - smooth(0, 6, t)));
        // The trail behind the head: wide green glow, a hot white core.
        Vec3 tail = from.add(dir.scale(Math.max(0, length * travel - 7)));
        if (t < 6) {
            c.line(tail, head, 0.32F * s, TOXIC, 0.1F, 0.7F);
            c.line(tail, head, 0.1F * s, WHITE_HOT, 0.2F, 1F);
            c.glow(head, 0.7F * s, WHITE_HOT, 0.95F);
            c.glow(head, 1.6F * s, TOXIC, 0.5F);
        }
        // The afterglow along the line.
        c.line(from, head, 0.12F * s, TOXIC, 0.45F * f, 0.3F * f);
        c.line(from, head, 0.035F * s, WHITE_HOT, 0.7F * f, 0.5F * f);
        // Where it ends: a flash and a ring facing back at the caster.
        if (travel > 0.98F) {
            float k = smooth(3, 11, t);
            c.glow(to, (1.2F + 1.5F * k) * s, TOXIC, 0.8F * (1 - k));
            c.glow(to, 0.6F * s, WHITE_HOT, 0.9F * (1 - k));
            c.ring(to, dir, (0.3F + 1.6F * k) * s, 0.08F, 0, TWO_PI, TOXIC, 0.8F * (1 - k));
        }
    }

    // ------------------------------------------------------------------
    // Critical Mass building
    // ------------------------------------------------------------------

    /**
     * The moment before: the caster swells with light as they rise, rings of heat collapse in on them faster and
     * faster, and the ground under them cracks and glows.
     */
    private static void charge(Canvas c, Active a, float t) {
        float span = Math.max(1, a.scale()), k = Mth.clamp(t / span, 0, 1);
        if (t > span + 2) return;
        Entity e = a.bound(c);
        Vec3 body = (e != null ? e.getPosition(c.partial) : a.origin()).add(0, 1.0, 0);
        Vec3 ground = a.origin().add(0, 0.06, 0);
        int seed = a.seed();
        if (c.glow()) {
            MiniBlast.charge(c, body, k, t, 1.2F);
            // Rings collapsing inward, quicker as it comes.
            for (int i = 0; i < 4; i++) {
                float phase = (t * (0.06F + 0.12F * k) + i * 0.25F) % 1;
                float r = 4.5F * (1 - phase) + 0.3F;
                Vec3 axis = new Vec3(sin(i * 1.3F + t * 0.05F) * 0.4, 1, cos(i * 1.7F) * 0.4).normalize();
                c.ring(body, axis, r, 0.06F + 0.05F * k, 0, TWO_PI, i % 2 == 0 ? HEAT : WHITE_HOT, 0.75F * phase * (0.4F + 0.6F * k));
            }
            // The ground cracking under it.
            for (int i = 0; i < 9; i++) {
                float ang = (i + hash(seed, i)) * TWO_PI / 9, len = (1 + 5 * hash(seed, i + 9)) * k;
                c.bolt(ground, ground.add(cos(ang) * len, 0, sin(ang) * len), seed + i, 0.3F, 0.05F + 0.04F * k, EMBER, 0.8F * k);
            }
            c.ring(ground, UP, 1 + 5 * k, 0.15F, 0, TWO_PI, HEAT, 0.5F * k);
        }
        if (c.soft()) {
            // Dust lifting off the ground as the heat builds.
            for (int i = 0; i < 8; i++) {
                float ang = hash(seed, i + 30) * TWO_PI, d = 1.5F + 3 * hash(seed, i + 31);
                float rise = ((t * 0.04F + hash(seed, i + 32)) % 1) * 1.6F;
                c.cloud(ground.add(cos(ang) * d, 0.3 + rise, sin(ang) * d), 0.5F, 0.3F, 0.5F, i, ang + t * 0.02F, 0.3F * k, Shades.smoke(c, DUST, 0));
            }
        }
    }
}
