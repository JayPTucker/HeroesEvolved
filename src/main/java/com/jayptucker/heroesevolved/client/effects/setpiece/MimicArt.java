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
        float clock = c.time();
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

    /** Where the {@code i}th of {@code n} voices of a Chorus circles, {@code clock} ticks in. */
    private static Vec3 voiceAt(Vec3 chest, int i, int n, float clock) {
        float ang = clock * .06F + i * TWO_PI / Math.max(1, n);
        return chest.add(cos(ang) * .95, .25 + sin(clock * .11F + i * 1.7F) * .12, sin(ang) * .95);
    }

    /**
     * Chorus ({@code mimicry/Chorus}): a ring of voices circling the empath, one per remembered power in its own colour,
     * each a turning ring round a bright bead on a gold thread back to his chest, with a slow halo binding them. The
     * voices are packed in the seed (five bits each, the count from bit 27). Bound to him; re-sent each second.
     */
    static void chorus(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, java.util.Set.of(com.jayptucker.heroesevolved.styles.Piece.MIMIC_CHORUS), c.level.getGameTime())) return;
        Entity mimic = a.bound(c);
        if (mimic == null) return;
        int packed = a.seed(), n = Math.min(5, packed >>> 27 & 7);
        float life = a.piece().ticks, f = Math.clamp(t / 4 + (a.scale() > 0 && t > 0 ? .5F : 0), 0, 1) * Math.clamp((life - t) / 2 + .3F, 0, 1);
        float clock = c.time();
        Vec3 chest = mimic.getPosition(c.partial).add(0, mimic.getBbHeight() * .62, 0);
        ring(c, chest.add(0, .25, 0), UP, .95F, .02F, THREAD, f * .45F);
        for (int i = 0; i < n; i++) {
            int index = packed >>> (i * 5) & 31;
            float[] tint = rgb(MimicColors.color(index));
            Vec3 at = voiceAt(chest, i, n, clock);
            Vec3 axis = at.subtract(chest).normalize();
            ring(c, at, axis, .16F + .03F * sin(clock * .3F + i), .025F, tint, f);
            if (c.glow()) {
                c.glow(at, .28F, tint, f * .55F);
                c.glow(at, .09F, CORE, f);
            }
            thread(c, new Vec3[] {chest, chest.lerp(at, .5).add(0, -.12, 0), at}, .012F, f * .5F);
        }
    }

    /** One voice of a Chorus speaking: a bolt in its power's colour from the chest to the foe, a bloom where it lands. */
    static void voice(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target();
        float[] tint = rgb(MimicColors.color((int) floor(a.scale())));
        float life = a.piece().ticks, f = fade(t, life, .5F, 6), k = smooth(0, 3, t);
        Vec3 head = from.lerp(to, k);
        if (c.glow()) {
            c.bolt(from.lerp(to, Math.max(0, k - .6F)), head, a.seed() * 13 + 1, .22F, .07F, tint, f * .8F);
            c.bolt(from.lerp(to, Math.max(0, k - .6F)), head, a.seed() * 13 + 1, .22F, .025F, CORE, f);
            c.glow(head, .3F, tint, f * .7F);
            if (t >= 3) {
                float b = smooth(3, 10, t);
                c.glow(to, .4F + .6F * b, tint, f * (1 - b));
            }
        }
        if (t >= 3) ring(c, to, to.subtract(from).normalize(), .2F + .8F * smooth(3, 10, t), .04F, tint, f * (1 - smooth(3, 10, t)));
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

    /**
     * The alternate finishers ({@code mimicry/MimicFinishers}). Echo: gold threads from the empath's chest to the gold
     * outline of the foe (drawn by BodyCopies), a halo round the outline as it rises, and a flash where its blow lands.
     * Borrowed Strength: a gold shimmer of rings rising round the empath as a kept power's finisher begins. Echo is bound
     * to the foe, the empath's entity id in the seed; Borrowed Strength to the empath.
     */
    static void alternates(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float life = a.piece().ticks, f = fade(t, life, 2, 6);
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_BORROW) {
            net.minecraft.world.entity.Entity me = a.bound(c);
            if (me == null) return;
            Vec3 feet = me.getPosition(c.partial);
            for (int k = 0; k < 3; k++) {
                float q = (t * .07F + k / 3F) % 1;
                ring(c, feet.add(0, .1 + 2 * q, 0), UP, .55F - .2F * q, .03F, THREAD, f * sin(PI * q));
            }
            if (c.glow()) c.glow(feet.add(0, 1.1, 0), .8F, THREAD, f * .3F * (1 - smooth(0, life, t)));
            return;
        }
        float h = Math.max(.6F, a.scale());
        net.minecraft.world.entity.Entity foe = a.bound(c), owner = c.level.getEntity(a.seed());
        Vec3 feet = foe != null && foe.isAlive() ? foe.getPosition(c.partial) : a.origin();
        Vec3 echo = feet.add(a.facing().scale(1.25)).add(0, h * .6, 0);
        int rise = com.jayptucker.heroesevolved.combat.FinisherBeats.ECHO_RISE, land = com.jayptucker.heroesevolved.combat.FinisherBeats.ECHO_LAND;
        float up = smooth(rise, rise + 5, t), gone = 1 - smooth(land + 1, land + 5, t);
        if (owner != null && t < land + 5) {
            Vec3 heart = owner.getPosition(c.partial).add(0, 1.15, 0);
            for (int k = 0; k < 2; k++) thread(c, strand(heart, echo, .35F * (1 - up), t * .2F + k * 2, .12F), .02F, f * up * gone * .8F);
        }
        if (t >= rise && t < land + 4) {
            ring(c, echo.subtract(0, h * .6 - .05, 0), UP, .55F + .1F * sin(t * .5F), .03F, THREAD, f * up * gone);
            if (c.glow()) c.glow(echo, .7F + h * .2F, THREAD, f * .25F * up * gone);
        }
        if (t >= land - 1 && c.glow()) {
            float flash = 1 - smooth(land, land + 4, t);
            Vec3 hit = feet.add(0, h * .6, 0).add(a.facing().scale(.3));
            c.glow(hit, .3F + .9F * flash, CORE, f * flash);
            ring(c, hit, a.facing(), .2F + 1.1F * smooth(land, land + 6, t), .04F, THREAD, f * (1 - smooth(land, land + 6, t)));
        }
    }

    /**
     * Take It In (the finisher, {@code mimicry/TakeItIn}): three gold threads sag between the foe and the empath's chest,
     * draw tight while they feel what it felt, and snap straight; a pulse runs along them into the chest and blooms there
     * as the foe goes still. A foe with a power to take burns the threads brighter. Bound to the foe, the empath's entity id
     * in the seed; origin its feet; scale its height (+ POWERED).
     */
    static void finisher(Canvas c, Active a, float t) {
        boolean powered = a.scale() >= com.jayptucker.heroesevolved.mimicry.TakeItIn.POWERED;
        float h = Math.max(.6F, a.scale() - (powered ? com.jayptucker.heroesevolved.mimicry.TakeItIn.POWERED : 0));
        float life = a.piece().ticks, f = fade(t, life, 2, 6) * (powered ? 1 : .85F);
        net.minecraft.world.entity.Entity foe = a.bound(c), owner = c.level.getEntity(a.seed());
        if (owner == null) return;
        Vec3 from = (foe != null ? foe.getPosition(c.partial) : a.origin()).add(0, h * .6, 0);
        Vec3 heart = owner.getPosition(c.partial).add(0, 1.15, 0);
        int tight = com.jayptucker.heroesevolved.mimicry.TakeItIn.TIGHT, snap = com.jayptucker.heroesevolved.mimicry.TakeItIn.SNAP;
        int land = com.jayptucker.heroesevolved.mimicry.TakeItIn.LAND;
        float sag = .7F * (1 - smooth(tight, snap, t)), reach = smooth(0, 5, t), gone = 1 - smooth(land, land + 5, t);
        for (int k = 0; k < 3; k++) {
            float spread = (k - 1) * .18F;
            int n = 14;
            Vec3[] pts = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                float u = i / (float) n * reach;
                pts[i] = from.lerp(heart, u).add(0, -sag * sin(PI * u) + spread * sin(PI * u), spread * .5 * sin(PI * u));
            }
            thread(c, pts, .025F, f * gone);
        }
        // The pulse running into the chest, and the bloom there.
        if (t >= snap && t < land + 6 && c.glow()) {
            float k = smooth(snap, land, t);
            Vec3 bead = from.lerp(heart, k);
            if (k < 1) { c.glow(bead, .18F, CORE, f); c.glow(bead, .35F, THREAD, f * .5F); }
            float bloom = smooth(land - 1, land + 1, t) * (1 - smooth(land + 1, land + 6, t));
            if (bloom > 0) ring(c, heart, heart.subtract(from).normalize(), .2F + .6F * smooth(land, land + 6, t), .05F, CORE, bloom * f);
        }
    }
}
