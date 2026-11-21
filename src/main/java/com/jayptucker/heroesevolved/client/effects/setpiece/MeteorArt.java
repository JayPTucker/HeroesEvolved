package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.ClientMeteors;
import com.jayptucker.heroesevolved.client.effects.intro.CloudMesh;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.meteor.MeteorGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.mix;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.PI;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * Starfall ({@code meteor/}), in Helixite's colours: teal light with a white-hot core and violet deep in it, over a
 * black fused crust. Drawn live from {@link ClientMeteors}:
 * <ul>
 *   <li>the night's streaks, crossing the sky far off;</li>
 *   <li>each meteor: a burning rock on the shared path ({@link MeteorGeometry#position}), a long tapering trail of light
 *   (and smoke, for a great one), and on the ground its blast's ring, filling exactly as it falls;</li>
 *   <li>whoever works on it: a thread of their power's light to the rock (or, bracing, rings in the ground).</li>
 * </ul>
 * Past {@link #FAR} blocks the whole meteor is drawn nearer the camera and smaller in proportion (as distress flares
 * are), so the far plane never clips it. Its landing and its cracking apart are set pieces.
 */
final class MeteorArt {
    private static final float[] TEAL = rgb(0x3CE0C8), CRYSTAL = rgb(0x60DED0), PALE = rgb(0xB8FFF0), WHITE = rgb(0xF4FFFC),
            VIOLET = rgb(0x7854C8), DEEP = rgb(0x1E4E58), ROCK = rgb(0x2A2526), CRUST = rgb(0x3E3836), DUST = rgb(0x8A7E70),
            SMOKE = rgb(0x45403E);
    /** The helpers' threads, by {@code Meteor.Help}: a telekinetic's violet, a flyer's wind, stopped time, a sonic's white, a quaker's wave. */
    private static final float[][] HELP = {rgb(0xB9A6FF), rgb(0xF6F4EC), rgb(0x9A7CFF), rgb(0xFFFFFF), rgb(0xCFE6FF)};
    private static final double FAR = 300;

    private MeteorArt() {}

    static boolean live() { return ClientMeteors.any(); }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    static void drawLive(Canvas c) {
        if (ClientMeteors.starfall()) streaks(c);
        for (ClientMeteors.View v : ClientMeteors.views()) meteor(c, v);
    }

    // ------------------------------------------------------------------
    // The night sky
    // ------------------------------------------------------------------

    /** Streaks of light crossing the sky far off, a few at a time, each a short-lived tapering line. */
    private static void streaks(Canvas c) {
        if (!c.glow()) return;
        // Only against a dark sky.
        float night = 1 - smooth(0.25F, 0.6F, c.daylight);
        if (night <= 0.01F) return;
        float time = c.time();
        Vec3 cam = c.camera();
        // A sky full of them: up to a dozen at once, now and then a bright one.
        int window = 5, slots = minimal() ? 3 : 8;
        long now = (long) Math.floor(time / window);
        for (long w = now - 4; w <= now; w++)
            for (int s = 0; s < slots; s++) {
                int seed = (int) (w * 31 + s * 7);
                if (hash(seed, 1) < 0.3F) continue;
                float age = time - (w * window + hash(seed, 2) * window), life = 14 + 16 * hash(seed, 3);
                if (age < 0 || age > life) continue;
                float az = hash(seed, 4) * TWO_PI, el = 0.3F + 0.85F * hash(seed, 5);
                float heading = az + (hash(seed, 6) - 0.5F) * 1.4F;
                Vec3 dir = new Vec3(cos(az) * cos(el), sin(el), sin(az) * cos(el));
                Vec3 across = new Vec3(cos(heading), -0.45, sin(heading)).normalize();
                double r = 260;
                boolean bright = hash(seed, 8) < 0.15F;
                float k = age / life, span = (40 + 50 * hash(seed, 7)) * (bright ? 1.6F : 1), size = bright ? 1.8F : 1;
                Vec3 head = cam.add(dir.scale(r)).add(across.scale(span * k));
                Vec3 tail = head.subtract(across.scale(span * 0.6F));
                float f = night * sin(k * PI);
                c.line(tail, head, 0.9F * size, TEAL, 0, f * 0.4F);
                c.line(tail, head, 0.28F * size, PALE, 0, f * 0.95F);
                c.glow(head, 2.4F * size, WHITE, f * 0.7F);
            }
    }

    // ------------------------------------------------------------------
    // A meteor
    // ------------------------------------------------------------------

    private static void meteor(Canvas c, ClientMeteors.View v) {
        float partial = c.partial(), fallen = v.fallen(partial), time = c.time();
        Vec3 cam = c.camera();
        Vec3 head = v.sky(partial);
        double dist = head.distanceTo(cam);
        // Far off, drawn nearer and smaller in proportion.
        double k = dist > FAR ? FAR / dist : 1;
        float rock = v.rock() * (float) k;
        Vec3 at = cam.add(head.subtract(cam).scale(k));
        // Never smaller than a bright point: far off its light is drawn at least a set share of its distance across.
        float seen = (float) (Math.min(dist, FAR) * (v.great ? 0.012 : 0.006));
        rock = Math.max(rock, seen);
        float in = smooth(0, 0.04F, fallen);
        int seed = v.id * 13;
        // The trail: back along its path, stretching as it speeds up.
        int n = minimal() ? 10 : 22;
        Vec3[] trail = new Vec3[n + 1];
        float[] width = new float[n + 1], alpha = new float[n + 1];
        float back = v.great ? 0.16F : 0.12F;
        for (int i = 0; i <= n; i++) {
            float q = i / (float) n;
            trail[i] = cam.add(v.behind(partial, back * q).subtract(cam).scale(k));
            width[i] = rock * (v.great ? 2.2F : 1.8F) * (1 - q) + 0.02F;
            alpha[i] = in * (1 - q) * (1 - q);
        }
        if (c.glow()) {
            c.ribbon(trail, scale(width, 2.2F), TEAL, scale(alpha, 0.35F));
            c.ribbon(trail, width, CRYSTAL, scale(alpha, 0.7F));
            c.ribbon(trail, scale(width, 0.35F), WHITE, alpha);
            c.glow(at, rock * 4.5F, TEAL, in * 0.45F);
            c.glow(at, rock * 1.8F, WHITE, in * 0.9F);
            if (v.held) {
                // Held in stopped time: a violet clock round it.
                float spin = time * 0.02F;
                c.ring(at, UP, rock * 2.6F, rock * 0.12F, spin, spin + TWO_PI * 0.85F, HELP[2], in * 0.8F);
            }
            if (v.cracked > 0) {
                int cracks = 2 + Math.round(8 * v.cracked);
                for (int i = 0; i < cracks; i++) {
                    float a = hash(seed, i + 40) * TWO_PI, b = hash(seed, i + 60) * PI - PI / 2;
                    Vec3 dir = new Vec3(cos(a) * cos(b), sin(b), sin(a) * cos(b));
                    c.bolt(at, at.add(dir.scale(rock * (1.1 + v.cracked))), seed + i + (int) (time / 2), 0.3F * rock, 0.03F + 0.02F * rock,
                            WHITE, in * (0.4F + 0.6F * v.cracked));
                }
            }
        }
        if (c.soft() && v.great && !minimal()) {
            // A great one smokes: dark billows left along its trail, thinning behind it.
            CloudMesh.Shade smoke = Shades.smoke(c, SMOKE, 0.35F);
            for (int i = 2; i <= n; i += 2) {
                float q = i / (float) n, s = rock * (1.2F + 1.8F * q);
                c.cloud(trail[i], s, s * 0.8F, s, i + seed, time * 0.02F + i, in * 0.5F * (1 - q), smoke);
            }
        }
        if (c.solid()) {
            // The rock: a lump of black crust tumbling as it falls.
            int lumps = v.great ? 5 : 3;
            for (int i = 0; i < lumps; i++) {
                Vec3 o = i == 0 ? at : at.add((hash(seed, i) - 0.5) * rock, (hash(seed, i + 10) - 0.5) * rock, (hash(seed, i + 20) - 0.5) * rock);
                float s = rock * (i == 0 ? 0.9F : 0.55F);
                c.box(o, s, s * 0.85F, s * 0.95F, time * 0.05F + i, time * 0.04F + i * 2, i, i == 0 ? ROCK : CRUST, 1);
            }
        }
        ring(c, v, fallen);
        threads(c, v, at, rock);
    }

    private static float[] scale(float[] a, float k) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] * k;
        return out;
    }

    /** Its blast's ring on the ground, filling exactly as it falls, brighter at its middle as it nears. */
    private static void ring(Canvas c, ClientMeteors.View v, float fallen) {
        if (!c.glow() && !c.soft()) return;
        Vec3 o = v.land.add(0, 0.08, 0);
        if (o.distanceToSqr(c.camera()) > 192 * 192) return;
        float r = v.blast(), in = smooth(0, 0.02F, fallen);
        if (c.soft()) {
            // A dark body under the light, so it reads at noon on grass.
            c.ring(o, UP, r, 0.45F, 0, TWO_PI, DEEP, in * 0.45F);
            c.ring(o, UP, r, 0.8F, -PI / 2, -PI / 2 + TWO_PI * fallen, DEEP, in * 0.75F);
            return;
        }
        c.ring(o, UP, r, 0.12F, 0, TWO_PI, TEAL, in * 0.4F);
        c.ring(o, UP, r, 0.25F, -PI / 2, -PI / 2 + TWO_PI * fallen, CRYSTAL, in * 0.9F);
        float late = smooth(0.7F, 1, fallen);
        if (late > 0) {
            c.glow(o.add(0, 0.3, 0), r * 0.5F * late, TEAL, late * 0.35F);
            float pulse = (c.time() * 0.15F) % 1;
            c.ring(o, UP, r * (1 - pulse), 0.1F, 0, TWO_PI, PALE, late * 0.6F * pulse);
        }
        if (v.braced > 0) {
            // The ground holding: blue-white rings settling into it.
            for (int i = 0; i < 2; i++) {
                float p = (c.time() * 0.05F + i * 0.5F) % 1;
                c.ring(o, UP, r * 0.3F + r * 0.6F * p, 0.18F, 0, TWO_PI, HELP[4], v.braced * 0.6F * (1 - p));
            }
        }
    }

    /** A thread of light from each helper's hand to the rock (a quaker's runs into the ground instead). */
    private static void threads(Canvas c, ClientMeteors.View v, Vec3 at, float rock) {
        if (!c.glow() || v.helpers.length == 0) return;
        for (int i = 0; i < v.helpers.length; i++) {
            Entity e = c.level.getEntity(v.helpers[i]);
            if (e == null) continue;
            int kind = Math.clamp(v.kinds[i], 0, HELP.length - 1);
            float[] col = HELP[kind];
            Vec3 hand = e.getPosition(c.partial).add(0, e.getBbHeight() * 0.75, 0);
            Vec3 to = kind == 4 ? v.land.add(0, 0.1, 0) : at;
            c.line(hand, to, 0.22F, col, 0.3F, 0.15F);
            c.line(hand, to, 0.07F, col, 0.8F, 0.5F);
            c.line(hand, to, 0.02F, WHITE, 0.9F, 0.7F);
            float u = (c.time() * 0.06F + i * 0.3F) % 1;
            c.glow(hand.lerp(to, kind == 4 ? u : 1 - u), 0.3F, col, 0.7F);
            if (kind == 3) c.ring(at, UP, rock * (1.6F + 0.6F * sin(c.time() * 0.8F)), rock * 0.08F, 0, TWO_PI, col, 0.5F);
        }
    }

    // ------------------------------------------------------------------
    // Set pieces
    // ------------------------------------------------------------------

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case METEOR_IMPACT -> impact(c, a, t);
            case METEOR_SHATTER -> shatter(c, a, t);
            default -> {}
        }
    }

    /**
     * A landing: a white-teal flash, a shock ring running out past the blast, dust billowing up and out, and lumps of rock
     * and crystal thrown up and falling back.
     */
    private static void impact(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        float blast = Math.max(2, a.scale()), life = a.piece().ticks;
        boolean great = blast > 10;
        int seed = a.seed();
        float f = Mth.clamp((life - t) / 12, 0, 1);
        if (c.glow()) {
            float flash = Mth.clamp(1 - t / 8, 0, 1);
            c.glow(o.add(0, 1, 0), blast * 1.6F * (0.5F + flash), WHITE, flash * 0.9F);
            c.glow(o.add(0, 1, 0), blast * 2.6F, TEAL, flash * 0.5F + f * 0.1F);
            float k = 1 - (float) Math.pow(1 - Mth.clamp(t / 14, 0, 1), 3);
            c.ring(o.add(0, 0.3, 0), UP, 1 + blast * 1.8F * k, 0.3F + blast * 0.04F, 0, TWO_PI, PALE, f * 0.8F * (1 - k * 0.6F));
            c.ring(o.add(0, 0.3, 0), UP, 1 + blast * 1.8F * k, 0.9F + blast * 0.1F, 0, TWO_PI, TEAL, f * 0.3F);
            // The meteorite glowing in its crater.
            c.glow(o.add(0, 0.2, 0), great ? 3.5F : 1.4F, CRYSTAL, f * 0.6F);
        }
        if (c.soft()) {
            CloudMesh.Shade dust = Shades.smoke(c, DUST, 0.25F * Mth.clamp(1 - t / 12, 0, 1));
            int puffs = minimal() ? 8 : great ? 34 : 14;
            for (int i = 0; i < puffs; i++) {
                float ang = (i + hash(seed, i)) * TWO_PI / puffs, up = hash(seed, i + 30);
                float k = 1 - (float) Math.pow(1 - Mth.clamp(t / (life * 0.6F), 0, 1), 2);
                float r = blast * (0.3F + 0.9F * k) * (0.7F + 0.5F * hash(seed, i + 60));
                float s = (great ? 3.5F : 1.2F) * (0.6F + 0.8F * k);
                c.cloud(o.add(cos(ang) * r, up * blast * 0.5 * k + s * 0.4, sin(ang) * r), s, s * 0.8F, s, i, ang, f * 0.65F, dust);
            }
        }
        if (c.solid()) {
            int pieces = minimal() ? 6 : great ? 26 : 10;
            float g = 0.04F;
            for (int i = 0; i < pieces; i++) {
                float ang = hash(seed, i + 100) * TWO_PI, speed = (great ? 0.9F : 0.5F) * (0.5F + hash(seed, i + 120));
                float vy = (great ? 1.0F : 0.6F) * (0.5F + hash(seed, i + 140));
                double y = vy * t - g * t * t;
                if (y < -0.5 && t > 2) continue;
                Vec3 p = o.add(cos(ang) * speed * t, Math.max(0, y) + 0.3, sin(ang) * speed * t);
                float s = (great ? 0.45F : 0.2F) * (0.5F + hash(seed, i + 160));
                c.box(p, s, s * 0.8F, s, t * 0.3F + i, t * 0.2F, i, i % 4 == 0 ? CRYSTAL : i % 2 == 0 ? ROCK : DUST, 1);
            }
        }
    }

    /** Cracked apart in the sky: a burst of white and teal, and shards of crust flying out of it. */
    private static void shatter(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        Vec3 cam = c.camera();
        double dist = o.distanceTo(cam), k = dist > FAR ? FAR / dist : 1;
        Vec3 at = cam.add(o.subtract(cam).scale(k));
        float rock = Math.max(0.5F, a.scale()) * (float) k, life = a.piece().ticks, f = Mth.clamp((life - t) / 10, 0, 1);
        int seed = a.seed();
        if (c.glow()) {
            float flash = Mth.clamp(1 - t / 6, 0, 1);
            c.glow(at, rock * 6 * (0.5F + flash), WHITE, flash);
            c.glow(at, rock * 9, TEAL, f * 0.4F);
            c.ring(at, UP, rock * (2 + 8 * Mth.clamp(t / 12, 0, 1)), rock * 0.2F, 0, TWO_PI, PALE, f * 0.7F);
        }
        if (c.solid()) {
            for (int i = 0; i < 12; i++) {
                float az = hash(seed, i) * TWO_PI, el = (hash(seed, i + 20) - 0.5F) * PI;
                Vec3 dir = new Vec3(cos(az) * cos(el), sin(el), sin(az) * cos(el));
                Vec3 p = at.add(dir.scale(rock * 0.4F * t));
                float s = rock * 0.25F * (0.6F + hash(seed, i + 40));
                c.box(p, s, s, s, t * 0.2F + i, t * 0.3F, i, i % 3 == 0 ? CRYSTAL : ROCK, 1);
            }
        }
        if (c.glow()) for (int i = 0; i < 8; i++) {
            float az = hash(seed, i + 60) * TWO_PI, el = (hash(seed, i + 80) - 0.5F) * PI;
            Vec3 dir = new Vec3(cos(az) * cos(el), sin(el), sin(az) * cos(el));
            Vec3 p = at.add(dir.scale(rock * 0.5F * t));
            c.line(p.subtract(dir.scale(rock * 0.8F)), p, rock * 0.08F, mix(TEAL, WHITE, 0.4F), 0, f * 0.8F);
        }
    }
}
