package com.jayptucker.heroesevolved.client.effects.setpiece;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The vision's explosion, anywhere and at any size: the flash, the fireball rising and rolling over into a
 * mushroom cap on a stem of dust, the dust thrown up at its foot, the pale condensation dome racing out
 * with the shock, and a ring of dust rolling outward. Built the way the city's blast is (lumpy billows lit by
 * the sun and their own heat), scaled by {@code s} (1 is the size of the blast over Odessa) and played a
 * little faster the smaller it is. Ted Sprague's detonation and Ground Zero both use it.
 */
public final class MiniBlast {
    private static final float FIREBALL = 42;
    private static final float[] DUST = {0.64F, 0.58F, 0.53F}, PALE = {0.97F, 0.96F, 0.93F}, FLASH = {1, 0.97F, 0.9F}, SURGE = {0.8F, 0.74F, 0.66F},
            HALO = {1, 0.72F, 0.38F};

    private MiniBlast() {}

    /**
     * Draws this pass of a blast at {@code center} (on the ground), {@code t} ticks after the flash, which lasts
     * {@code life} ticks. {@code ring} is how far its ring of dust rolls out, in blocks.
     */
    public static void draw(Canvas c, Vec3 center, float s, float ring, float t, float life) {
        if (t < 0 || t > life) return;
        float time = t / Mth.sqrt(Mth.clamp(s, 0.05F, 1));
        // Near the end the cloud thins away instead of vanishing: drawn see-through in the soft pass.
        float thin = Mth.clamp((life - t) / Math.min(100, life * 0.4F), 0, 1);
        boolean cloudPass = thin >= 1 ? c.solid() : c.soft();
        float alpha = thin >= 1 ? 1 : thin;
        if (cloudPass) cloud(c, center, s, time, alpha);
        if (c.soft()) dust(c, center, s, ring, t, thin);
        if (c.soft()) condensation(c, center, ring, t);
        if (c.glow()) light(c, center, s, time, t);
    }

    private static float fireball(float time, float s) { return FIREBALL * s * (1 - (float) Math.exp(-time / 6)); }

    private static void billow(Canvas c, Vec3 at, float r, float ry, int variant, float spin, float heat, float dust, float alpha) {
        float tint = 0.84F + 0.3F * Canvas.hash(variant, 77);
        float[] base = {Mth.lerp(dust, DUST[0], 0.44F) * tint, Mth.lerp(dust, DUST[1], 0.38F) * tint, Mth.lerp(dust, DUST[2], 0.33F) * tint};
        c.cloud(at, r, ry, r, variant, spin, alpha, Shades.smoke(c, base, heat));
    }

    /** The fireball, rising and rolling over into a cap on a stem, over a skirt of dust: the vision's own shapes. */
    private static void cloud(Canvas c, Vec3 o, float s, float t, float alpha) {
        float ball = fireball(t, s);
        float sm = Canvas.smooth(16, 110, t);
        float lift = Math.max(0, t - 8) * s;
        float ballY = (float) o.y + ball * 0.72F + lift * 0.55F;
        float capY = Math.min((float) o.y + 215 * s, (float) o.y + ball * 0.72F + lift * 0.86F);
        float major = Math.min(78, 20 + t * 0.32F) * s * sm, minor = Math.min(34, 14 + t * 0.12F) * s;
        int n = 30;
        for (int i = 0; i < n; i++) {
            float k = (i + 0.5F) / n, phi = (float) Math.acos(1 - 2 * k), theta = i * 2.39996F;
            float bx = Mth.sin(phi) * Mth.cos(theta), by = Mth.cos(phi), bz = Mth.sin(phi) * Mth.sin(theta);
            float ringAngle = i * Mth.TWO_PI / n, roll = Canvas.hash(i, 3) * Mth.TWO_PI - t * 0.03F;
            float rr = major + minor * Mth.cos(roll);
            float px = Mth.lerp(sm, bx * ball * 0.45F, Mth.cos(ringAngle) * rr);
            float py = Mth.lerp(sm, ballY + by * ball * 0.45F, capY + minor * 0.72F * Mth.sin(roll));
            float pz = Mth.lerp(sm, bz * ball * 0.45F, Mth.sin(ringAngle) * rr);
            float size = Mth.lerp(sm, ball * 0.58F, minor * 0.98F) * (0.85F + 0.3F * Canvas.hash(i, 5));
            float inner = 0.5F - 0.5F * Mth.cos(roll);
            float heat = (float) Math.exp(-t / 50) * (1 - sm * 0.55F) + sm * (float) Math.exp(-t / 85) * 0.55F * inner;
            billow(c, new Vec3(o.x + px, py, o.z + pz), size, size * 0.9F, i, t * 0.008F + i, heat, 0, alpha);
        }
        for (int i = 0; i < 13 && sm > 0.05F; i++) {
            float a = i * Mth.TWO_PI / 12 + 0.3F, r = i == 12 ? 0 : major * 0.52F, size = minor * (i == 12 ? 1.2F : 0.98F) * sm;
            billow(c, new Vec3(o.x + Mth.cos(a) * r, capY + minor * 0.95F, o.z + Mth.sin(a) * r), size, size * 0.82F, 40 + i,
                    -t * 0.006F, (float) Math.exp(-t / 55) * 0.25F, 0.08F, alpha);
        }
        if (t > 10) {
            float bottom = capY - minor * 0.7F, grow = Canvas.smooth(10, 60, t);
            for (int i = 0; i < 12; i++) {
                float f = (i + 0.5F) / 12;
                float y = (float) o.y + 3 * s + (bottom - (float) o.y - 3 * s) * f * grow;
                float r = (7 + Math.min(t, 140) * 0.075F) * s * (1.25F - 0.5F * Mth.sin(f * Mth.PI)) * (0.6F + 0.4F * grow);
                float sway = Mth.sin(i * 1.7F + t * 0.02F) * 2.2F * s;
                billow(c, new Vec3(o.x + sway, y, o.z - sway * 0.6F), r, r * 1.3F, 60 + i, t * 0.012F * (i % 2 == 0 ? 1 : -1),
                        0.4F * (float) Math.exp(-t / 34) * f, 0.55F, alpha);
            }
        }
        if (t > 3) {
            float reach = Math.min(110 * s, 64 * s + t * 0.5F * s);
            for (int i = 0; i < 20; i++) {
                float a = i * Mth.TWO_PI / 20 + Canvas.hash(i, 9), r = reach * (0.35F + 0.4F * Canvas.hash(i, 11));
                float size = (7 + Math.min(t, 90) * 0.11F + Canvas.hash(i, 13) * 4) * s;
                billow(c, new Vec3(o.x + Mth.cos(a) * r, o.y + size * 0.5F, o.z + Mth.sin(a) * r), size, size * 0.7F, 80 + i,
                        t * 0.02F, 0.14F * (float) Math.exp(-t / 28), 1, alpha);
            }
        }
    }

