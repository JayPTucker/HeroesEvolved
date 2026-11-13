package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Empathic Mimicry: a heartbeat made visible. Gold threads draw tight between the empath and someone with a power,
 * a pulse runs down them in a heartbeat's rhythm, and what is taken blooms at the empath's chest in that power's own
 * colour. Palette: {@code E8C27A} thread, {@code FFF6E0} core, {@code 6B4422} the soft halo, plus the borrowed tint.
 */
final class MimicArt {
    private static final float[] THREAD = rgb(0xE8C27A), CORE = rgb(0xFFF6E0), HALO = rgb(0x6B4422);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private MimicArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        Entity mimic = a.bound(c);
        Vec3 chest = mimic == null ? a.target() : mimic.getPosition(c.partial).add(0, mimic.getBbHeight() * .62, 0);
        float clock = c.level.getGameTime() + c.partial;
        switch (a.piece()) {
            case MIMIC_ATTUNE -> attune(c, a, t, chest, clock);
            case MIMIC_IMPRINT -> imprint(c, a, t, chest);
            case MIMIC_SHIFT -> shift(c, a, t, chest);
            default -> { }
        }
    }

    /** Three strands, sagging less as the attunement builds, with a heartbeat running from them to you. */
    private static void attune(Canvas c, Active a, float t, Vec3 chest, float clock) {
        int index = (int) floor(a.scale());
        float progress = a.scale() - index;
        float alpha = fade(t, a.piece().ticks, 2, 2) * (.45F + .55F * progress);
        float[] tint = rgb(MimicColors.color(index));
        Vec3 from = a.origin();
        int strands = minimal() ? 1 : 3;
        for (int s = 0; s < strands; s++) {
            Vec3[] points = strand(from, chest, (1 - progress) * .55F + .06F, s * TWO_PI / 3 + clock * .05F, .07F * (1 - progress));
            thread(c, points, .035F + .03F * progress, alpha * (s == 0 ? 1 : .55F));
        }
        // Lub-dub: two beats a second, each a bead of light travelling from them to you.
        float beat = (clock % 20) / 20F;
        for (float start : new float[]{0, .18F}) {
            float u = (beat - start) / .55F;
            if (u < 0 || u > 1) continue;
            Vec3 bead = from.lerp(chest, u).subtract(0, sin(PI * u) * ((1 - progress) * .55F + .06F), 0);
            if (c.glow()) {
                c.glow(bead, .22F + .1F * progress, tint, alpha * .8F);
                c.glow(bead, .08F, CORE, alpha);
            }
        }
        // A glimpse of their power: a small ring in its colour round their chest.
        ring(c, from, UP, .38F + .08F * sin(clock * .3F), .035F, tint, alpha * .8F);
    }

    /** The thread snaps straight, a pulse rushes to you, and their colour blooms out of your chest. */
    private static void imprint(Canvas c, Active a, float t, Vec3 chest) {
        boolean touched = a.scale() >= 32;
        int index = (int) floor(touched ? a.scale() - 32 : a.scale());
        float[] tint = rgb(MimicColors.color(index));
        float life = a.piece().ticks;
        Vec3 from = a.origin();
        float rush = smooth(0, 4, t);
        float threadAlpha = 1 - smooth(4, 12, t);
        if (threadAlpha > .01F) {
            Vec3[] line = new Vec3[9];
            for (int i = 0; i < line.length; i++) line[i] = from.lerp(chest, i / 8.0);
            thread(c, line, .05F, threadAlpha);
            Vec3 front = from.lerp(chest, rush);
            if (c.glow()) {
                c.glow(front, .45F, tint, threadAlpha);
                c.glow(front, .14F, CORE, threadAlpha);
            }
        }
        if (t >= 3) {
            float bloom = smooth(3, 16, t), alpha = 1 - smooth(10, life, t);
            ring(c, chest, UP, .3F + 1.6F * bloom, .06F, tint, alpha);
            ring(c, chest.subtract(0, .45, 0), UP, .2F + 1.1F * smooth(5, 18, t), .045F, THREAD, alpha * .7F);
            if (!minimal()) {
                Vec3 facing = chest.subtract(from).multiply(1, 0, 1);
                Vec3 axis = facing.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : facing.normalize().cross(UP);
                ring(c, chest, axis, .25F + .9F * bloom, .04F, tint, alpha * .6F);
            }
            if (c.glow()) c.glow(chest, .9F * (1 - bloom) + .25F, tint, alpha * .8F);
        }
        // A touch leaves a handprint of light where the palm met them.
        if (touched) {
            float print = fade(t, 18, 1, 10);
            Vec3 normal = chest.subtract(from);
            normal = normal.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : normal.normalize();
            Vec3 palm = from.add(normal.scale(.32));
            if (c.glow()) {
                c.disc(palm, normal, .13F, tint, .9F * print, .2F * print);
                for (int f = 0; f < 5; f++) {
                    float angle = -.9F + f * .45F + (f == 0 ? -.35F : 0);
                    c.spoke(palm, normal, angle + HALF_PI, .14F, f == 0 ? .25F : .31F, .035F, CORE, print);
                }
                c.glow(palm, .35F, tint, .7F * print);
            }
        }
    }

    /** Switching: the gold of the empath drawn in to the heart, then the chosen power's colour opening out. */
    private static void shift(Canvas c, Active a, float t, Vec3 chest) {
        float[] tint = rgb(MimicColors.color((int) floor(a.scale())));
        float in = smooth(0, 6, t), out = smooth(6, 16, t);
        if (t < 7) ring(c, chest, UP, 1.2F - 1.05F * in, .05F, THREAD, 1 - smooth(5, 7, t));
        if (t >= 5) {
            float alpha = fade(t - 5, a.piece().ticks - 5, 1, 6);
            ring(c, chest, UP, .15F + 1.25F * out, .055F, tint, alpha);
            ring(c, chest.add(0, .35, 0), UP, .1F + .8F * out, .035F, tint, alpha * .6F);
            if (c.glow()) c.glow(chest, .55F * (1 - out) + .15F, tint, alpha);
        }
    }

    private static Vec3[] strand(Vec3 from, Vec3 to, float sag, float phase, float twist) {
        Vec3 axis = to.subtract(from);
        Vec3 side = axis.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : axis.normalize().cross(UP);
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0); else side = side.normalize();
        Vec3[] points = new Vec3[17];
        for (int i = 0; i < points.length; i++) {
            float u = i / 16F, edge = sin(PI * u);
            points[i] = from.lerp(to, u).subtract(0, sag * edge, 0).add(side.scale(sin(phase + u * TWO_PI) * twist * edge));
        }
        return points;
    }

    /**
     * A thread in three layers. It fades out only where it passes close to the camera: a thread running past a
     * front-view camera, or ending at your own chest in first person, would otherwise fill the screen.
     */
    private static void thread(Canvas c, Vec3[] points, float width, float alpha) {
        if (alpha < .01F) return;
        Vec3 eye = c.camera();
        float[] near = new float[points.length];
        for (int i = 0; i < points.length; i++)
            near[i] = alpha * (float) Math.clamp((points[i].distanceTo(eye) - .8) / 1.8, 0, 1);
        if (c.soft()) c.ribbon(points, widths(points.length, width * 3.2F), HALO, scaled(near, .35F));
        if (c.glow()) {
            c.ribbon(points, widths(points.length, width * 1.6F), THREAD, scaled(near, .55F));
            c.ribbon(points, widths(points.length, width * .45F), CORE, scaled(near, .95F));
        }
    }

    private static float[] widths(int n, float width) {
        float[] w = new float[n];
        java.util.Arrays.fill(w, width);
        return w;
    }

    private static float[] scaled(float[] alpha, float k) {
        float[] out = new float[alpha.length];
        for (int i = 0; i < alpha.length; i++) out[i] = alpha[i] * k;
        return out;
    }

    private static void ring(Canvas c, Vec3 at, Vec3 axis, float radius, float width, float[] tint, float alpha) {
        if (alpha < .01F || radius < .02F) return;
        if (c.soft()) c.ring(at, axis, radius, width * 2.4F, 0, TWO_PI, HALO, alpha * .3F);
        if (c.glow()) {
            c.ring(at, axis, radius, width, 0, TWO_PI, tint, alpha * .8F);
            c.ring(at, axis, radius, width * .35F, 0, TWO_PI, CORE, alpha * .9F);
        }
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
