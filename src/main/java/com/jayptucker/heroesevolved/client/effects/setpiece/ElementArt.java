package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/** Radiation, fire, ice, lightning, stone and sound. */
final class ElementArt {
    private ElementArt() {}

    private static final float[] RAD = rgb(0x7CFF6A), RAD_DEEP = rgb(0x2E8F2A), CHERENKOV = rgb(0x3FA8FF), CHERENKOV_PALE = rgb(0xBFE6FF),
            STEEL = rgb(0x4A5057), WHITE = {1, 1, 1};
    private static final float[] FIRE = rgb(0xFF7A1A), FLAME = rgb(0xFFC14A), EMBER = rgb(0xFF4A12), SOOT = rgb(0x3A332E);
    private static final float[] ICE = rgb(0xBFEFFF), ICE_DEEP = rgb(0x6FC8F0), FROST = rgb(0xE8FBFF), STORM = rgb(0x7C8699),
            AURORA_GREEN = rgb(0x5CFFB0), AURORA_VIOLET = rgb(0xB070FF);
    private static final float[] VOLT = rgb(0x9FD8FF), VOLT_CORE = rgb(0xE6F6FF), COPPER = rgb(0xB8733A);
    private static final float[] STONE = rgb(0x8A7F72), STONE_DARK = rgb(0x5E554C), MAGMA = rgb(0xFF8A2A), EARTH = rgb(0x9C8A70);
    private static final float[] SOUND = rgb(0xE6E0FF), SOUND_GLOW = rgb(0x8FD8FF), HYMN = rgb(0xFFD27A);

    // ------------------------------------------------------------------
    // Radiation
    // ------------------------------------------------------------------

    /** A mote flies to the point and hangs there, swelling with Sprague's light; then it goes off, the vision's blast in small. */
    static void groundZero(Canvas c, Active a, float t) {
        float charge = 30;
        Vec3 point = a.target().add(0, 1.6, 0);
        if (t < charge) {
            float fly = Mth.clamp(t / 8, 0, 1);
            Vec3 mote = a.origin().add(0, 1.4, 0).lerp(point, fly * fly * (3 - 2 * fly));
            MiniBlast.charge(c, mote, Mth.clamp((t - 8) / (charge - 8), 0, 1), t, 0.6F);
            if (c.glow() && fly < 1) c.line(a.origin().add(0, 1.4, 0), mote, 0.12F, RAD, 0, 0.8F);
            if (c.glow()) c.ring(a.target().add(0, 0.05, 0), a.scale() * Mth.clamp(t / charge, 0, 1), 0.1F, RAD, 0.5F * fly);
            return;
        }
        MiniBlast.draw(c, a.target(), a.scale() / 80F, a.scale() * 1.25F, t - charge, Piece().ticks - charge);
    }

    private static com.jayptucker.heroesevolved.styles.Piece Piece() { return com.jayptucker.heroesevolved.styles.Piece.GROUND_ZERO; }

    /** Lead-glass panels lock together from the ground up into a dome, its seams glowing green. */
    static void containment(Canvas c, Active a, float t) {
        float life = a.piece().ticks, r = a.scale();
        float grow = smooth(0, 14, t), end = Mth.clamp((life - t) / 14, 0, 1);
        float flicker = end < 1 ? (Canvas.hash(a.seed(), (int) t) > 0.35F ? 1 : 0.3F) : 1;
        float cut = Mth.HALF_PI * (1 - grow);
        Pattern assemble = (theta, phi) -> phi < cut ? 0 : hexes(7, 0.07F, 0.18F).at(theta, phi);
        Vec3 o = a.origin();
        if (c.soft()) c.shell(o, r, r * 0.92F, r, Mth.HALF_PI, RAD_DEEP, 0.3F * end * flicker, 0.4F, assemble, 0);
        if (c.glow()) {
            c.shell(o, r, r * 0.92F, r, Mth.HALF_PI, RAD, 0.55F * end * flicker, 0.6F, assemble, 0);
            c.ring(o.add(0, 0.06, 0), r, 0.18F, RAD, 0.8F * end);
            // A slow pulse climbing the dome.
            float h = (t * 0.04F) % 1;
            c.ring(o.add(0, Mth.sin(h * Mth.HALF_PI) * r * 0.92F, 0), r * Mth.cos(h * Mth.HALF_PI), 0.12F, RAD, 0.5F * end * grow * (1 - h));
        }
    }

