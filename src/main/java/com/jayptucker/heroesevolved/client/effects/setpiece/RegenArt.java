package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Claire Bennet's blood and heartbeat as set pieces, in the look her older effects already have
 * ({@code RegenerationEffects}): crimson for blood, warm gold for life. Blood is drawn with normal blending, so it reads
 * as blood rather than light; only its wet highlight glows. Four primitives draw every piece:
 * <ul>
 *   <li>{@link #strand}: a sagging, wavering ribbon of blood between two points;</li>
 *   <li>{@link #drops}: beads of blood travelling along a line;</li>
 *   <li>{@link #beat}: a heartbeat ring that jumps twice (lub-dub);</li>
 *   <li>{@link #ecg}: a heart-monitor trace whose spikes can flatten.</li>
 * </ul>
 * Palette: {@code B0101C} blood, {@code FF4050} bright, {@code 3A060C} clot, {@code FFD9A0} life, {@code FFF4F0} core.
 */
final class RegenArt {
    private static final float[] BLOOD = rgb(0xB0101C), BRIGHT = rgb(0xFF4050), CLOT = rgb(0x3A060C), LIFE = rgb(0xFFD9A0),
            CORE = rgb(0xFFF4F0);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private RegenArt() {}

    static void draw(Canvas c, Active a, float t) {
        Vec3 at = a.origin(), end = a.target();
        Entity bound = a.bound(c);
        float life = a.piece().ticks, size = a.scale();
        float clock = (c.level.getGameTime() % 2400 + c.partial);
        int seed = a.seed();
        switch (a.piece()) {
            case REGEN_STREAM -> {
                Vec3 from = bound == null ? at : bound.getPosition(c.partial).add(0, 1.1, 0)
                        .add(bound.getViewVector(c.partial).scale(0.55));
                float alpha = fade(t, life, 1, 2) * 0.6F;
                strand(c, from, end, clock * 0.4F, 0.09F, alpha, 0.35F);
                drops(c, from, end, clock * 0.09F, minimal() ? 3 : 6, 0.07F, alpha * 1.6F, 0.35F);
                if (c.glow()) c.glow(end, 0.35F, BRIGHT, alpha * 0.5F);
            }
            case REGEN_FAN -> fan(c, at, end.subtract(at), t, seed);
            case REGEN_IMPACT -> {
                Vec3 ground = at.add(0, 0.06, 0);
                float p = clamp(t / 20, 0, 1), open = 1 - (float) Math.pow(1 - clamp(t / 8, 0, 1), 3);
                if (c.soft()) for (int i = 0; i < (minimal() ? 6 : 12); i++) {
                    float ang = i * TWO_PI / 12 + hash(seed, i) * 0.4F, reach = size * (0.4F + 0.5F * hash(seed, i + 30)) * open;
                    Vec3 d = new Vec3(cos(ang), 0, sin(ang));
                    gash(c, ground.add(d.scale(0.3)), ground.add(d.scale(0.3 + reach)), seed + i, 0.07F, (1 - p) * 0.9F);
                }
                beat(c, ground.add(0, 0.02, 0), UP, size, t, 1 - p);
                if (c.glow() && t < 4) c.glow(at.add(0, 0.8, 0), 0.9F, BRIGHT, 0.6F * (1 - t / 4));
            }
            case REGEN_PAYBACK -> {
                Vec3 o = bound == null ? at : bound.getPosition(c.partial);
                float alpha = fade(t, life, 0.5F, 10);
                beat(c, o.add(0, 0.06, 0), UP, size, t, alpha);
                beat(c, o.add(0, 1.1, 0), UP, size * 0.6F, t - 2, alpha * 0.7F);
                if (c.glow() && t < 5) c.glow(o.add(0, 1.1, 0), 0.7F, LIFE, 0.7F * (1 - t / 5));
            }
            case REGEN_FEIGN -> {
                if (bound == null) return;
                Vec3 o = bound.getPosition(c.partial);
                // Above the body lying still: a heart monitor whose beat slows and flattens.
                float alpha = fade(t, life, 2, 2) * 0.8F;
                Vec3 right = Vec3.directionFromRotation(0, bound.getYRot() + 90);
                ecg(c, o.add(0, 1.0, 0), right, 1.4F, clock * 0.05F, alpha, clamp(1 - size, 0, 1) * 0.8F);
            }
            case REGEN_CPR -> {
                Vec3 o = bound == null ? at : bound.getPosition(c.partial);
                float alpha = fade(t, life, 1, 6);
                Vec3 chest = o.add(0, Math.max(0.6, size * 0.6), 0);
                for (int i = 0; i < 3; i++) {
                    float k = clamp((t - i * 5) / 5, 0, 1);
                    if (k > 0 && k < 1) beat(c, chest, UP, 0.4F + 0.5F * k, k * 6, alpha * (1 - k));
                }
                Vec3 right = Vec3.directionFromRotation(0, (bound == null ? 0 : bound.getYRot()) + 90);
                // Flat at first, then the beat comes back.
                ecg(c, o.add(0, Math.max(1.6, size + 0.4), 0), right, 1.2F, t * 0.05F, alpha, clamp(1 - t / 12, 0, 1));
            }
            default -> { }
        }
    }

    /** Bloodletting: a fan of blood flung from the palm, arcing and falling, splashing where it lands. */
    private static void fan(Canvas c, Vec3 from, Vec3 dir, float t, int seed) {
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = d.cross(UP);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        float reach = (float) dir.length();
        int count = minimal() ? 9 : 18;
        float travel = clamp(t / 7, 0, 1);
        for (int i = 0; i < count; i++) {
            float spread = (i / (float) (count - 1) - 0.5F) * 1.2F + (hash(seed, i) - 0.5F) * 0.15F;
            float far = reach * (0.55F + 0.45F * hash(seed, i + 40));
            Vec3 aim = d.add(side.scale(spread)).normalize();
            float k = travel;
            Vec3 p = from.add(aim.scale(far * k)).add(0, 0.6 * k - 1.6 * k * k, 0);
            Vec3 tail = from.add(aim.scale(far * Math.max(0, k - 0.15F))).add(0, 0.6 * (k - 0.15) - 1.6 * (k - 0.15) * (k - 0.15), 0);
            float alpha = fade(t, 16, 0.5F, 6);
            if (c.soft()) c.line(tail, p, 0.06F, BLOOD, alpha * 0.5F, alpha);
            if (c.glow()) c.glow(p, 0.06F, BRIGHT, alpha * 0.5F);
            if (travel >= 1 && c.soft()) {
                // A splash where it lands.
                Vec3 land = from.add(aim.scale(far)).add(0, -1.0, 0);
                c.disc(land, UP, 0.15F + 0.1F * hash(seed, i + 70), CLOT, alpha * 0.7F, alpha * 0.2F);
            }
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    /** A ribbon of blood from one point to another, sagging by {@code sag} and wavering, with a wet glowing line along it. */
    static void strand(Canvas c, Vec3 from, Vec3 to, float phase, float width, float alpha, float sag) {
        if (alpha < 0.01F) return;
        int n = 24;
        Vec3[] pts = new Vec3[n + 1];
        Vec3 dir = to.subtract(from);
        Vec3 side = dir.cross(UP);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, bend = 4 * u * (1 - u);
            pts[i] = from.add(dir.scale(u)).add(0, -sag * bend, 0).add(side.scale(0.05 * bend * sin(u * 12 + phase * 6)));
        }
        // Each point fades near the camera, so a stream passing the viewer doesn't fill the screen.
        float[] w = new float[n + 1], wet = new float[n + 1], body = new float[n + 1], shine = new float[n + 1];
        Vec3 eye = c.camera();
        for (int i = 0; i <= n; i++) {
            float near = (float) clamp((pts[i].distanceTo(eye) - 1.2) / 2.4, 0, 1);
            w[i] = width; wet[i] = width * 0.3F; body[i] = alpha * near; shine[i] = alpha * 0.6F * near;
        }
        if (c.soft()) c.ribbon(pts, w, BLOOD, body);
        if (c.glow()) c.ribbon(pts, wet, BRIGHT, shine);
    }

    /** Beads of blood moving along a sagging line; {@code phase} slides them. */
    static void drops(Canvas c, Vec3 from, Vec3 to, float phase, int count, float size, float alpha, float sag) {
        if (alpha < 0.01F) return;
        Vec3 dir = to.subtract(from);
        for (int i = 0; i < count; i++) {
            float u = frac(phase + i / (float) count), bend = 4 * u * (1 - u);
            Vec3 p = from.add(dir.scale(u)).add(0, -sag * bend, 0);
            if (c.soft()) c.disc(p, c.camera().subtract(p), size, BLOOD, alpha, alpha * 0.5F);
            if (c.glow()) c.glow(p, size * 0.6F, BRIGHT, alpha * 0.4F);
        }
    }

    /** A dark, jagged split in the ground (soft pass, normal blending, so it stays dark). */
    static void gash(Canvas c, Vec3 from, Vec3 to, int seed, float width, float alpha) {
        if (!c.soft() || alpha < 0.01F) return;
        Vec3 d = to.subtract(from), side = d.cross(UP);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 last = from;
        for (int i = 1; i <= 5; i++) {
            float u = i / 5F, kink = i == 5 ? 0 : (hash(seed, i) - 0.5F) * 0.35F * (float) d.length() / 2;
            Vec3 next = from.add(d.scale(u)).add(side.scale(kink));
            c.line(last, next, width * (1.2F - u * 0.7F), CLOT, alpha, alpha);
            last = next;
        }
    }

    /** A heartbeat ring across {@code axis} that jumps twice (lub-dub) as it spreads, crimson going to gold. */
    static void beat(Canvas c, Vec3 at, Vec3 axis, float radius, float t, float alpha) {
        if (alpha < 0.01F || t < 0) return;
        for (int i = 0; i < 2; i++) {
            float k = clamp((t - i * 3) / 15, 0, 1);
            if (k <= 0 || k >= 1) continue;
            float r = radius * (1 - (1 - k) * (1 - k));
            float[] col = mix(BRIGHT, LIFE, k);
            if (c.soft()) c.ring(at, axis, r, 0.16F, 0, TWO_PI, BLOOD, alpha * (1 - k) * 0.35F);
            if (c.glow()) {
                c.ring(at, axis, r, 0.07F, 0, TWO_PI, col, alpha * (1 - k) * 0.9F);
                c.ring(at, axis, r, 0.025F, 0, TWO_PI, CORE, alpha * (1 - k) * 0.6F);
            }
        }
    }

    /**
     * A heart-monitor trace across {@code right}, {@code width} long, scrolling with {@code phase}. {@code flat} from 0 (a
     * healthy beat) to 1 (a flatline) shrinks the spikes.
     */
    static void ecg(Canvas c, Vec3 center, Vec3 right, float width, float phase, float alpha, float flat) {
        if (alpha < 0.01F || !c.glow()) return;
        int n = 48;
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, beat = frac(u * 2 - phase);
            float spike = beat < 0.08F ? sin(beat / 0.08F * PI) * 0.3F : beat < 0.14F ? -sin((beat - 0.08F) / 0.06F * PI) * 0.12F : 0;
            pts[i] = center.add(right.scale((u - 0.5) * width)).add(0, spike * (1 - flat), 0);
        }
        float[] col = mix(BRIGHT, LIFE, 1 - flat);
        c.ribbon(pts, 0.035F, col, alpha, alpha);
        c.ribbon(pts, 0.012F, CORE, alpha * 0.8F, alpha * 0.8F);
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
