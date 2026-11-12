package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.SpeedCyclone;
import com.jayptucker.heroesevolved.client.effects.SpeedOverrunClient;
import com.jayptucker.heroesevolved.client.effects.intro.CloudMesh;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.speed.SpeedOverrun;
import com.jayptucker.heroesevolved.speed.TornadoShape;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

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
 * Superspeed drawn as geometry: Cyclone's wind round the runner's ring and the tornado it rises into, the gust
 * a sudden stop throws ahead, and outrunning the last minute (the wake, the breach, leaving and arriving).
 */
public final class SpeedArt {
    private static final float[] GOLD = rgb(0xFFC640), HOT = rgb(0xFFF4C8), AMBER = rgb(0xFF8C1E),
            WIND = {0.86F, 0.9F, 0.96F}, DUST = {0.84F, 0.79F, 0.7F}, DUST_DARK = {0.66F, 0.63F, 0.6F},
            CLOUD = {0.86F, 0.86F, 0.89F}, FUNNEL = {0.8F, 0.8F, 0.84F}, STORM = {0.52F, 0.53F, 0.58F}, SOOT = {0.1F, 0.08F, 0.07F},
            STORM_LIT = {0.7F, 0.71F, 0.76F}, VIOLET = {0.6F, 0.49F, 1}, DEEP = {0.36F, 0.25F, 0.82F},
            PALE = {0.96F, 0.92F, 1}, CYAN = {0.55F, 0.85F, 1},
            TEMPEST_CLOUD = rgb(0xDBDBD2), TEMPEST_WIND = rgb(0xEEEDE3);
    private static final float[][] DEBRIS = {{0.42F, 0.3F, 0.2F}, {0.5F, 0.5F, 0.5F}, {0.36F, 0.52F, 0.2F}, {0.55F, 0.4F, 0.25F}, {0.3F, 0.28F, 0.26F}};

    private SpeedArt() {}

    /** Is anything of Superspeed's with no fixed length (a cyclone, a wake) to be drawn? */
    static boolean live() {
        return !SpeedCyclone.views().isEmpty() || !SpeedOverrunClient.states().isEmpty();
    }

    /** Draws the cyclones and wakes playing now, in the canvas's current pass. */
    static void drawLive(Canvas c) {
        long now = c.level.getGameTime();
        Vec3 eye = c.camera();
        for (SpeedCyclone.View v : SpeedCyclone.views()) {
            if (v.center.distanceToSqr(eye) > 200 * 200) continue;
            if (v.rose()) tornado(c, v, now);
            ring(c, v, now);
            if (v.style == com.jayptucker.heroesevolved.network.SpeedCyclonePayload.CONTAIN && !v.ended()) containment(c, v, now);
        }
        for (SpeedOverrunClient.State s : SpeedOverrunClient.states()) overrun(c, s, now);
    }

    // ------------------------------------------------------------------
    // Shared strokes
    // ------------------------------------------------------------------