    /** The ring of dust the shock throws up: a pale surge rolling outward to {@code ring}, rising as it thins away. */
    private static void dust(Canvas c, Vec3 o, float s, float ring, float t, float alpha) {
        if (t < 1 || t > 110) return;
        float radius = ring * (1 - (float) Math.exp(-t / 3.5F)) + t * 0.025F * ring;
        float fade = 0.42F * Canvas.smooth(1, 4, t) * (1 - Canvas.smooth(18, 110, t)) * alpha;
        float h = Math.max(1F, ring * 0.12F) * (0.6F + 0.6F * Mth.clamp(t / 30, 0, 1));
        int around = Mth.clamp((int) (radius * 2.2F), 20, 96);
        for (int i = 0; i < around; i++) {
            float a = (i + Canvas.hash(i, 21) * 0.6F) * Mth.TWO_PI / around;
            float size = h * (0.7F + 0.6F * Canvas.hash(i, 23)), r = radius * (0.92F + 0.12F * Canvas.hash(i, 25));
            c.cloud(new Vec3(o.x + Mth.cos(a) * r, o.y + size * 0.3F + t * 0.01F * h, o.z + Mth.sin(a) * r), size * 1.3F, size * 0.55F, size * 1.3F,
                    100 + i, t * 0.03F + i, fade * (0.7F + 0.3F * Canvas.hash(i, 27)), Shades.smoke(c, SURGE, 0));
        }
    }

    /** For a breath after the flash, a pale dome of condensed air racing out with the shock. */
    private static void condensation(Canvas c, Vec3 o, float ring, float t) {
        float alpha = 0.3F * Canvas.smooth(0.5F, 2.5F, t) * (1 - Canvas.smooth(6, 16, t));
        if (alpha <= 0.01F) return;
        float radius = ring * 0.97F * (1 - (float) Math.exp(-t / 3.5F));
        c.shell(o, radius, radius * 0.7F, radius, Mth.HALF_PI, PALE, alpha, 0.15F, Canvas.PLAIN, 0);
    }

    /** The flash, the fireball's halo burning through the dust, and the bright line of the shock on the ground. */
    private static void light(Canvas c, Vec3 o, float s, float time, float t) {
        float ball = fireball(time, s);
        if (t < 6) c.glow(o.add(0, ball + 3 * s, 0), (40 + t * 60) * s + 6, FLASH, 1 - t / 6);
        float halo = 0.65F * (float) Math.exp(-time / 35);
        float lift = Math.max(0, time - 8) * s;
        if (halo > 0.02F) c.glow(o.add(0, ball * 0.72F + lift * 0.55F, 0), Math.max(ball, 1) * 2.4F, HALO, halo);
        if (t < 3) c.glow(o.add(0, 1, 0), (16 + 80 * t) * s + 3, FLASH, 0.9F);
    }

    /**
     * Sprague's light before he goes: a white-gold glow at {@code at} that swells and pulses, with a pillar of
     * light rising from it. {@code k} runs 0 to 1 as the moment comes.
     */
    public static void charge(Canvas c, Vec3 at, float k, float age, float size) {
        if (!c.glow()) return;
        float pulse = 1 + 0.18F * Mth.sin(age * 0.9F);
        c.glow(at, (1.5F + 7 * k * k) * size * pulse, new float[] {1, 0.9F, 0.7F}, 0.55F * k + 0.15F);
        c.glow(at, (0.6F + 2.4F * k) * size * pulse, new float[] {1, 0.98F, 0.9F}, 0.9F);
        c.line(at.add(0, -1, 0), at.add(0, 6 + 30 * k * size, 0), (0.2F + 1.2F * k) * size, new float[] {1, 0.92F, 0.75F}, 0.5F * k * k, 0);
    }
}
