package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.fade;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static net.minecraft.util.Mth.PI;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.sin;

/**
 * Pieces the story's scripted fights share (the Last Day's sky fight, the Space-Time vision): a forked lightning bolt
 * ({@code STORY_BOLT}: origin the hand, target where it lands, scale its thickness) and a telekinetic grip
 * ({@code STORY_GRIP}: origin the held body's middle, target the hand that holds it, scale how hard it is held, 0 to 1;
 * bound to whoever is held when there is someone).
 *
 * <p>Palette: lightning {@code 2E6BD8} halo, {@code 7FD4FF} body, {@code F2FBFF} core; the grip {@code B3262E} red bands,
 * {@code 7A5A22} deep brass and {@code FFE7A8} gilt.</p>
 */
final class StoryArt {
    private static final float[] HALO = rgb(0x2E6BD8), ARC = rgb(0x7FD4FF), CORE = rgb(0xF2FBFF),
            GRIP = rgb(0xB3262E), DEEP = rgb(0x7A5A22), GILT = rgb(0xFFE7A8), WHITE = rgb(0xF4FAFF);

    private StoryArt() {}

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static float clock(Canvas c, float t) { return c.level != null ? (c.level.getGameTime() % 24000) + c.partial : t; }

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case STORY_BOLT -> bolt(c, a, t);
            case STORY_GRIP -> grip(c, a, t);
            default -> {}
        }
    }

    /** The bolt: thick, forked, white at its heart, with a flash where it lands. */
    private static void bolt(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 0.5F, 7);
        float w = 0.05F * a.scale();
        forked(c, a.origin(), a.target(), a.seed(), w, f, 3);
        if (c.glow()) {
            c.glow(a.target(), 0.6F * a.scale(), CORE, f * 0.8F);
            c.glow(a.origin(), 0.35F * a.scale(), ARC, f * 0.6F);
        }
    }

    /** Held by a mind: red bands closing round the body, a line from the hand, and a gilt arc of how hard it holds. */
    private static void grip(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 2, 2);
        Entity e = a.bound(c);
        Vec3 chest = e != null ? e.getPosition(c.partial).add(0, 1.0, 0) : a.origin().add(0, 1, 0);
        float now = clock(c, t), held = Mth.clamp(a.scale(), 0, 1);
        for (int i = 0; i < 3; i++) {
            float phase = (now * 0.05F + i / 3F) % 1, r = 1.1F - 0.6F * phase;
            c.ring(chest.add(0, (i - 1) * 0.45, 0), UP, r, 0.05F, 0, TWO_PI, GRIP, f * 0.7F * (1 - phase * 0.5F));
        }
        Vec3 hand = a.target().add(0, -0.4, 0);
        c.line(hand, chest, 0.16F, GRIP, f * 0.25F, f * 0.25F);
        c.line(hand, chest, 0.05F, GRIP, f * 0.6F, f * 0.6F);
        c.line(hand, chest, 0.018F, GILT, f * 0.7F, f * 0.7F);
        c.ring(chest, UP, 0.75F, 0.05F, 0, TWO_PI, DEEP, f * 0.5F);
        if (held > 0) {
            c.ring(chest, UP, 0.75F, 0.16F, PI, PI - held * TWO_PI, GILT, f * 0.3F);
            c.ring(chest, UP, 0.75F, 0.05F, PI, PI - held * TWO_PI, WHITE, f);
        }
    }

    /** A jagged channel from {@code a} to {@code b} in three layers, {@code jitter} blocks of wander at its middle. */
    static void filament(Canvas c, Vec3 a, Vec3 b, int seed, float jitter, float width, float alpha) {
        if (!c.glow() || alpha < 0.01F) return;
        double length = a.distanceTo(b);
        if (length < 0.02) return;
        int n = Mth.clamp((int) (length * 2.4) + 3, 4, 40);
        Vec3 d = b.subtract(a), side = d.cross(UP);
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(d).normalize();
        Vec3[] points = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n, taper = sin(k * PI);
            float ox = (hash(seed, i * 2) - 0.5F) * 2 * jitter * taper, oy = (hash(seed, i * 2 + 1) - 0.5F) * 2 * jitter * taper;
            points[i] = a.add(d.scale(k)).add(side.scale(ox)).add(up.scale(oy));
        }
        c.ribbon(points, width * 3.4F, HALO, alpha * 0.32F, alpha * 0.32F);
        c.ribbon(points, width, ARC, alpha * 0.85F, alpha * 0.85F);
        c.ribbon(points, width * 0.35F, CORE, alpha, alpha);
    }

    /** A forked bolt: one channel and a few short branches off it. */
    static void forked(Canvas c, Vec3 a, Vec3 b, int seed, float width, float alpha, int branches) {
        float jitter = (float) Math.min(1.4, a.distanceTo(b) * 0.09);
        filament(c, a, b, seed, jitter, width, alpha);
        if (minimal()) return;
        Vec3 d = b.subtract(a);
        for (int i = 0; i < branches; i++) {
            float k = 0.2F + 0.6F * hash(seed, 300 + i);
            Vec3 from = a.add(d.scale(k));
            Vec3 off = new Vec3(hash(seed, 310 + i) - 0.5, hash(seed, 320 + i) - 0.5, hash(seed, 330 + i) - 0.5).scale(2.2 * jitter + 0.4);
            filament(c, from, from.add(d.normalize().scale(0.6 + jitter)).add(off), seed * 7 + i, jitter * 0.5F, width * 0.45F, alpha * 0.7F);
        }
    }
}