    /** A band along the ring behind angle {@code theta}, {@code len} radians long, brightest at its head. */
    private static void trail(Canvas c, Vec3 o, float r, float theta, double h, float len, float width, float[] col, float alpha) {
        int n = 20;
        double[] x = new double[n + 1], y = new double[n + 1], z = new double[n + 1];
        float[] a = new float[n + 1], w = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n, ang = theta - len * (1 - k);
            x[i] = o.x + cos(ang) * r; y[i] = o.y + h; z[i] = o.z + sin(ang) * r;
            a[i] = alpha * k * k; w[i] = width * (0.35F + 0.65F * k);
        }
        c.ribbon(x, y, z, a, w, col);
    }

    /** A band round part of a circle at height {@code h}, faded at both ends. */
    private static void arc(Canvas c, Vec3 o, float r, double h, float from, float to, float width, float[] col, float alpha) {
        int n = 16;
        double[] x = new double[n + 1], y = new double[n + 1], z = new double[n + 1];
        float[] a = new float[n + 1], w = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n, ang = from + (to - from) * k;
            x[i] = o.x + cos(ang) * r; y[i] = o.y + h; z[i] = o.z + sin(ang) * r;
            a[i] = alpha * sin(k * PI); w[i] = width;
        }
        c.ribbon(x, y, z, a, w, col);
    }

    /** Two directions across a plane whose normal is {@code n}, as {@link Canvas#ring} uses them. */
    private static Vec3[] across(Vec3 n) {
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0).cross(n) : UP.cross(n);
        u = u.normalize();
        return new Vec3[] {u, n.cross(u).normalize()};
    }

    private static Vec3 on(Vec3 at, Vec3[] b, float angle, float r) {
        return at.add(b[0].scale(cos(angle) * r)).add(b[1].scale(sin(angle) * r));
    }

    // ------------------------------------------------------------------
    // Cyclone
    // ------------------------------------------------------------------

    /** The ring: a gold trail behind the runner with lightning off it, wind bands dragged round after them, dust and grit. */
    private static void ring(Canvas c, SpeedCyclone.View v, long now) {
        // No one on the ring: a tornado left standing alone, or a Tempest (its flyer is seen as they are).
        if (v.free) return;
        float age = v.age(now, c.partial), gone = v.gone(now, c.partial);
        float fade = gone < 0 ? 1 : 1 - gone / (v.rose() ? SpeedCyclone.TORNADO_FADE : SpeedCyclone.RING_FADE);
        float f = Mth.clamp(age / 6, 0, 1) * Mth.clamp(fade, 0, 1);
        // A beaten runner's trail fades as they slow to a stop.
        f *= 1 - 0.8F * v.stillness(now, c.partial);
        if (f <= 0.003F) return;
        Vec3 o = v.center;
        float r = v.radius, theta = v.angle(age);
        boolean running = !v.ended();
        int seed = v.entity * 31;
        if (c.glow()) {
            if (running) {
                trail(c, o, r, theta, 0.95, 2.6F, 0.34F, GOLD, 0.85F * f);
                trail(c, o, r, theta, 0.95, 1.2F, 0.1F, HOT, 0.9F * f);
                trail(c, o, r, theta, 1.55, 1.7F, 0.08F, AMBER, 0.6F * f);
                trail(c, o, r, theta, 0.25, 1.4F, 0.12F, GOLD, 0.5F * f);
                int flick = (int) (age / 2);
                for (int k = 0; k < 3; k++) {
                    float a0 = theta - 0.3F - hash(seed + flick, k) * 1.9F;
                    Vec3 p0 = o.add(cos(a0) * r, 0.5 + hash(seed + flick, k + 5) * 1.1, sin(a0) * r);
                    Vec3 p1 = p0.add((hash(seed + flick, k + 9) - 0.5) * 1.8, (hash(seed + flick, k + 11) - 0.3) * 1.2, (hash(seed + flick, k + 13) - 0.5) * 1.8);
                    c.bolt(p0, p1, seed + flick * 7 + k, 0.25F, 0.035F, HOT, 0.8F * f);
                }
            } else if (!v.rose()) {
                // The ring breaks: a shock of wind runs out from it.
                float k = Mth.clamp(gone / SpeedCyclone.RING_FADE, 0, 1);
                c.ring(o.add(0, 0.4, 0), r + k * 5, 0.5F, WIND, 0.5F * (1 - k));
            }
            c.ring(o.add(0, 0.06, 0), r, 0.45F, GOLD, 0.22F * f);
        }
        if (c.soft()) {
            for (int b = 0; b < 6; b++) {
                float h = 0.35F + b * 0.48F, head = theta * 0.75F + b * 1.1F, len = 1.4F + hash(seed, b) * 0.9F;
                float rr = r + (b % 2 == 0 ? 0.45F : -0.35F);
                arc(c, o, rr, h, head - len, head, 0.24F, WIND, 0.3F * f);
            }
            CloudMesh.Shade dust = Shades.smoke(c, DUST, 0);
            for (int j = 0; j < 12; j++) {
                float a = j * TWO_PI / 12 + age * 0.22F + hash(seed, j + 30);
                float rr = r + (hash(seed, j + 40) - 0.5F) * 1.2F, s = 0.45F + hash(seed, j + 50) * 0.4F;
                c.cloud(o.add(cos(a) * rr, 0.15 + s * 0.2, sin(a) * rr), s * 1.5F, s * 0.32F, s * 1.5F, j, a, 0.15F * f, dust);
            }
        }
        if (c.solid() && running) {
            for (int i = 0; i < 12; i++) {
                float a = hash(seed, i + 60) * TWO_PI + age * (0.3F + hash(seed, i + 70) * 0.2F);
                float h = 0.3F + ((age * 0.03F + hash(seed, i + 80)) % 1) * 2.6F, rr = r + (hash(seed, i + 90) - 0.5F) * 1.4F;
                c.box(o.add(cos(a) * rr, h, sin(a) * rr), 0.07F, 0.02F, 0.06F, a * 2, age * 0.4F + i, i, DEBRIS[i % DEBRIS.length], 1);
            }
        }
    }

    /**
     * The tornado: a funnel of billowing dust and cloud standing on the ring, pinched to a neck and flaring to a
     * lid of storm cloud, turning faster near the ground; the runner's lightning wound up inside it; debris
     * carried up its wall. When it comes down it lifts off the ground and thins away from the bottom up.
     */
    private static void tornado(Canvas c, SpeedCyclone.View v, long now) {
        float built = v.built(now, c.partial), gone = v.gone(now, c.partial);
        float collapse = gone < 0 ? 0 : Mth.clamp(gone / SpeedCyclone.TORNADO_FADE, 0, 1);
        float height = TornadoShape.height(built), grow = Mth.clamp(built / TornadoShape.GROW, 0, 1);
        float f = Mth.clamp(built / 8, 0, 1) * (1 - collapse * collapse);
        if (f <= 0.003F) return;
        Vec3 o = v.at(c.partial);
        float ring = v.radius;
        int seed = v.entity * 17;
        // A speedster winds lightning through the funnel. A flyer winds strips of condensation.
        boolean sky = v.style == com.jayptucker.heroesevolved.network.SpeedCyclonePayload.FLIGHT;
        float[] strand = sky ? TEMPEST_WIND : GOLD;
        if (sky) FlightArt.tempest(c, o, built, f);
        if (c.soft()) {
            // The funnel's skin: see-through, streaked in spirals that wind up it as it turns.
            final float h0 = height, cut = collapse;
            float twist = built * 0.35F;
            c.revolve(o, height, u -> TornadoShape.radius(u * h0, h0, ring), sky ? TEMPEST_CLOUD : FUNNEL, (sky ? .38F : .55F) * f,
                    (theta, u) -> smooth(cut * 1.15F - 0.1F, cut * 1.15F + 0.05F, u)
                            * (0.45F + 0.55F * Mth.abs(sin(theta * 3 - u * 9 + twist))) * (0.6F + 0.4F * u));
            int layers = 16;
            for (int i = 0; i < layers; i++) {
                float u = (i + 0.5F) / layers, h = u * height;
                float gate = smooth(collapse * 1.15F - 0.1F, collapse * 1.15F + 0.05F, u);
                float alpha = 0.36F * f * gate;
                if (alpha < 0.01F) continue;
                float wall = TornadoShape.radius(h, height, ring);
                int n = 6 + (int) (wall * 1.4F);
                float spin = built * (0.5F - 0.32F * u) + i * 0.7F;
                CloudMesh.Shade shade = Shades.smoke(c, mix(DUST_DARK, sky ? TEMPEST_CLOUD : CLOUD, Mth.clamp(u * 1.3F, 0, 1)), 0);
                for (int j = 0; j < n; j++) {
                    float ang = j * TWO_PI / n + spin + hash(seed + i, j) * 0.5F;
                    float s = (0.5F + wall * 0.2F) * (0.8F + hash(seed + i, j + 20) * 0.45F);
                    float rr = wall + (hash(seed + i, j + 40) - 0.3F) * s * 0.6F;
                    float y = h + (hash(seed + i, j + 60) - 0.5F) * height / layers * 1.1F;
                    c.cloud(o.add(cos(ang) * rr, y, sin(ang) * rr), s * 1.15F, s * 0.6F, s * 1.15F, i * 7 + j, ang,
                            sky ? alpha * .72F : alpha, shade);
                }
            }
            for (int k = 0; k < 16; k++) wallStreak(c, o, height, ring, built, seed, k, 0.3F * f, collapse);
            if (collapse < 0.5F) {
                CloudMesh.Shade dust = Shades.smoke(c, DUST, 0);
                for (int j = 0; j < 14; j++) {
                    float ang = j * TWO_PI / 14 + built * 0.35F + hash(seed, j + 300);
                    float rr = ring * (0.7F + hash(seed, j + 320) * 0.5F), s = (0.9F + hash(seed, j + 340) * 0.6F) * (0.5F + 0.5F * grow);
                    c.cloud(o.add(cos(ang) * rr, s * 0.25, sin(ang) * rr), s * 1.4F, s * 0.38F, s * 1.4F, j + 3, ang, 0.22F * f * (1 - collapse * 2), dust);
                }
            }
            CloudMesh.Shade storm = Shades.smoke(c, sky ? TEMPEST_CLOUD : STORM_LIT, 0);
            for (int j = 0; j < 14; j++) {
                float ang = j * TWO_PI / 14 + built * 0.08F + hash(seed, j + 400);
                float rr = TornadoShape.TOP * (0.5F + hash(seed, j + 420) * 0.55F) * grow, s = 1.8F + hash(seed, j + 440) * 1.2F;
                c.cloud(o.add(cos(ang) * rr, height + 0.2 + hash(seed, j + 460) * 1.0, sin(ang) * rr), s * 1.3F, s * 0.38F, s * 1.3F, j + 5, ang, 0.36F * f * grow, storm);
            }
        }
        if (c.glow()) {
            for (int k = 0; k < 3; k++) innerHelix(c, o, height, ring, built, k, !sky && k == 0 ? HOT : strand,
                    (sky ? .22F : .55F) * f, collapse);
            int flick = (int) (built / 3);
            if (!sky && hash(seed, flick) < 0.45F && collapse < 0.6F) {
                float h0 = hash(seed, flick + 1) * height * 0.75F, h1 = h0 + 2 + hash(seed, flick + 2) * 5;
                float a0 = hash(seed, flick + 3) * TWO_PI, a1 = a0 + 0.8F + hash(seed, flick + 4) * 1.5F;
                float r0 = TornadoShape.radius(h0, height, ring) * 0.75F, r1 = TornadoShape.radius(h1, height, ring) * 0.75F;
                c.bolt(o.add(cos(a0) * r0, h0, sin(a0) * r0), o.add(cos(a1) * r1, h1, sin(a1) * r1), seed + flick, 0.6F, 0.06F, HOT, 0.9F * f);
            }
            c.glow(o.add(0, height * 0.5, 0), height * 0.35F, strand, 0.05F * f);
        }
        if (c.solid()) {
            for (int d = 0; d < 34; d++) {
                float u = (hash(seed, d + 100) + built * 0.004F * (1 + hash(seed, d + 130))) % 1;
                if (u < collapse) continue;
                float h = u * height * 0.92F, wall = TornadoShape.radius(h, height, ring) * (0.85F + hash(seed, d + 160) * 0.25F);
                float ang = hash(seed, d + 190) * TWO_PI + built * (0.55F - 0.35F * u), s = 0.12F + hash(seed, d + 220) * 0.28F;
                c.box(o.add(cos(ang) * wall, h, sin(ang) * wall), s, s * 0.6F, s * 0.8F, ang * 1.7F, built * 0.2F + d, d, DEBRIS[d % DEBRIS.length], 1);
            }
        }
    }

    /** A pale streak spiralling up the funnel wall. */
    private static void wallStreak(Canvas c, Vec3 o, float height, float ring, float built, int seed, int k, float alpha, float collapse) {
        float u0 = hash(seed, k + 500) * 0.6F, a0 = hash(seed, k + 520) * TWO_PI;
        int n = 16;
        double[] x = new double[n + 1], y = new double[n + 1], z = new double[n + 1];
        float[] a = new float[n + 1], w = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float q = i / (float) n, u = u0 + q * 0.4F, h = u * height;
            float wall = TornadoShape.radius(h, height, ring) * 0.96F, ang = a0 + q * 2.5F + built * (0.5F - 0.3F * u);
            x[i] = o.x + cos(ang) * wall; y[i] = o.y + h; z[i] = o.z + sin(ang) * wall;
            a[i] = u < collapse ? 0 : alpha * sin(q * PI); w[i] = 0.35F + wall * 0.04F;
        }
        c.ribbon(x, y, z, a, w, WIND);
    }

    /** A strand of the runner's lightning wound up the inside of the funnel. */
    private static void innerHelix(Canvas c, Vec3 o, float height, float ring, float built, int k, float[] col, float alpha, float collapse) {
        int n = 40;
        double[] x = new double[n + 1], y = new double[n + 1], z = new double[n + 1];
        float[] a = new float[n + 1], w = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, h = u * height * 0.95F;
            float wall = TornadoShape.radius(h, height, ring) * 0.55F, ang = k * TWO_PI / 3 + h * 0.35F + built * 0.4F;
            x[i] = o.x + cos(ang) * wall; y[i] = o.y + h; z[i] = o.z + sin(ang) * wall;
            a[i] = u < collapse ? 0 : alpha * sin(u * PI); w[i] = 0.1F;
        }
        c.ribbon(x, y, z, a, w, col);
    }

    // ------------------------------------------------------------------
    // A sudden stop
    // ------------------------------------------------------------------

    /**
     * The gust a sudden stop throws: the air you were dragging rushes on past in streaks and spreads, a bow
     * wave of pressed air rolls ahead, skid dust billows forward off your feet, grit is flung on, and two
     * scorched skid marks lie behind you, glowing for a moment.
     */
    static void gust(Canvas c, Active a, float t) {
        float life = a.piece().ticks, s = Mth.clamp(a.scale(), 0.2F, 1), f = Mth.clamp(1 - t / life, 0, 1);
        Vec3 o = a.origin(), dir = a.facing(), side = new Vec3(-dir.z, 0, dir.x);
        int seed = a.seed();
        float facing = (float) Math.atan2(dir.x, dir.z);
        if (c.soft()) {
            CloudMesh.Shade dust = Shades.smoke(c, DUST, 0);
            for (int k = 0; k < 8; k++) {
                float start = k * 0.4F;
                if (t < start) continue;
                float go = (3F + 3F * s) * (1 - (float) Math.exp(-(t - start) / 7F));
                float lat = (hash(seed, k) - 0.5F) * 1.8F * (1 + go * 0.15F), size = 0.45F + go * 0.12F + hash(seed, k + 10) * 0.3F;
                Vec3 at = o.add(dir.scale(go + 0.3)).add(side.scale(lat)).add(0, 0.2 + size * 0.4, 0);
                c.cloud(at, size * 1.3F, size * 0.7F, size * 1.3F, k, hash(seed, k + 20) * TWO_PI + t * 0.05F,
                        0.42F * f * Mth.clamp(t / 3, 0, 1), dust);
            }
            for (int i = 0; i < 16; i++) streak(c, o, dir, side, seed, i, t, s, life, WIND, 0.5F * f);
            float bow = 0.8F + t * 0.5F * (1 - t / 80F), fb = Mth.clamp(1 - t / 18F, 0, 1);
            for (int b = 0; b < 3; b++)
                c.ring(o.add(0, 0.35 + b * 0.65, 0), UP, bow + b * 0.15F, 0.28F, facing - 1.1F, facing + 1.1F, WIND, 0.5F * fb * (0.5F + 0.5F * s));
            for (int foot = -1; foot <= 1; foot += 2) {
                Vec3 heel = o.add(side.scale(foot * 0.16)).add(0, 0.03, 0);
                c.line(heel.subtract(dir.scale(2.4 * s + 0.6)), heel, 0.1F, SOOT, 0, 0.55F * f);
            }
        }
        if (c.glow()) {
            float hot = Mth.clamp(1 - t / 10, 0, 1);
            for (int foot = -1; foot <= 1; foot += 2) {
                Vec3 heel = o.add(side.scale(foot * 0.16)).add(0, 0.04, 0);
                c.line(heel.subtract(dir.scale(2.4 * s + 0.6)), heel, 0.06F, AMBER, 0, 0.8F * hot);
            }
            for (int i = 0; i < 4; i++) streak(c, o, dir, side, seed + 77, i, t, s, life, GOLD, 0.6F * f);
            c.ring(o.add(0, 0.05, 0), 0.4F + t * 0.3F, 0.2F, WIND, 0.5F * Mth.clamp(1 - t / 12, 0, 1));
            c.glow(o.add(0, 0.9, 0).add(dir.scale(0.6)), 1.4F * hot, HOT, 0.3F * hot);
        }
        if (c.solid()) {
            for (int i = 0; i < 9; i++) {
                float pace = 0.35F + hash(seed, i + 40) * 0.4F, d = pace * t * (1 - t / 70);
                float y = Math.max(0.05F, 0.3F + t * 0.07F * (0.5F + hash(seed, i + 50)) - t * t * 0.0035F);
                Vec3 at = o.add(dir.scale(0.6 + d)).add(side.scale((hash(seed, i + 60) - 0.5F) * 2 * (1 + d * 0.2))).add(0, y, 0);
                c.box(at, 0.06F, 0.02F, 0.05F, i, t * 0.5F + i, i * 2, DEBRIS[i % DEBRIS.length], f > 0.05F ? 1 : 0);
            }
        }
    }

    /**
     * Wind Arms: a blurred disc at each hand where it circles too fast to see, and off each palm a funnel of wind
     * spiralling out along the look (or down into the ground, lifting). It follows the player; the server sends
     * a fresh one every ten ticks, each fading in and out over two, so they hand over without a seam.
     */
    static void arms(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (e == null) return;
        float life = a.piece().ticks, f = Mth.clamp(Math.min(t / 2F, (life - t) / 2F), 0, 1);
        if (f <= 0) return;
        float now = c.level == null ? t : (c.level.getGameTime() % 24000L) + c.partial;
        Vec3 pos = e.getPosition(c.partial), look = e.getViewVector(c.partial);
        // The arms hang off the body, which turns with the body's yaw, not the head's.
        float bodyYaw = (e instanceof net.minecraft.world.entity.LivingEntity l ? Mth.rotLerp(c.partial, l.yBodyRotO, l.yBodyRot) : e.getViewYRot(c.partial)) * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(-cos(bodyYaw), 0, -sin(bodyYaw));
        boolean down = look.y < -0.7;
        Vec3 dir = look;
        float length = down ? 3.5F : 8;
        Vec3[] basis = basis(dir);
        double shoulderY = e.isCrouching() ? 1.17 : 1.375;
        for (int hand = -1; hand <= 1; hand += 2) {
            // Shoulder joints sit five pixels out from the middle; an arm is twelve pixels long.
            Vec3 shoulder = pos.add(0, shoulderY, 0).add(side.scale(hand * 0.3125));
            Vec3 palm = shoulder.add(dir.scale(0.72));
            float turn = now * 0.9F * hand;
            if (c.soft()) {
                // The hand circling too fast to see: a pale blurred disc across the arm's end.
                c.disc(palm.subtract(dir.scale(0.1)), dir, 0.34F, WIND, 0.38F * f, 0.05F * f);
                for (int s = 0; s < 4; s++) {
                    int n = 22;
                    Vec3[] p = new Vec3[n + 1];
                    for (int k = 0; k <= n; k++) {
                        float d = length * k / n, r = 0.16F + d * 0.26F, ang = turn + s * TWO_PI / 4 + d * 1.4F;
                        p[k] = palm.add(dir.scale(d)).add(basis[0].scale(cos(ang) * r)).add(basis[1].scale(sin(ang) * r));
                    }
                    c.ribbon(p, 0.09F, WIND, 0.7F * f, 0.05F * f);
                }
                for (int i = 0; i < 5; i++) {
                    float d = (now * 0.8F + i * length / 5F) % length, r = 0.16F + d * 0.26F, fade = 1 - d / length;
                    float gap = turn + i * 1.7F;
                    c.ring(palm.add(dir.scale(d)), dir, r, 0.07F + d * 0.015F, gap, gap + 4.8F, WIND, 0.6F * f * fade);
                }
            }
            if (c.glow()) {
                for (int s = 0; s < 2; s++) {
                    int n = 10;
                    Vec3[] p = new Vec3[n + 1];
                    for (int k = 0; k <= n; k++) {
                        float d = length * 0.6F * k / n, r = 0.1F + d * 0.22F, ang = turn * 1.3F + s * PI + d * 1.8F;
                        p[k] = palm.add(dir.scale(d)).add(basis[0].scale(cos(ang) * r)).add(basis[1].scale(sin(ang) * r));
                    }
                    c.ribbon(p, 0.03F, GOLD, 0.5F * f, 0);
                }
            }
        }
        if (down && c.soft()) {
            // Dust kicked out in a ring where the funnels hit the ground.
            Vec3 foot = pos.add(0, -Math.min(com.jayptucker.heroesevolved.speed.SpeedWindArms.heightAboveGround(e), 8) + 0.1, 0);
            for (int i = 0; i < 8; i++) {
                float ang = now * 0.3F + i * TWO_PI / 8, r = 0.6F + ((now * 0.08F + i * 0.13F) % 1) * 1.8F;
                c.cloud(foot.add(cos(ang) * r, 0.15, sin(ang) * r), 0.4F, 0.2F, 0.4F, i, ang, 0.3F * f * (1 - r / 2.6F), Shades.smoke(c, DUST, 0));
            }
        }
    }

    private static Vec3[] basis(Vec3 axis) {
        Vec3 ref = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 u = axis.cross(ref).normalize();
        return new Vec3[] {u, axis.cross(u).normalize()};
    }

    // ------------------------------------------------------------------
    // Running the heat out of a radioactive player
    // ------------------------------------------------------------------

    private static final float[] RAD_GREEN = rgb(0xC4F23C), EMBER = rgb(0xFF4A0C), ASH = {0.36F, 0.34F, 0.33F};

    /**
     * A containment run: the runner's trail is wound into a spinning wall of light round the radioactive player, and
     * their heat is dragged out of them on the wind: orange streams spiralling out to the wall, green motes pulled
     * after them.
     */
    private static void containment(Canvas c, SpeedCyclone.View v, long now) {
        float age = v.age(now, c.partial), f = Mth.clamp(age / 8, 0, 1);
        Vec3 o = v.center;
        float r = v.radius, theta = v.angle(age);
        int seed = v.entity * 17;
        // Holding a blast in builds over five seconds: everything grows denser, taller and brighter.
        float hold = v.held == Long.MIN_VALUE ? 0 : Mth.clamp((now - v.held + c.partial) / 100F, 0, 1);
        if (c.glow()) {
            // The wall: the runner's light laid down lap over lap at every height, more of it the longer it holds.
            int layers = 4 + (int) (hold * 4);
            for (int k = 0; k < layers; k++) {
                float h = 0.4F + k * (0.62F + hold * 0.1F);
                trail(c, o, r, theta - k * 0.35F, h, 4.2F + hold * 1.5F, 0.16F + hold * 0.08F, k % 2 == 0 ? GOLD : HOT, (0.55F + 0.3F * hold) * f);
            }
            c.ring(o.add(0, 2.6 + hold * 2.6, 0), UP, r, 0.12F + hold * 0.1F, 0, TWO_PI, GOLD, (0.35F + 0.4F * hold) * f);
            if (hold > 0) {
                Vec3 core = o.add(0, 1.6, 0);
                // Rings of pressure closing in on them, faster and faster.
                for (int k = 0; k < 3; k++) {
                    float phase = (age * (0.03F + 0.09F * hold) + k / 3F) % 1;
                    c.ring(core, UP, (r - 0.6F) * (1 - phase) + 0.6F, 0.1F + 0.1F * hold, 0, TWO_PI, k == 0 ? HOT : GOLD,
                            0.75F * phase * (0.4F + 0.6F * hold) * f);
                }
                // Gold lightning leaping from the wall to the one being held, more often as it comes.
                int bolts = (int) (1 + hold * 5);
                for (int k = 0; k < bolts; k++) {
                    int flick = (int) (age / 2) + k * 13;
                    if (hash(seed + flick, k) > 0.35F + 0.5F * hold) continue;
                    float a0 = theta - hash(seed + flick, k + 3) * TWO_PI;
                    Vec3 wall = o.add(cos(a0) * r, 0.5 + hash(seed + flick, k + 7) * 2.2, sin(a0) * r);
                    c.bolt(wall, core.add(0, (hash(seed + flick, k + 9) - 0.5) * 0.8, 0), seed + flick, 0.35F, 0.04F + 0.03F * hold, HOT, 0.85F * f);
                }
                c.glow(core, 1.2F + 2.5F * hold, AMBER, 0.15F + 0.35F * hold);
            }
            // The heat dragged out: streams spiralling from the body out to the wall, behind the runner.
            for (int s = 0; s < 5; s++) {
                int n = 14;
                Vec3[] p = new Vec3[n + 1];
                float base = theta - 0.6F - s * TWO_PI / 5, h0 = 0.8F + hash(seed, s) * 1.2F;
                for (int k = 0; k <= n; k++) {
                    float u = k / (float) n, a = base - u * 1.6F, rr = 0.35F + u * (r - 0.35F);
                    p[k] = o.add(cos(a) * rr, h0 + Mth.sin(u * Mth.PI) * 0.4F, sin(a) * rr);
                }
                c.ribbon(p, 0.12F, s % 2 == 0 ? EMBER : AMBER, 0.75F * f, 0.1F * f);
            }
            // Motes of radiation pulled out after the heat.
            for (int m = 0; m < 10; m++) {
                float cycle = (age * 0.09F + hash(seed, m + 20)) % 1;
                float a = theta - 0.4F - cycle * 2.2F - m * 0.63F, rr = 0.3F + cycle * (r - 0.3F);
                c.glow(o.add(cos(a) * rr, 0.9 + hash(seed, m + 30) * 1.3, sin(a) * rr), 0.14F, RAD_GREEN, 0.8F * f * Mth.sin(cycle * Mth.PI));
            }
        }
        if (c.soft()) {
            for (int b = 0; b < 3; b++)
                arc(c, o, r * 0.94F, 0.6 + b * 0.8, theta - 2.6F + b, theta + b * 0.2F, 0.3F, WIND, 0.35F * f);
        }
    }

    // ------------------------------------------------------------------
    // Burning away
    // ------------------------------------------------------------------

    private static final java.util.Map<Integer, Long> DISSOLVING = new java.util.HashMap<>();

    /** Is this player burning away right now (so their body, or their corpse, is not drawn)? */
    public static boolean disintegrating(int entity) {
        Long until = DISSOLVING.get(entity);
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (until == null || level == null) return false;
        if (level.getGameTime() > until) { DISSOLVING.remove(entity); return false; }
        return true;
    }

    /** The body's parts, in blocks (width, height, depth, and the centre's x and y), facing +z. */
    private static final float[][] PARTS = {
            {0.5F, 0.5F, 0.5F, 0, 1.75F},           // head
            {0.5F, 0.75F, 0.25F, 0, 1.125F},        // body
            {0.25F, 0.75F, 0.25F, 0.375F, 1.125F},  // arms
            {0.25F, 0.75F, 0.25F, -0.375F, 1.125F},
            {0.25F, 0.75F, 0.25F, 0.125F, 0.375F},  // legs
            {0.25F, 0.75F, 0.25F, -0.125F, 0.375F}};
    private static final float CUBE = 0.125F;

    /** When the burning has eaten enough of a runner that their running body is gone and only cubes are left. */
    static final float CONSUMED = 50;

    /** Is this runner's running body burnt up (so only the cubes of them are drawn, not the body)? */
    public static boolean consumed(int entity) {
        Long at = CONSUMED_AT.get(entity);
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return at != null && level != null && level.getGameTime() >= at && disintegrating(entity);
    }
    private static final java.util.Map<Integer, Long> CONSUMED_AT = new java.util.HashMap<>();

    /**
     * A runner burning away, still running. Their body glows white-hot and cracks with gold light while their legs keep
     * going round the ring; then it comes apart from the head down, each glowing cube left behind on the ring where it
     * broke off, drifting up on the heat and cooling to ash. The runner slows as they burn, and the last of them, their
     * legs, crumbles where they come to a stop.
     */
    static void disintegrate(Canvas c, Active a, float t) {
        if (a.entity() >= 0 && c.level != null) {
            DISSOLVING.put(a.entity(), Math.max(DISSOLVING.getOrDefault(a.entity(), 0L), a.start() + a.piece().ticks + 40));
            CONSUMED_AT.putIfAbsent(a.entity(), a.start() + (long) CONSUMED);
            if (CONSUMED_AT.size() > 64) CONSUMED_AT.clear();
        }
        // The runner on the ring at any moment of the burning (or, if the ring is gone, where the server last saw them).
        SpeedCyclone.View view = null;
        for (SpeedCyclone.View v : SpeedCyclone.views())
            if (v.entity == a.entity() && v.failAt != Long.MIN_VALUE) { view = v; break; }
        final SpeedCyclone.View ring = view;
        Vec3 fallback = a.origin(), fallbackDir = a.facing();
        java.util.function.Function<Float, Vec3[]> runnerAt = u -> {
            if (ring == null) return new Vec3[] {fallback, fallbackDir};
            float age = a.start() + u - ring.start;
            float angle = ring.angle(age);
            return new Vec3[] {ring.runner(age), new Vec3(-sin(angle), 0, cos(angle))};
        };
        Vec3[] now = runnerAt.apply(t);
        Vec3 o = now[0], dir = now[1], side = new Vec3(dir.z, 0, -dir.x);
        int seed = a.seed();
        float life = a.piece().ticks;
        int i = 0;
        for (float[] part : PARTS) {
            int nx = Math.round(part[0] / CUBE), ny = Math.round(part[1] / CUBE), nz = Math.round(part[2] / CUBE);
            for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++, i++) {
                float lx = part[3] - part[0] / 2 + (x + 0.5F) * CUBE, ly = part[4] - part[1] / 2 + (y + 0.5F) * CUBE, lz = -part[2] / 2 + (z + 0.5F) * CUBE;
                // Top first: the head goes, then the body, the legs last of all.
                float release = 20 + (2.0F - ly) * 22 + hash(seed, i) * 10;
                float flown = t - release;
                Vec3 at;
                float heat, size;
                if (flown < 0) {
                    // Still part of them. While their body still runs, the body itself is drawn; after, these cubes are.
                    if (t < CONSUMED) continue;
                    at = o.add(side.scale(lx)).add(0, ly, 0).add(dir.scale(lz));
                    heat = 1;
                    size = CUBE;
                } else {
                    // Left behind on the ring where it broke off, drifting up and cooling.
                    Vec3[] then = runnerAt.apply(release);
                    Vec3 d0 = then[1], s0 = new Vec3(d0.z, 0, -d0.x);
                    Vec3 home = then[0].add(s0.scale(lx)).add(0, ly, 0).add(d0.scale(lz));
                    Vec3 out = new Vec3(lx, 0, lz).lengthSqr() < 1e-4 ? s0 : s0.scale(lx).add(d0.scale(lz)).normalize();
                    float swirl = flown * 0.08F + hash(seed, i + 300) * TWO_PI;
                    at = home.add(out.scale(flown * (0.02 + 0.02 * hash(seed, i + 100))))
                            .add(cos(swirl) * flown * 0.009, flown * (0.03 + 0.03 * hash(seed, i + 200)), sin(swirl) * flown * 0.009);
                    heat = 1 - Mth.clamp(flown / 34, 0, 1);
                    size = CUBE * (1 - Mth.clamp(flown / 44, 0, 1)) * 0.9F;
                }
                if (size <= 0.004F) continue;
                if (c.solid()) {
                    float[] col = Canvas.mix(ASH, mix(EMBER, HOT, heat), heat);
                    c.box(at, size, size, size, Math.max(0, flown) * 0.2F + i, Math.max(0, flown) * 0.15F, i, col, 1);
                }
                if (c.glow() && heat > 0.05F && (i % 2 == 0 || flown < 0))
                    c.glow(at, size * 2.4F, flown > 0 ? AMBER : HOT, 0.5F * heat);
            }
        }
        Vec3 body = o.add(0, 1.1, 0);
        if (c.glow()) {
            // The running body burning: a halo that follows them, gold cracks over them, a flash as they start to go.
            float burning = Mth.clamp(t / 10, 0, 1) * (1 - Canvas.smooth(CONSUMED, CONSUMED + 30, t));
            c.glow(body, 1.3F, AMBER, 0.45F * burning);
            float crack = 1 - Canvas.smooth(CONSUMED - 10, CONSUMED + 10, t);
            for (int k = 0; k < 6 && crack > 0; k++) {
                if (t < k * 3) continue;
                Vec3 p0 = o.add(side.scale((hash(seed, k + 400) - 0.5) * 0.5)).add(0, 0.2 + hash(seed, k + 410) * 1.7, 0);
                Vec3 p1 = p0.add(side.scale((hash(seed, k + 420) - 0.5) * 0.6)).add(0, (hash(seed, k + 430) - 0.5) * 0.8, 0);
                c.bolt(p0, p1, seed + k + (int) (t / 2), 0.2F, 0.03F, GOLD, 0.9F * crack * Mth.clamp((t - k * 3) / 4, 0, 1));
            }
            float flash = Canvas.smooth(16, 22, t) * (1 - Canvas.smooth(22, 32, t));
            if (flash > 0) c.glow(body, 2.5F, HOT, 0.8F * flash);
        }
        if (c.soft()) {
            // Ash smoke left hanging along the ring behind them.
            CloudMesh.Shade ash = Shades.smoke(c, ASH, 0);
            for (int k = 0; k < 9; k++) {
                float start = 24 + k * 6;
                if (t < start) continue;
                float age = t - start, k2 = Mth.clamp(age / 30, 0, 1);
                Vec3 left = runnerAt.apply(start)[0].add(0, 1.1, 0);
                Vec3 at = left.add((hash(seed, k + 500) - 0.5) * 0.8, age * 0.06 + hash(seed, k + 510) * 0.6, (hash(seed, k + 520) - 0.5) * 0.8);
                c.cloud(at, 0.3F + k2 * 0.7F, 0.25F + k2 * 0.5F, 0.3F + k2 * 0.7F, k, age * 0.03F, 0.45F * (1 - k2) * Mth.clamp((life - t) / 10, 0, 1), ash);
            }
        }
    }

    /** One streak of the air rushing on: it runs ahead, spreads and fades. */
    private static void streak(Canvas c, Vec3 o, Vec3 dir, Vec3 side, int seed, int i, float t, float s, float life, float[] col, float alpha) {
        float lat = (hash(seed, i + 200) - 0.5F) * 2.4F, h = 0.2F + hash(seed, i + 220) * 1.9F, pace = 0.8F + hash(seed, i + 240) * 0.5F;
        float head = (2 + 6 * s) * pace * (1 - (float) Math.exp(-t / 6)) + 0.5F, tail = Math.max(0, head - 2.6F * (1 - t / life) - 0.3F);
        int n = 8;
        Vec3[] p = new Vec3[n + 1];
        for (int k = 0; k <= n; k++) {
            float d = tail + (head - tail) * k / n;
            p[k] = o.add(dir.scale(d)).add(side.scale(lat * (1 + d * 0.12))).add(0, h + d * 0.03, 0);
        }
        c.ribbon(p, 0.05F, col, 0, alpha);
    }

    // ------------------------------------------------------------------
    // Outrunning the last minute
    // ------------------------------------------------------------------

    private static Vec3 heading(SpeedOverrunClient.State s, Entity e) {
        if (s.path.size() >= 2) {
            List<Vec3> path = new ArrayList<>(s.path);
            Vec3 d = path.getLast().subtract(path.get(Math.max(0, path.size() - 4))).multiply(1, 0, 1);
            if (d.lengthSqr() > 1e-4) return d.normalize();
        }
        Vec3 look = e.getLookAngle().multiply(1, 0, 1);
        return look.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : look.normalize();
    }

    /**
     * The wake and the breach. The wake: time smearing round the runner, violet and gold streams along the path
     * just run, a clock face at their feet turning backward faster and faster, and (when it is strong) rings in
     * the air ahead rushing at them. The breach: a ring of light opening in the air ahead with the clock's marks
     * on it, hands spinning backward, a tunnel of rings going down into it and the air streaming in; it comes on
     * toward the runner until they pass through it.
     */
    private static void overrun(Canvas c, SpeedOverrunClient.State s, long now) {
        if (!c.glow() && !c.soft()) return;
        Entity e = c.level.getEntity(s.entity);
        if (e == null) return;
        float w = s.wake(c.partial), b = s.breach(now, c.partial);
        float k = smooth(0.25F, 0.85F, w);
        if (k <= 0.01F && b < 0) return;
        Vec3 at = e.getPosition(c.partial), dir = heading(s, e), side = new Vec3(-dir.z, 0, dir.x);
        float time = now + c.partial;
        if (c.glow()) {
            List<Vec3> path = new ArrayList<>(s.path);
            path.add(at);
            int n = path.size();
            if (n >= 3) for (int strand = 0; strand < 3; strand++) {
                double[] x = new double[n], y = new double[n], z = new double[n];
                float[] a = new float[n], wd = new float[n];
                for (int i = 0; i < n; i++) {
                    float q = i / (float) (n - 1), twist = i * 0.6F + time * 0.5F + strand * 2.1F;
                    Vec3 p = path.get(i);
                    Vec3 next = path.get(Math.min(n - 1, i + 1)), prev = path.get(Math.max(0, i - 1));
                    Vec3 d = next.subtract(prev).multiply(1, 0, 1);
                    Vec3 sd = d.lengthSqr() < 1e-6 ? side : new Vec3(-d.z, 0, d.x).normalize();
                    x[i] = p.x + sd.x * sin(twist) * 0.45; y[i] = p.y + 0.5 + strand * 0.5 + cos(twist) * 0.2; z[i] = p.z + sd.z * sin(twist) * 0.45;
                    // Faded where it passes the camera, so the runner's own view is not striped by it.
                    double dx = x[i] - c.cx, dy = y[i] - c.cy, dz = z[i] - c.cz;
                    float clear = Mth.clamp(((float) Math.sqrt(dx * dx + dy * dy + dz * dz) - 1.2F) / 2.5F, 0, 1);
                    a[i] = 0.85F * k * q * q * clear; wd[i] = 0.08F + 0.12F * q;
                }
                c.ribbon(x, y, z, a, wd, strand == 1 ? GOLD : strand == 0 ? VIOLET : PALE);
            }
            // The clock at their feet, turning backward.
            Vec3 foot = at.add(0, 0.08, 0);
            float turn = -time * (0.05F + 0.25F * k);
            c.ring(foot, 1.3F, 0.08F, VIOLET, 0.5F * k);
            c.ring(foot, 1.6F, 0.25F, DEEP, 0.25F * k);
            for (int m = 0; m < 12; m++) c.spoke(foot, UP, turn + m * TWO_PI / 12, 1.12F, 1.42F, 0.04F, PALE, 0.6F * k);
            c.spoke(foot, UP, turn * 12, 0, 1.0F, 0.05F, GOLD, 0.7F * k);
            c.spoke(foot, UP, turn, 0, 0.7F, 0.07F, GOLD, 0.7F * k);
            // Glints shed behind, drifting back the way they came.
            for (int g = 0; g < 10 && n > 2; g++) {
                int idx = (int) (hash(s.entity, g) * (n - 1));
                Vec3 p = path.get(idx).add((hash(s.entity, g + 20) - 0.5) * 1.2, 0.3 + hash(s.entity, g + 40) * 1.6, (hash(s.entity, g + 60) - 0.5) * 1.2);
                c.glow(p, 0.16F, g % 2 == 0 ? PALE : GOLD, 0.6F * k);
            }
            // Strong enough, and the air ahead starts to ring.
            float ahead = Mth.clamp((k - 0.55F) / 0.45F, 0, 1);
            if (ahead > 0 && b < 0) for (int j = 0; j < 3; j++) {
                float ph = ((time * 0.06F + j / 3F) % 1);
                Vec3 p = at.add(0, 1.2, 0).add(dir.scale(1 + 7 * (1 - ph)));
                c.ring(p, dir, 1.4F + 1.4F * ph, 0.12F, 0, TWO_PI, j % 2 == 0 ? CYAN : VIOLET, 0.35F * ahead * sin(ph * PI));
            }
        }
        if (b >= 0) breach(c, s, at, dir, side, b, time);
    }

    private static void breach(Canvas c, SpeedOverrunClient.State s, Vec3 at, Vec3 dir, Vec3 side, float b, float time) {
        float q = Mth.clamp(b / SpeedOverrun.BREACH_TICKS, 0, 1);
        float open = smooth(0, 8, b), radius = 2.6F * open * (1 + 0.08F * sin(b * 0.3F));
        Vec3 p = at.add(0, 1.1, 0).add(dir.scale(13 * (1 - q) + 1.2));
        Vec3[] basis = across(dir);
        if (c.soft()) {
            c.disc(p, dir, radius * 1.05F, DEEP, 0.7F * open, 0.1F * open);
            // The air streaming in from round the runner.
            for (int i = 0; i < 14; i++) {
                float lat = (hash(s.entity, i + 600) - 0.5F) * 9, up = hash(s.entity, i + 620) * 3.2F, back = hash(s.entity, i + 640) * 4;
                Vec3 from = at.add(side.scale(lat)).add(0, up, 0).subtract(dir.scale(back));
                Vec3 bend = from.lerp(p, 0.5).add(dir.scale(2));
                Vec3[] pts = new Vec3[10];
                for (int j = 0; j < 10; j++) {
                    float u = j / 9F;
                    pts[j] = from.scale((1 - u) * (1 - u)).add(bend.scale(2 * u * (1 - u))).add(p.scale(u * u));
                }
                c.ribbon(pts, 0.07F, WIND, 0, 0.35F * open);
            }
        }
        if (!c.glow()) return;
        c.glow(p, radius * 1.7F, PALE, 0.2F * open);
        c.glow(p, radius * 0.6F, HOT, 0.3F * q * open);
        c.ring(p, dir, radius, 0.25F, 0, TWO_PI, GOLD, 0.9F * open);
        c.ring(p, dir, radius * 0.78F, 0.1F, 0, TWO_PI, PALE, 0.7F * open);
        c.ring(p, dir, radius * 1.18F, 0.45F, 0, TWO_PI, VIOLET, 0.35F * open);
        float turn = -b * 0.05F;
        for (int m = 0; m < 12; m++) c.spoke(p, dir, turn + m * TWO_PI / 12, radius * 0.84F, radius * 0.98F, 0.06F, HOT, 0.8F * open);
        c.spoke(p, dir, -b * 0.5F, 0, radius * 0.62F, 0.06F, GOLD, 0.85F * open);
        c.spoke(p, dir, -b * 0.04F, 0, radius * 0.42F, 0.09F, GOLD, 0.85F * open);
        // The tunnel down into it, its rings coming up toward you.
        float slide = (b * 0.15F) % 1;
        for (int j = 0; j < 9; j++) {
            float ph = j + 1 - slide;
            c.ring(p.add(dir.scale(ph * 1.6)), dir, radius * (float) Math.pow(0.84, ph), 0.12F, 0, TWO_PI, j % 2 == 0 ? VIOLET : CYAN,
                    0.5F * open * (1 - ph / 10));
        }
        int flick = (int) (b / 2);
        for (int j = 0; j < 2; j++) {
            float a0 = hash(s.entity + flick, j) * TWO_PI, a1 = a0 + 0.5F + hash(s.entity + flick, j + 3) * 0.9F;
            c.bolt(on(p, basis, a0, radius), on(p, basis, a1, radius), s.entity + flick * 5 + j, 0.25F, 0.04F, HOT, 0.8F * open);
        }
    }

    /** Leaving: the runner smeared into the breach, the breach slamming shut in a flash, a ripple of time spreading out. */
    static void depart(Canvas c, Active a, float t) {
        Vec3 o = a.origin(), dir = a.facing(), p = o.add(0, 1.1, 0).add(dir.scale(1.2));
        if (c.glow()) {
            float smear = Mth.clamp(1 - t / 10, 0, 1);
            c.line(o.add(0, 1, 0), o.add(0, 1, 0).add(dir.scale(2 + t * 3)), 0.5F * smear, GOLD, 0.8F * smear, 0);
            c.line(o.add(0, 1, 0), o.add(0, 1, 0).add(dir.scale(1 + t * 2)), 0.15F * smear, HOT, 0.9F * smear, 0);
            float shut = 1 - smooth(0, 9, t), radius = 2.6F * shut;
            if (shut > 0.01F) {
                c.ring(p, dir, radius, 0.25F, 0, TWO_PI, GOLD, 0.9F);
                c.ring(p, dir, radius * 1.18F, 0.45F, 0, TWO_PI, VIOLET, 0.4F);
                for (int m = 0; m < 12; m++) c.spoke(p, dir, -t * 0.3F + m * TWO_PI / 12, radius * 0.84F, radius * 0.98F, 0.06F, HOT, 0.8F);
            }
            float flash = t < 7 ? 0 : Math.max(0, 1 - Math.abs(t - 10) / 4);
            c.glow(p, 6 * flash, HOT, 0.8F * flash);
            if (t >= 8 && t < 18) for (int i = 0; i < 6; i++) {
                float ang = i * TWO_PI / 6 + a.seed();
                c.bolt(p, p.add(cos(ang) * 4, (hash(a.seed(), i) - 0.5) * 3, sin(ang) * 4), a.seed() + i + (int) t, 0.5F, 0.05F, PALE, 0.8F * (1 - (t - 8) / 10));
            }
        }
        if (c.soft() && t > 8) {
            float k = smooth(8, 42, t), r = 1 + k * 9;
            c.shell(p, r, r * 0.7F, r, PI, VIOLET, 0.35F * (1 - k), 0.05F, Canvas.PLAIN, 0);
        }
    }

    /**
     * Arriving a minute ago: a column of light, a flash, a ring of wind on the ground and ripples of time
     * spreading, lightning across the ground, light drawn in, and a clock face under the runner whose hands
     * spin backward and wind down to rest before it fades.
     */
    static void arrive(Canvas c, Active a, float t) {
        float life = a.piece().ticks;
        Vec3 o = a.origin();
        int seed = a.seed();
        if (c.glow()) {
            float beam = Mth.clamp(1 - t / 12, 0, 1);
            c.line(o, o.add(0, 14, 0), 1.2F * beam, PALE, 0.9F * beam, 0);
            c.line(o, o.add(0, 10, 0), 0.35F * beam, GOLD, beam, 0);
            c.glow(o.add(0, 1, 0), 4 * Mth.clamp(1 - t / 8, 0, 1), HOT, 0.8F * Mth.clamp(1 - t / 8, 0, 1));
            float shock = Mth.clamp(1 - t / 16, 0, 1);
            c.ring(o.add(0, 0.1, 0), 1 + t * 0.9F, 0.5F, WIND, 0.6F * shock);
            if (t < 14) for (int i = 0; i < 8; i++) {
                float ang = i * TWO_PI / 8 + hash(seed, i), reach = 3 + hash(seed, i + 10) * 2.5F;
                c.bolt(o.add(0, 0.12, 0), o.add(sin(ang) * reach, 0.12, cos(ang) * reach), seed + i + (int) (t / 2) * 9, 0.4F, 0.045F, GOLD, 0.9F * (1 - t / 14));
            }
            float dial = Mth.clamp(t / 6, 0, 1) * Mth.clamp((life - t) / 30, 0, 1);
            if (dial > 0.01F) {
                Vec3 face = o.add(0, 0.07, 0);
                c.ring(face, 2.4F, 0.1F, GOLD, 0.75F * dial);
                c.ring(face, 2.0F, 0.05F, PALE, 0.45F * dial);
                c.ring(face, 2.8F, 0.35F, VIOLET, 0.25F * dial);
                for (int m = 0; m < 12; m++) c.spoke(face, UP, m * TWO_PI / 12, 2.05F, 2.35F, m % 3 == 0 ? 0.07F : 0.04F, PALE, 0.8F * dial);
                // Spun back hard, winding down to rest.
                float wound = 1 - (float) Math.exp(-t / 16);
                float hour = 1.3F - wound * TWO_PI, minute = 0.4F - wound * TWO_PI * 12;
                c.spoke(face, UP, minute, 0, 1.75F, 0.07F, GOLD, 0.9F * dial);
                c.spoke(face, UP, hour, 0, 1.1F, 0.11F, GOLD, 0.9F * dial);
                c.glow(face.add(0, 0.05, 0), 0.4F, HOT, 0.6F * dial);
            }
            float coil = Mth.clamp(1 - t / 60, 0, 1);
            for (int k = 0; k < 2; k++) c.helix(o, 3.2F, 1.5F, 0.4F, 2.2F, t * 0.3F + k * PI, 0.08F, k == 0 ? VIOLET : GOLD, 0.7F * coil, 32);
            for (int m = 0; m < 18; m++) {
                float start = hash(seed, m + 30) * 10, q = Mth.clamp((t - start) / 30, 0, 1);
                if (q <= 0 || q >= 1) continue;
                float ang = hash(seed, m + 50) * TWO_PI + q * 2.5F, rr = 6 * (1 - q * q);
                Vec3 mote = o.add(sin(ang) * rr, 1 + (hash(seed, m + 70) * 3 - 1) * (1 - q), cos(ang) * rr);
                c.glow(mote, 0.2F, m % 2 == 0 ? PALE : GOLD, 0.8F * sin(q * PI));
            }
        }
        if (c.soft()) {
            for (int k = 0; k < 2; k++) {
                float q = smooth(k * 6, k * 6 + 34, t), r = 0.5F + q * 8;
                if (q > 0 && q < 1) c.shell(o.add(0, 1, 0), r, r * 0.6F, r, PI, k == 0 ? VIOLET : CYAN, 0.3F * (1 - q), 0.05F, Canvas.PLAIN, 0);
            }
        }
    }

}
