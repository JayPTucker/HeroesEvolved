package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.PowerParticle.Shape.*;

/**
 * Electrokinesis reads as real lightning: a fractal channel (midpoint displacement) with forks that
 * taper away, drawn in three layers (a wide blue glow, a bright body, a white-hot core), and struck
 * two or three times in quick succession the way a real bolt flickers. Everything is cosmetic.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ElectroEffects {
    static final int GLOW = 0x2D6CFF, BLUE = 0x63BAFF, PALE = 0xBFE6FF, WHITE = 0xF6FCFF;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private record Pending(int at, Runnable run) {}
    private static final List<Pending> PENDING = new ArrayList<>();
    private static ClientLevel pendingWorld;
    private static int clock;

    private ElectroEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case ELECTRIC_ARC -> strike(level, origin, target, 0.06F * Math.max(0.6F, size), detail, detail ? 2 : 1, 0.22);
            case ELECTRIC_STREAM -> stream(level, origin, target, size, detail);
            case ELECTRIC_ORB -> orb(level, origin, target, size, detail);
            case ELECTRIC_IMPACT -> impact(level, origin, size, detail);
            case ELECTRIC_NOVA -> nova(level, origin, size, detail);
            case ELECTRIC_SKY_BOLT -> skyBolt(level, origin, target, size, detail);
            case ELECTRIC_TELEGRAPH -> telegraph(level, origin, size, detail);
            case ELECTRIC_DASH -> dash(level, origin, target, detail);
            case ELECTRIC_SCAR -> scar(level, origin, target, detail);
            case ELECTRIC_AURA -> aura(level, origin, size, detail);
            case ELECTRIC_SURGE_CHARGE -> surgeCharge(level, origin, size, detail);
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The bolt itself
    // ------------------------------------------------------------------

    /** A jagged channel from {@code from} to {@code to}: each pass splits every segment and nudges the midpoint sideways. */
    static List<Vec3> channel(Vec3 from, Vec3 to, RandomSource random, double roughness, int passes) {
        List<Vec3> points = new ArrayList<>(List.of(from, to));
        double offset = from.distanceTo(to) * roughness;
        for (int pass = 0; pass < passes; pass++) {
            List<Vec3> next = new ArrayList<>(points.size() * 2);
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = points.get(i), b = points.get(i + 1);
                next.add(a);
                next.add(a.lerp(b, 0.5).add(perpendicular(b.subtract(a), random).scale(offset * (random.nextDouble() * 2 - 1))));
            }
            next.add(points.getLast());
            points = next;
            offset *= 0.5;
        }
        return points;
    }

    private static Vec3 perpendicular(Vec3 direction, RandomSource random) {
        Vec3 axis = direction.lengthSqr() < 1.0E-6 ? UP : direction.normalize();
        Vec3 any = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
        Vec3 side = any.subtract(axis.scale(any.dot(axis)));
        return side.lengthSqr() < 1.0E-6 ? axis.cross(Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP).normalize() : side.normalize();
    }

    /** How many halving passes give segments of roughly half a block. */
    private static int passes(double length, boolean detail) {
        int passes = (int) Math.ceil(Math.log(Math.max(1, length / 0.5)) / Math.log(2));
        return Math.clamp(passes, 2, detail ? 6 : 4);
    }

    /**
     * Draws one stroke of a bolt with its forks. {@code width} is the body's half-width at the source;
     * the channel tapers toward its end. Forks branch off at random points, shorter and thinner.
     */
    static void bolt(ClientLevel level, Vec3 from, Vec3 to, float width, int life, int delay, float alpha,
                     boolean detail, double roughness, int forkDepth) {
        RandomSource random = level.random;
        double length = from.distanceTo(to);
        if (length < 0.05) return;
        List<Vec3> points = channel(from, to, random, roughness, passes(length, detail));
        // Three additive layers: a wide blue glow, the bright body, and a thin white-hot core.
        if (detail) LightningRenderer.stroke(points, width * 4.0F, width * 2.0F, GLOW, alpha * 0.45F, life + 1, delay);
        LightningRenderer.stroke(points, width * 1.3F, width * 0.7F, BLUE, alpha * 0.95F, life, delay);
        LightningRenderer.stroke(points, width * 0.35F, width * 0.2F, WHITE, alpha, life, delay, false);
        if (forkDepth <= 0) return;
        for (int i = 1; i < points.size() - 2; i++) {
            if (random.nextFloat() >= (detail ? 0.14F : 0.07F)) continue;
            float t = i / (float) (points.size() - 1);
            Vec3 a = points.get(i), along = points.get(i + 1).subtract(a).normalize();
            Vec3 direction = along.add(perpendicular(along, random).scale(0.6 + random.nextDouble() * 0.6)).normalize();
            double reach = length * (1 - t) * (0.18 + random.nextDouble() * 0.3);
            if (reach > 0.25) bolt(level, a, a.add(direction.scale(reach)), width * 0.5F * (1 - 0.4F * t), life, delay, alpha * 0.8F,
                    detail && forkDepth > 1, roughness * 1.2, forkDepth - 1);
        }
    }

    private static void layer(ClientLevel level, Vec3 a, Vec3 b, float width, int life, int delay, int color, float alpha) {
        LightningRenderer.stroke(List.of(a, b), width, width, color, alpha, life, delay);
    }

    /** A crackling ring of current around {@code axis}: a jittered circle. */
    static void loop(ClientLevel level, Vec3 center, Vec3 axis, double radius, float width, int color, float alpha, int life) {
        Vec3 normal = axis.normalize();
        Vec3 right = normal.cross(Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : UP).normalize(), up = right.cross(normal);
        List<Vec3> points = new ArrayList<>();
        double phase = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i <= 16; i++) {
            double angle = phase + i * Math.PI * 2 / 16, r = radius * (0.88 + level.random.nextDouble() * 0.24);
            points.add(center.add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r)));
        }
        LightningRenderer.stroke(points, width * 2.5F, width * 2.5F, GLOW, alpha * 0.3F, life, 0);
        LightningRenderer.stroke(points, width, width, color, alpha, life, 0);
    }

    /** A full strike: the main stroke now, then re-strikes along fresh jitter a tick or two later. */
    static void strike(ClientLevel level, Vec3 from, Vec3 to, float width, boolean detail, int restrikes, double roughness) {
        bolt(level, from, to, width, 3, 0, 1F, detail, roughness, 2);
        spark(level, to, detail ? 6 : 2, 0.25);
        flash(level, to, width * 4, 3);
        for (int i = 1; i <= restrikes; i++) {
            float alpha = 0.75F / i;
            later(i * 2, () -> bolt(level, from, to, width * 0.85F, 2, 0, alpha, detail, roughness, 1));
        }
    }

    // ------------------------------------------------------------------
    // The kit's cues
    // ------------------------------------------------------------------

    /** Live Current: two intertwined channels, redrawn every update, white-hot at the hand. */
    private static void stream(ClientLevel level, Vec3 hand, Vec3 end, float intensity, boolean detail) {
        float width = 0.05F + 0.03F * intensity;
        bolt(level, hand, end, width, 3, 0, 1F, detail, 0.12, 1);
        if (detail) bolt(level, hand, end, width * 0.6F, 3, 0, 0.6F, false, 0.2, 0);
        flash(level, hand, 0.22F, 3);
        flash(level, end, 0.32F + 0.08F * intensity, 3);
        spark(level, end, detail ? 4 : 1, 0.3);
        if (detail && level.random.nextFloat() < 0.35F) {
            // A stray lash from the end of the current to something nearby.
            Vec3 lash = end.add(perpendicular(end.subtract(hand), level.random).scale(0.8 + level.random.nextDouble()))
                    .add(0, -0.4 - level.random.nextDouble() * 0.6, 0);
            bolt(level, end, lash, width * 0.5F, 2, 0, 0.7F, false, 0.3, 0);
        }
    }

    /** Ball Lightning: a white core in a blue shell, rings of current, tendrils lashing out. */
    private static void orb(ClientLevel level, Vec3 center, Vec3 ahead, float size, boolean detail) {
        Vec3 velocity = ahead.subtract(center);
        LightningRenderer.star(center, size * 0.9F, WHITE, 1F, 3, 0);
        LightningRenderer.star(center, size * 1.5F, BLUE, 0.55F, 3, 0);
        for (int ring = 0; ring < (detail ? 3 : 1); ring++) {
            Vec3 axis = new Vec3(Math.sin(clock * 0.3 + ring * 2.1), 1, Math.cos(clock * 0.27 + ring * 1.7));
            loop(level, center, axis, size * 1.15, 0.02F, ring == 1 ? PALE : BLUE, 0.85F, 3);
        }
        for (int i = 0; i < (detail ? 3 : 1); i++) {
            Vec3 tip = center.add(new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5)
                    .normalize().scale(size * (2 + level.random.nextDouble() * 1.5)));
            bolt(level, center, tip, 0.025F, 2, 0, 0.8F, false, 0.35, 0);
        }
        if (detail && velocity.lengthSqr() > 1.0E-4) bolt(level, center.subtract(velocity.scale(2.5)), center, 0.035F, 3, 0, 0.5F, false, 0.2, 0);
    }

    /** Where current lands: a white flash, a ring, sparks, and forks crawling over the ground. */
    private static void impact(ClientLevel level, Vec3 at, float radius, boolean detail) {
        flash(level, at, 0.5F + radius * 0.15F, 4);
        ring(level, at.add(0, 0.08, 0), UP, 0.2F, radius, 12, 0, BLUE, 0.7F);
        if (detail) ring(level, at.add(0, 0.12, 0), UP, 0.1F, radius * 0.7F, 9, 1, WHITE, 0.55F);
        spark(level, at, detail ? 18 : 6, 0.45);
        int forks = detail ? 7 : 3;
        for (int i = 0; i < forks; i++) {
            double angle = i * Math.PI * 2 / forks + level.random.nextDouble() * 0.6;
            Vec3 end = at.add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            bolt(level, at.add(0, 0.1, 0), end, 0.035F, 4, level.random.nextInt(2), 0.8F, false, 0.25, 0);
        }
    }

    /** Storm Surge: a ground ring, forks racing outward along the ground, and arcs climbing into the air. */
    private static void nova(ClientLevel level, Vec3 feet, float radius, boolean detail) {
        Vec3 chest = feet.add(0, 1.1, 0);
        flash(level, chest, 1.4F, 5);
        ring(level, feet.add(0, 0.1, 0), UP, 0.4F, radius, 16, 0, WHITE, 0.8F);
        ring(level, feet.add(0, 0.5, 0), UP, 0.3F, radius * 0.85F, 18, 2, BLUE, 0.6F);
        if (detail) ring(level, chest, UP, 0.2F, radius * 0.6F, 20, 4, GLOW, 0.5F);
        int forks = detail ? 12 : 6;
        for (int i = 0; i < forks; i++) {
            double angle = i * Math.PI * 2 / forks + level.random.nextDouble() * 0.3;
            Vec3 ground = feet.add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            bolt(level, chest, ground, 0.06F, 4, i % 3, 1F, detail, 0.25, 1);
        }
        for (int i = 0; i < (detail ? 4 : 2); i++) {
            Vec3 sky = chest.add(perpendicular(UP, level.random).scale(1 + level.random.nextDouble() * 2)).add(0, 2 + level.random.nextDouble() * 2, 0);
            bolt(level, chest, sky, 0.04F, 4, 1, 0.8F, false, 0.3, 0);
        }
        spark(level, chest, detail ? 28 : 8, 0.6);
    }

    /** Thunderstrike: a thick channel from the sky with heavy branching, re-striking twice. */
    private static void skyBolt(ClientLevel level, Vec3 ground, Vec3 sky, float size, boolean detail) {
        float width = 0.22F * size;
        level.setSkyFlashTime(2);
        bolt(level, sky, ground, width, 4, 0, 1F, detail, 0.2, 2);
        flash(level, ground.add(0, 0.5, 0), 1.6F * size, 5);
        ring(level, ground.add(0, 0.1, 0), UP, 0.3F, 3.5F * size, 14, 0, WHITE, 0.85F);
        if (detail) ring(level, ground.add(0, 0.2, 0), UP, 0.2F, 2.6F * size, 18, 2, BLUE, 0.6F);
        spark(level, ground.add(0, 0.2, 0), detail ? 30 : 10, 0.7);
        later(2, () -> bolt(level, sky, ground, width * 0.8F, 3, 0, 0.7F, detail, 0.2, 1));
        later(5, () -> bolt(level, sky, ground, width * 0.6F, 2, 0, 0.45F, false, 0.2, 0));
        impact(level, ground, 3.2F * size, detail);
    }

    /** The warning before Thunderstrike: a ring closing in, sparks climbing off the ground, a leader descending. */
    private static void telegraph(ClientLevel level, Vec3 ground, float radius, boolean detail) {
        int ticks = 16;
        ring(level, ground.add(0, 0.06, 0), UP, radius, 0.3F, ticks, 0, BLUE, 0.65F);
        ring(level, ground.add(0, 0.09, 0), UP, radius * 0.6F, 0.2F, ticks, 4, PALE, 0.5F);
        Vec3 sky = ground.add(0, 26, 0);
        List<Vec3> leader = channel(sky, ground.add(0, 2, 0), level.random, 0.12, 4);
        for (int step = 0; step < ticks; step += 2) {
            final int s = step;
            later(step, () -> {
                // Upward streamers rise from the ground to meet the descending leader.
                for (int i = 0; i < (detail ? 3 : 1); i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2, r = level.random.nextDouble() * radius;
                    Vec3 base = ground.add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r);
                    bolt(level, base, base.add(0, 0.4 + s * 0.08, 0), 0.025F, 2, 0, 0.75F, false, 0.35, 0);
                }
                int reach = Math.min(leader.size() - 1, (int) (leader.size() * (s + 2) / (double) ticks));
                for (int i = Math.max(0, reach - 3); i < reach; i++)
                    layer(level, leader.get(i), leader.get(i + 1), 0.05F, 2, 0, PALE, 0.6F);
            });
        }
    }

    /** Ride the Lightning: the body becomes a bolt, with pillars of light where it leaves and arrives. */
    private static void dash(ClientLevel level, Vec3 start, Vec3 end, boolean detail) {
        Vec3 lift = new Vec3(0, 1, 0);
        bolt(level, start.add(lift), end.add(lift), 0.12F, 4, 0, 1F, detail, 0.12, 1);
        bolt(level, start.add(0, 0.4, 0), end.add(0, 1.6, 0), 0.06F, 4, 1, 0.8F, detail, 0.18, 1);
        for (Vec3 at : new Vec3[] {start, end}) {
            bolt(level, at.add(0, 2.4, 0), at, 0.09F, 3, 0, 1F, false, 0.15, 0);
            flash(level, at.add(0, 1, 0), 0.6F, 4);
            ring(level, at.add(0, 0.08, 0), UP, 0.2F, 1.8F, 10, 0, BLUE, 0.6F);
            spark(level, at.add(0, 1, 0), detail ? 14 : 4, 0.4);
        }
    }

    /** The scar a lightning ride leaves: small arcs hopping along the ground line. */
    private static void scar(ClientLevel level, Vec3 start, Vec3 end, boolean detail) {
        for (int i = 0; i < (detail ? 3 : 1); i++) {
            double t = level.random.nextDouble();
            Vec3 a = start.lerp(end, t).add(0, 0.08, 0);
            Vec3 b = start.lerp(end, Math.clamp(t + (level.random.nextDouble() - 0.5) * 0.25, 0, 1)).add(0, 0.08, 0);
            bolt(level, a, b.add(0, 0.25, 0), 0.03F, 3, 0, 0.85F, false, 0.35, 0);
            spark(level, a, 2, 0.15);
        }
    }

    /** Charged: arcs crawl over the body. Overcharged (size above 1) adds more, and some reach the ground. */
    private static void aura(ClientLevel level, Vec3 feet, float size, boolean detail) {
        boolean over = size > 1;
        int arcs = (detail ? 3 : 1) + (over ? 3 : 0);
        for (int i = 0; i < arcs; i++) {
            bolt(level, onBody(level, feet), onBody(level, feet), 0.022F, 3, level.random.nextInt(3), 0.85F, false, 0.4, 0);
        }
        if (over && level.random.nextFloat() < 0.5F) {
            Vec3 from = onBody(level, feet);
            bolt(level, from, feet.add(perpendicular(UP, level.random).scale(0.9 + level.random.nextDouble())), 0.03F, 3, 0, 0.9F, false, 0.3, 0);
        }
        if (detail) spark(level, onBody(level, feet), over ? 2 : 1, 0.1);
    }

    private static Vec3 onBody(ClientLevel level, Vec3 feet) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        return feet.add(Math.cos(angle) * 0.42, 0.15 + level.random.nextDouble() * 1.65, Math.sin(angle) * 0.42);
    }

    /** Storm Surge's wind-up: current drawn in from a shrinking sphere to the chest, faster as it fills. */
    private static void surgeCharge(ClientLevel level, Vec3 feet, float progress, boolean detail) {
        Vec3 chest = feet.add(0, 1.1, 0);
        double radius = 4.5 - 3 * progress;
        int arcs = (detail ? 3 : 1) + (int) (progress * 3);
        for (int i = 0; i < arcs; i++) {
            Vec3 from = chest.add(perpendicular(UP, level.random).add(0, level.random.nextDouble() * 1.6 - 0.6, 0).normalize().scale(radius));
            bolt(level, from, chest, 0.03F + 0.03F * progress, 2, 0, 0.9F, false, 0.3, 0);
        }
        loop(level, feet.add(0, 0.1, 0), UP, radius, 0.02F + 0.02F * progress, BLUE, 0.5F + 0.4F * progress, 3);
        flash(level, chest, 0.3F + progress * 0.6F, 2);
    }

    // ------------------------------------------------------------------
    // Small pieces
    // ------------------------------------------------------------------

    /** A brief glint where current lands or leaves. */
    static void flash(ClientLevel level, Vec3 at, float size, int life) {
        LightningRenderer.star(at, size, WHITE, 0.9F, life, 0);
        LightningRenderer.star(at, size * 1.5F, GLOW, 0.35F, life + 1, 0);
    }

    /** Vanilla electric sparks: small and fast, they scatter and fall. */
    static void spark(ClientLevel level, Vec3 at, int count, double speed) {
        for (int i = 0; i < count; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                    (level.random.nextDouble() - 0.5) * speed, level.random.nextDouble() * speed * 0.7, (level.random.nextDouble() - 0.5) * speed);
        }
    }

    /** Runs a piece of an effect a few client ticks from now (re-strikes, a descending leader). */
    static void later(int ticks, Runnable run) {
        if (PENDING.size() < 256) PENDING.add(new Pending(clock + ticks, run));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != pendingWorld) { PENDING.clear(); pendingWorld = level; }
        clock++;
        if (level == null || PENDING.isEmpty() || Minecraft.getInstance().isPaused()) return;
        for (Pending pending : List.copyOf(PENDING)) {
            if (pending.at() <= clock) { PENDING.remove(pending); pending.run().run(); }
        }
    }
}