    /** A reactor's heart at their feet: a column of Cherenkov blue among turning fuel rods, its light pulsing outward. */
    static void reactor(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 10, 20), r = a.scale();
        Vec3 o = a.origin();
        float rise = smooth(0, 12, t);
        if (c.solid()) for (int i = 0; i < 8; i++) {
            float ang = i * Mth.TWO_PI / 8 + t * 0.03F;
            float h = 2.6F * rise * (Mth.clamp((life - t) / 20, 0, 1));
            if (h > 0.05F) c.box(o.add(Mth.cos(ang) * 1.25F, h / 2, Mth.sin(ang) * 1.25F), 0.09F, h / 2, 0.09F, -ang, 0, 0, STEEL, 1);
        }
        if (c.glow()) {
            c.tube(o, 0.55F, 3.4F * rise, CHERENKOV, 0.7F * f, 0, PLAIN, t * 0.1F);
            c.line(o, o.add(0, 3.6F * rise, 0), 0.35F, CHERENKOV_PALE, 0.9F * f, 0.1F * f);
            c.glow(o.add(0, 1.4, 0), 2.6F, CHERENKOV, 0.5F * f);
            c.disc(o.add(0, 0.05, 0), UP, r, CHERENKOV, 0.35F * f, 0);
            for (int k = 0; k < 2; k++) {
                float p = ((t + k * 20) % 40) / 40;
                c.ring(o.add(0, 0.08, 0), r * p, 0.14F, CHERENKOV_PALE, 0.6F * f * (1 - p));
            }
        }
    }

    // ------------------------------------------------------------------
    // Fire
    // ------------------------------------------------------------------

    /** A small sun falls out of the sky onto the point, and a pillar of fire stands where it lands. */
    /** Where Starfall's star is {@code k} of the way down (0 high in the sky, 1 at the ground), falling faster and faster. */
    private static Vec3 star(Active a, float k) {
        Vec3 ground = a.target().add(0, 1.2, 0);
        Vec3 sky = a.target().add(-a.facing().x * 30, 80, -a.facing().z * 30);
        return sky.lerp(ground, k * k);
    }

    static void sunfall(Canvas c, Active a, float t) {
        float fall = com.jayptucker.heroesevolved.styles.StyleCasts.STARFALL_FALL, r = a.scale();
        Vec3 ground = a.target();
        if (t < fall) {
            float k = t / fall;
            Vec3 sun = star(a, k), dir = star(a, Math.min(1, k + 0.02F)).subtract(sun);
            dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : dir.normalize();
            if (c.solid()) {
                c.cloud(sun, 1.6F, 1.6F, 1.6F, a.seed(), t * 0.2F, 1, Shades.fire(1));
                // The tail: burning billows strung back up the way it came, cooling into smoke.
                for (int i = 1; i <= 12; i++) {
                    float kk = Math.max(0, k - i * 0.022F);
                    Vec3 p = star(a, kk);
                    float size = 1.4F - i * 0.08F;
                    c.cloud(p, size, size, size, a.seed() + i, t * 0.1F + i, 1, i < 7 ? Shades.fire(1 - i * 0.1F) : Shades.smoke(c, SOOT, 0.35F));
                }
            }
            if (c.glow()) {
                c.glow(sun, 3.2F, FLAME, 0.95F);
                c.glow(sun, 9, FIRE, 0.5F);
                // A streak of light behind it, all the way up.
                Vec3 tail = star(a, Math.max(0, k - 0.35F));
                c.line(tail, sun, 1.6F, FIRE, 0, 0.55F);
                c.line(tail, sun, 0.4F, FLAME, 0, 0.9F);
                for (int i = 0; i < 8; i++) {
                    float ang = i * Mth.TWO_PI / 8 + t * 0.15F;
                    c.line(sun, sun.add(Mth.cos(ang) * 3.2, Mth.sin(ang) * 3.2, Mth.sin(ang * 1.7F) * 1.2).subtract(dir.scale(2)), 0.22F, FLAME, 0.7F, 0);
                }
                // The ground below it warms and glows as it comes.
                c.ring(ground.add(0, 0.06, 0), r * (0.4F + 0.6F * k), 0.12F, FIRE, 0.6F * k);
                c.disc(ground.add(0, 0.05, 0), Canvas.UP, r * 0.9F, FIRE, 0.35F * k * k, 0);
            }
            return;
        }
        float s = t - fall, life = a.piece().ticks - fall;
        float end = Mth.clamp((life - s) / 25, 0, 1);
        if (c.solid() && end >= 1 || c.soft() && end < 1) {
            float alpha = end >= 1 ? 1 : end;
            for (int i = 0; i < 9; i++) {
                float rise = s * (0.25F + 0.05F * i), size = (1.6F - i * 0.08F) * (0.6F + 0.4F * smooth(0, 6, s));
                float heat = Mth.clamp(1 - s / 40 - i * 0.05F, 0.1F, 1);
                c.cloud(ground.add(Mth.sin(i * 2.1F) * 0.4, 0.8 + i * 0.9 + rise * 0.3, Mth.cos(i * 2.1F) * 0.4), size, size * 1.2F, size,
                        a.seed() + i, s * 0.05F + i, alpha, heat > 0.3F ? Shades.fire(heat) : Shades.smoke(c, SOOT, heat));
            }
            for (int i = 0; i < 14; i++) {
                float ang = i * Mth.TWO_PI / 14, rr = r * (1 - (float) Math.exp(-s / 4)) * (0.85F + 0.15F * hash(a.seed(), i));
                float size = 0.7F + 0.4F * hash(a.seed(), i + 30);
                c.cloud(ground.add(Mth.cos(ang) * rr, size * 0.5, Mth.sin(ang) * rr), size, size * 0.8F, size, a.seed() + 40 + i, s * 0.08F,
                        alpha, Shades.fire(Mth.clamp(0.9F - s / 50, 0.25F, 0.9F)));
            }
        }
        if (c.glow()) {
            if (s < 5) c.glow(ground.add(0, 2, 0), 10 + s * 4, WHITE, 1 - s / 5);
            c.glow(ground.add(0, 2.5, 0), 6, FIRE, 0.6F * end);
            c.disc(ground.add(0, 0.06, 0), UP, r * 1.1F, EMBER, 0.45F * end, 0);
            c.ring(ground.add(0, 0.06, 0), r * (1 - (float) Math.exp(-s / 4)), 0.3F, FLAME, 0.8F * end);
        }
    }

    /** Flame wound round them in three turning strands, embers lifting off it. */
    static void emberShroud(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 8, 16);
        Vec3 o = a.at(c);
        for (int k = 0; k < 3; k++) {
            float phase = k * Mth.TWO_PI / 3 + t * 0.22F;
            c.helix(o, 2.3F, 0.95F, 0.55F, 1.3F, phase, 0.22F, k == 1 ? FLAME : FIRE, 0.85F * f, 28);
            c.helix(o, 2.3F, 0.95F, 0.55F, 1.3F, phase, 0.08F, WHITE, 0.4F * f, 28);
        }
        c.glow(o.add(0, 1, 0), 2.2F, FIRE, 0.35F * f);
        for (int i = 0; i < 14; i++) {
            float k = ((t * 0.03F + hash(a.seed(), i)) % 1);
            float ang = hash(a.seed(), i + 20) * Mth.TWO_PI + k * 2;
            c.glow(o.add(Mth.cos(ang) * 0.9, 0.2 + k * 3, Mth.sin(ang) * 0.9), 0.12F, FLAME, f * (1 - k));
        }
    }

    /** A hearth: glowing coals in a ring and a tall twisting flame, embers rising and a warm light around it. */
    static void hearthfire(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 12, 24), r = a.scale();
        Vec3 o = a.origin();
        if (c.solid()) for (int i = 0; i < 10; i++) {
            float ang = i * Mth.TWO_PI / 10;
            c.cloud(o.add(Mth.cos(ang) * 0.75, 0.15, Mth.sin(ang) * 0.75), 0.28F, 0.18F, 0.28F, a.seed() + i, i, 1,
                    Shades.fire(0.35F + 0.15F * Mth.sin(t * 0.2F + i)));
        }
        if (!c.glow()) return;
        float height = 3.2F * smooth(0, 14, t) * (0.9F + 0.1F * Mth.sin(t * 0.5F));
        for (int k = 0; k < 5; k++) {
            float phase = k * Mth.TWO_PI / 5 + t * 0.18F;
            c.helix(o.add(0, 0.1, 0), height * (0.7F + 0.06F * k), 0.6F, 0.05F, 0.6F, phase, 0.32F - k * 0.03F, k < 2 ? FIRE : FLAME, 0.75F * f, 18);
        }
        c.glow(o.add(0, 1.0, 0), 1.6F, WHITE, 0.4F * f);
        c.glow(o.add(0, 1.2, 0), 4.5F, FIRE, 0.45F * f);
        c.disc(o.add(0, 0.05, 0), UP, r, FLAME, 0.22F * f, 0);
        c.ring(o.add(0, 0.06, 0), r, 0.1F, FLAME, 0.4F * f);
        for (int i = 0; i < 20; i++) {
            float k = (t * 0.025F + hash(a.seed(), i)) % 1, ang = hash(a.seed(), i + 40) * Mth.TWO_PI;
            c.glow(o.add(Mth.cos(ang) * 0.5 * (1 + k), 0.5 + k * 5, Mth.sin(ang) * 0.5 * (1 + k)), 0.1F, FLAME, f * (1 - k));
        }
    }

    // ------------------------------------------------------------------
    // Ice
    // ------------------------------------------------------------------

    /** A storm cloud gathers over the point and hammers it with hail. */
    static void hailstorm(Canvas c, Active a, float t) {
        float life = a.piece().ticks, r = a.scale(), form = smooth(0, 16, t), end = Mth.clamp((life - t) / 20, 0, 1);
        Vec3 o = a.target();
        if (c.solid() && end >= 1 || c.soft() && end < 1) {
            for (int i = 0; i < 16; i++) {
                float ang = i * 2.39996F, rr = r * 0.9F * Mth.sqrt((i + 0.5F) / 16);
                float size = (1.6F + hash(a.seed(), i) * 1.2F) * form;
                c.cloud(o.add(Mth.cos(ang) * rr, 10 + hash(a.seed(), i + 9) * 1.5, Mth.sin(ang) * rr), size * 1.3F, size * 0.8F, size * 1.3F,
                        a.seed() + i, t * 0.01F + i, end >= 1 ? 1 : end, Shades.smoke(c, STORM, 0));
            }
        }
        for (int i = 0; i < 48; i++) {
            float start = 14 + hash(a.seed(), i * 3) * (life - 36);
            float k = (t - start) / 8;
            if (k < 0 || k > 1.6F) continue;
            float ang = hash(a.seed(), i * 3 + 1) * Mth.TWO_PI, rr = r * Mth.sqrt(hash(a.seed(), i * 3 + 2));
            Vec3 ground = o.add(Mth.cos(ang) * rr, 0, Mth.sin(ang) * rr);
            if (k <= 1) {
                Vec3 p = ground.add(0, 9 * (1 - k), 0);
                if (c.solid()) c.shard(p.add(0, 0.35, 0), p.add(0, -0.45, 0), 0.3F, 5, i, ICE, 1);
                if (c.soft()) c.line(p, p.add(0, 2.8, 0), 0.1F, FROST, 0.7F, 0);
            } else if (c.glow()) {
                float burst = (k - 1) / 0.6F;
                c.ring(ground.add(0, 0.08, 0), 0.3F + burst * 1.4F, 0.1F, FROST, 0.8F * (1 - burst));
            }
        }
        if (c.glow()) c.disc(o.add(0, 0.05, 0), UP, r, ICE_DEEP, 0.18F * form * end, 0);
    }

    /** Crystal pillars burst up in a ring around them, mist at their feet; at the end they shatter. */
    static void bastion(Canvas c, Active a, float t) {
        float life = a.piece().ticks, r = a.scale();
        Vec3 o = a.origin();
        int pillars = 8;
        float shatter = Mth.clamp((t - (life - 14)) / 14, 0, 1);
        for (int i = 0; i < pillars; i++) {
            float ang = i * Mth.TWO_PI / pillars + 0.2F;
            float rise = Mth.clamp((t - i * 0.6F) / 6, 0, 1);
            float overshoot = rise < 1 ? rise * (1.15F - 0.15F * rise) : 1;
            float h = (2.6F + 0.8F * hash(a.seed(), i)) * overshoot;
            Vec3 base = o.add(Mth.cos(ang) * r, -0.3, Mth.sin(ang) * r);
            Vec3 tip = base.add(Mth.cos(ang) * 0.4, h, Mth.sin(ang) * 0.4);
            if (shatter <= 0) {
                if (c.solid()) c.shard(base, tip, 0.42F, 6, ang, ICE, 1);
                if (c.soft()) c.shard(base, tip.add(0, 0.2, 0), 0.62F, 6, ang + 0.4F, FROST, 0.3F);
            } else if (c.solid()) {
                for (int k = 0; k < 5; k++) {
                    float fly = shatter * (2 + 2 * hash(a.seed(), i * 7 + k));
                    float out = ang + (hash(a.seed(), i * 9 + k) - 0.5F);
                    Vec3 p = base.add(0, h * (0.2 + 0.18 * k), 0).add(Mth.cos(out) * fly, -shatter * shatter * 3, Mth.sin(out) * fly);
                    if (p.y > o.y - 0.5) c.shard(p, p.add(Mth.cos(out) * 0.4, 0.5, Mth.sin(out) * 0.4), 0.16F, 4, k, ICE, 1);
                }
            }
            if (c.glow()) c.glow(base.add(0, 0.5, 0), 1.2F, ICE_DEEP, 0.3F * (1 - shatter));
        }
        if (c.glow()) c.ring(o.add(0, 0.06, 0), r, 0.4F, FROST, 0.35F * (1 - shatter) * smooth(0, 8, t));
    }

    /** Curtains of aurora ripple overhead, snow drifting down through them. */
    static void aurora(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 20, 30), r = a.scale();
        Vec3 o = a.origin();
        for (int k = 0; k < 3; k++) {
            int cols = 34;
            for (int j = 0; j <= cols; j++) {
                float u = j / (float) cols;
                float ang = k * 2.1F + (u - 0.5F) * 2.6F + Mth.sin(t * 0.03F + k) * 0.2F;
                float rr = r * (0.45F + 0.25F * k) + Mth.sin(u * 9 + t * 0.08F + k) * 0.8F;
                double x = o.x + Mth.cos(ang) * rr, z = o.z + Mth.sin(ang) * rr;
                float bottom = 6.5F + Mth.sin(u * 7 + t * 0.06F) * 0.6F, top = bottom + 4.5F + 1.5F * Mth.sin(u * 5 + k);
                float edge = Mth.sin(u * Mth.PI);
                float shimmer = 0.6F + 0.4F * Mth.sin(u * 23 + t * 0.25F + k * 3);
                Vec3 b = new Vec3(x, o.y + bottom, z), m = new Vec3(x, o.y + (bottom + top) / 2, z), tp = new Vec3(x, o.y + top, z);
                c.line(b, m, 0.32F, AURORA_GREEN, 0.5F * f * edge * shimmer, 0.25F * f * edge);
                c.line(m, tp, 0.32F, AURORA_VIOLET, 0.25F * f * edge * shimmer, 0);
            }
        }
        for (int i = 0; i < 40; i++) {
            float k = (t * 0.012F + hash(a.seed(), i)) % 1, ang = hash(a.seed(), i + 50) * Mth.TWO_PI, rr = r * Mth.sqrt(hash(a.seed(), i + 90));
            c.glow(o.add(Mth.cos(ang) * rr + Mth.sin(t * 0.05F + i) * 0.3, 7 * (1 - k), Mth.sin(ang) * rr), 0.09F, FROST, f * 0.9F);
        }
        c.ring(o.add(0, 0.06, 0), r, 0.12F, AURORA_GREEN, 0.4F * f);
        c.disc(o.add(0, 0.05, 0), UP, r, AURORA_GREEN, 0.08F * f, 0.02F * f);
    }

    // ------------------------------------------------------------------
    // Lightning
    // ------------------------------------------------------------------

    /** A coil on a mast, its crown crackling with idle arcs. */
    static void tesla(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 6, 14), grow = smooth(0, 10, t);
        Vec3 o = a.target(), crown = o.add(0, 3.2 * grow, 0);
        if (c.solid()) {
            c.box(o.add(0, 0.25, 0), 0.55F, 0.25F, 0.55F, 0, 0, 0, STEEL, 1);
            c.box(o.add(0, 1.6 * grow + 0.4, 0), 0.14F, 1.6F * grow, 0.14F, 0, 0, 0, COPPER, 1);
            for (int i = 0; i < 12; i++) {
                float ang = i * Mth.TWO_PI / 12;
                c.box(crown.add(Mth.cos(ang) * 0.6, 0, Mth.sin(ang) * 0.6), 0.2F, 0.16F, 0.2F, -ang, 0, 0, STEEL, 1);
            }
        }
        if (c.glow()) {
            c.glow(crown, 1.4F, VOLT, 0.7F * f);
            int flick = (int) (t / 2);
            for (int k = 0; k < 4; k++) {
                float ang = hash(a.seed() + flick, k) * Mth.TWO_PI, len = 1.5F + 2 * hash(a.seed() + flick, k + 7);
                Vec3 end = crown.add(Mth.cos(ang) * len, (hash(a.seed() + flick, k + 3) - 0.6) * 2, Mth.sin(ang) * len);
                c.bolt(crown, end, a.seed() + flick * 13 + k, 0.35F, 0.05F, VOLT, 0.8F * f);
            }
            c.ring(o.add(0, 0.06, 0), a.scale(), 0.08F, VOLT, 0.3F * f);
        }
    }

    /** One strike from the coil's crown to whoever it chose, flickering. */
    static void teslaZap(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = 1 - t / a.piece().ticks;
        c.bolt(a.origin(), a.target(), a.seed() + (int) t, 0.45F, 0.09F, VOLT, f);
        c.glow(a.target(), 1.2F, VOLT_CORE, 0.8F * f);
    }

    /** A cage of lightning round them, its bars the edges of a twenty-sided shell, flickering as current runs. */
    static void faraday(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 6, 14);
        Vec3 o = a.at(c).add(0, 1, 0);
        float r = a.scale(), g = (1 + Mth.sqrt(5)) / 2;
        float[][] base = {{-1, g, 0}, {1, g, 0}, {-1, -g, 0}, {1, -g, 0}, {0, -1, g}, {0, 1, g}, {0, -1, -g}, {0, 1, -g},
                {g, 0, -1}, {g, 0, 1}, {-g, 0, -1}, {-g, 0, 1}};
        Vec3[] v = new Vec3[12];
        float spin = t * 0.04F, cs = Mth.cos(spin), sn = Mth.sin(spin);
        for (int i = 0; i < 12; i++) {
            float l = Mth.sqrt(base[i][0] * base[i][0] + base[i][1] * base[i][1] + base[i][2] * base[i][2]);
            float x = base[i][0] / l, z = base[i][2] / l;
            v[i] = o.add((x * cs - z * sn) * r, base[i][1] / l * r, (x * sn + z * cs) * r);
        }
        int flick = (int) (t / 2), edge = 0;
        for (int i = 0; i < 12; i++)
            for (int j = i + 1; j < 12; j++) {
                if (Math.abs(v[i].distanceTo(v[j]) - r * 1.0515) > r * 0.1) continue;
                float on = hash(a.seed() + flick, edge++) > 0.25F ? 1 : 0.3F;
                c.bolt(v[i], v[j], a.seed() + flick * 31 + edge, 0.12F * r, 0.035F, VOLT, 0.75F * f * on);
            }
        for (Vec3 p : v) c.glow(p, 0.25F, VOLT_CORE, 0.8F * f);
    }

    /** Current leaps from one friend to the next, and each one's heart takes it with a pulse of light. */
    static void jumpstart(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = 1 - t / a.piece().ticks;
        if (t < 6) c.bolt(a.origin().add(0, 1.2, 0), a.target().add(0, 1.2, 0), a.seed() + (int) t, 0.5F, 0.08F, VOLT, 1);
        Vec3 heart = a.target().add(0, 1.2, 0);
        for (int k = 0; k < 2; k++) {
            float p = Mth.clamp((t - k * 4) / 10, 0, 1);
            c.ring(heart, UP, 0.3F + p * 1.6F, 0.1F, 0, Mth.TWO_PI, VOLT_CORE, 0.9F * (1 - p) * (p > 0 ? 1 : 0));
        }
        c.glow(heart, 1.2F, VOLT, 0.8F * f);
    }

    // ------------------------------------------------------------------
    // Stone
    // ------------------------------------------------------------------

    /** The ground splits along a line, slabs heaving up on either side of a crack full of glowing rock. */
    static void fissure(Canvas c, Active a, float t) {
        float life = a.piece().ticks;
        Vec3 from = a.origin(), dir = a.facing();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        float length = (float) Math.min(a.scale(), a.target().subtract(from).horizontalDistance() + 0.01);
        float yaw = (float) Math.atan2(dir.z, dir.x);
        float close = Mth.clamp((t - (life - 16)) / 16, 0, 1);
        int steps = (int) length;
        for (int i = 1; i <= steps; i++) {
            float open = Mth.clamp((t - i * 0.9F) / 4, 0, 1) * (1 - close);
            if (open <= 0) continue;
            Vec3 p = from.add(dir.scale(i));
            for (int s = -1; s <= 1; s += 2) {
                float tilt = s * 0.6F * open * (0.8F + 0.4F * hash(a.seed(), i * 2 + s));
                if (c.solid()) c.box(p.add(side.scale(s * (0.55 + 0.3 * open))).add(0, 0.25 * open - 0.1, 0), 0.5F, 0.32F, 0.46F,
                        -yaw, 0, tilt, s > 0 ? STONE : STONE_DARK, 1);
            }
            float puff = Mth.clamp((t - i * 0.9F) / 18, 0, 1);
            if (c.soft() && puff < 1) c.cloud(p.add(0, 0.4 + puff * 1.5, 0), 0.7F + puff, 0.6F + puff, 0.7F + puff, a.seed() + i, i, 0.55F * (1 - puff),
                    Shades.smoke(c, EARTH, 0));
            if (c.glow()) {
                Vec3 q = from.add(dir.scale(Math.max(1, i - 1)));
                c.line(q.add(0, 0.05, 0), p.add(0, 0.05, 0), 0.32F * open, MAGMA, 0.9F * open, 0.9F * open);
                c.glow(p.add(0, 0.3, 0), 0.9F * open, MAGMA, 0.4F * open);
            }
        }
    }

    /** Slabs of rock burst up in an arc before them, dust at their feet; at the end they crumble back down. */
    static void rampart(Canvas c, Active a, float t) {
        float life = a.piece().ticks, r = a.scale();
        Vec3 o = a.origin(), dir = a.facing();
        float yaw = (float) Math.atan2(dir.z, dir.x);
        int slabs = 9;
        float sink = Mth.clamp((t - (life - 18)) / 18, 0, 1);
        for (int i = 0; i < slabs; i++) {
            float u = (i / (float) (slabs - 1) - 0.5F) * 2.6F;
            float ang = yaw + u, delay = Math.abs(i - slabs / 2) * 1.2F;
            float rise = Mth.clamp((t - delay) / 5, 0, 1);
            float h = 2.7F + 0.5F * hash(a.seed(), i);
            Vec3 at = o.add(Mth.cos(ang) * r, h / 2 - h * (1 - rise) - sink * h * 0.9, Mth.sin(ang) * r);
            if (c.solid() && rise > 0) c.box(at, 0.3F, h / 2, 0.62F, -ang, (hash(a.seed(), i + 20) - 0.5F) * 0.2F,
                    (hash(a.seed(), i + 40) - 0.5F) * 0.25F + sink * 0.4F, i % 2 == 0 ? STONE : STONE_DARK, 1);
            float puff = (t - delay) / 16;
            if (c.soft() && puff > 0 && puff < 1) c.cloud(o.add(Mth.cos(ang) * r, 0.3 + puff, Mth.sin(ang) * r), 0.8F + puff, 0.6F + puff,
                    0.8F + puff, a.seed() + i, i, 0.5F * (1 - puff), Shades.smoke(c, EARTH, 0));
            float fall = (t - (life - 18)) / 18;
            if (c.soft() && fall > 0 && fall < 1) c.cloud(o.add(Mth.cos(ang) * r, 0.5 + fall, Mth.sin(ang) * r), 0.9F + fall, 0.8F, 0.9F + fall,
                    a.seed() + 60 + i, i, 0.45F * (1 - fall), Shades.smoke(c, EARTH, 0));
        }
    }

    /** The ground ripples outward from them in rings of rising stone, pebbles hanging in the air around them. */
    static void bedrock(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 6, 20), r = a.scale();
        Vec3 o = a.origin();
        for (int w = 0; w < 3; w++) {
            float p = ((t + w * 18) % 54) / 54;
            float rr = 1 + p * r, lift = Mth.sin(p * Mth.PI) * 0.35F * f;
            int n = (int) (rr * 4);
            for (int i = 0; i < n && c.solid(); i++) {
                float ang = i * Mth.TWO_PI / n + w;
                c.box(o.add(Mth.cos(ang) * rr, lift - 0.15, Mth.sin(ang) * rr), 0.22F, 0.18F, 0.18F, -ang, 0, 0, STONE, 1);
            }
            if (c.glow()) c.ring(o.add(0, 0.06, 0), rr, 0.12F, rgb(0xFFB45A), 0.45F * f * (1 - p));
        }
        if (c.solid()) for (int i = 0; i < 10; i++) {
            float ang = i * Mth.TWO_PI / 10 + t * 0.02F;
            float h = 0.4F + 1.4F * hash(a.seed(), i) + Mth.sin(t * 0.08F + i) * 0.15F;
            c.box(o.add(Mth.cos(ang) * 1.5, h * smooth(0, 15, t), Mth.sin(ang) * 1.5), 0.09F, 0.08F, 0.1F, ang * 3, i, i, STONE_DARK, 1);
        }
    }

    // ------------------------------------------------------------------
    // Sound
    // ------------------------------------------------------------------

    private static Vec3[] wavyRing(Vec3 at, float radius, float amplitude, int lobes, float phase) {
        Vec3[] p = new Vec3[49];
        for (int i = 0; i <= 48; i++) {
            float ang = i * Mth.TWO_PI / 48, rr = radius + amplitude * Mth.sin(lobes * ang + phase);
            p[i] = at.add(Mth.cos(ang) * rr, 0, Mth.sin(ang) * rr);
        }
        return p;
    }

    /** Standing waves stack up over the point, ring on ring, then fold in on themselves and burst. */
    static void cascade(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float r = a.scale(), build = 16;
        Vec3 o = a.target();
        float squeeze = t < build ? 1 : Mth.clamp(1 - (t - build) / 4, 0, 1);
        int rings = Math.min(8, (int) (t / 2) + 1);
        for (int i = 0; i < rings; i++) {
            float h = 0.3F + i * 0.45F, rr = r * (0.55F + 0.45F * Mth.sin((i + 1) / 9F * Mth.PI)) * squeeze;
            float amp = 0.25F * Mth.sin(t * 0.8F + i);
            if (rr > 0.05F) c.ribbon(wavyRing(o.add(0, h, 0), rr, amp, 6, t * 0.6F + i), 0.07F, i % 2 == 0 ? SOUND : SOUND_GLOW, 0.85F, 0.85F);
        }
        if (t >= build) {
            float s = t - build;
            float p = Mth.clamp(s / 12, 0, 1);
            c.ring(o.add(0, 0.1, 0), r * 1.3F * p, 0.3F, SOUND, 0.9F * (1 - p));
            if (s < 5) c.glow(o.add(0, 1.6, 0), 5, WHITE, 1 - s / 5);
            c.ribbon(wavyRing(o.add(0, 1.2, 0), r * 1.1F * p, 0.3F, 8, s), 0.12F, SOUND_GLOW, 0.7F * (1 - p), 0.7F * (1 - p));
        }
    }

    /** A dome where sound goes dead, its skin rippling with interference fringes. */
    static void silence(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 10, 16), r = a.scale() * smooth(0, 10, t) + 0.01F;
        Vec3 o = a.origin();
        Pattern fringes = (theta, phi) -> 0.25F + 0.75F * Math.abs(Mth.sin(phi * 9 - t * 0.25F) * Mth.cos(theta * 4 + phi * 3));
        if (c.soft()) c.shell(o, r, r * 0.85F, r, Mth.HALF_PI, SOUND, 0.18F * f, 0.25F, PLAIN, 0);
        if (c.glow()) {
            c.shell(o, r, r * 0.85F, r, Mth.HALF_PI, SOUND_GLOW, 0.4F * f, 0.5F, fringes, t * 0.01F);
            c.ring(o.add(0, 0.06, 0), r, 0.12F, SOUND_GLOW, 0.6F * f);
        }
    }

    /** A five-line staff of light circles them, notes rising off it on every beat. */
    static void hymn(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 8, 16);
        Vec3 o = a.at(c);
        for (int line = 0; line < 5; line++) {
            float from = t * 0.06F + line * 0.1F;
            c.ring(o.add(0, 1.05 + line * 0.1, 0), UP, 1.45F, 0.025F, from, from + Mth.PI * 1.5F, HYMN, 0.7F * f);
        }
        for (int i = 0; i < 10; i++) {
            float k = ((t + i * 8) % 80) / 80, ang = i * 2.39996F + k * 1.5F, rr = 1.45F + k * 1.6F;
            Vec3 head = o.add(Mth.cos(ang) * rr, 1.1 + k * 2.4, Mth.sin(ang) * rr);
            float al = f * Mth.sin(k * Mth.PI);
            c.glow(head, 0.16F, HYMN, al);
            c.line(head.add(0.06, 0, 0), head.add(0.06, 0.4, 0), 0.025F, HYMN, al, al);
            if (i % 2 == 0) c.line(head.add(0.06, 0.4, 0), head.add(0.22, 0.28, 0), 0.025F, HYMN, al, al);
        }
        float beat = (t % 10) / 10;
        c.ring(o.add(0, 0.06, 0), 0.8F + beat * 3, 0.08F, HYMN, 0.5F * f * (1 - beat));
    }
}
