package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.ElectroEffects.*;

/**
 * Electricity meeting machinery. A machine caught in a pulse shorts out (arcs crawl over its casing, it
 * spits sparks and smoke); one taking a charge glows as current climbs it; and during Blackout's siphon
 * every powered thing nearby throws an arc into Elle with motes of light running along it. The pulse
 * itself is a dome of lightning racing outward with a dark wave close behind: the lights going out.
 */
final class GridEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** Drawn normally, not added: the dark that follows the pulse. */
    private static final int SHADOW = 0x04060E;

    private GridEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case GRID_SHORT -> shortOut(level, origin, size, detail);
            case GRID_CHARGE -> charge(level, origin, size, detail);
            case GRID_SIPHON -> siphon(level, origin, target, size, detail);
            case ELECTRIC_BLACKOUT -> blackout(level, origin, size, detail);
            default -> { return false; }
        }
        return true;
    }

    /** A point on the surface of the block around {@code center}. */
    private static Vec3 onCasing(RandomSource random, Vec3 center) {
        int face = random.nextInt(6);
        double a = random.nextDouble() - 0.5, b = random.nextDouble() - 0.5, out = 0.52;
        return center.add(switch (face) {
            case 0 -> new Vec3(out, a, b);
            case 1 -> new Vec3(-out, a, b);
            case 2 -> new Vec3(a, out, b);
            case 3 -> new Vec3(a, -out, b);
            case 4 -> new Vec3(a, b, out);
            default -> new Vec3(a, b, -out);
        });
    }

    /** A machine dying: arcs skitter over the casing, sparks spray from the top, a puff of smoke. */
    private static void shortOut(ClientLevel level, Vec3 center, float size, boolean detail) {
        RandomSource random = level.random;
        int arcs = (detail ? 4 : 2) + (size >= 1 ? 1 : 0);
        for (int i = 0; i < arcs; i++)
            bolt(level, onCasing(random, center), onCasing(random, center), 0.05F, 4, random.nextInt(3), 1.0F, false, 0.35, 0);
        Vec3 top = center.add(0, 0.55, 0);
        if (size >= 1) {
            // The pop: a flash on the casing and a couple of arcs leaping off it into the air and the ground.
            flash(level, onCasing(random, center), 0.85F, 5);
            for (int i = 0; i < (detail ? 2 : 1); i++) {
                Vec3 from = onCasing(random, center);
                Vec3 out = from.subtract(center).normalize().scale(0.8 + random.nextDouble() * 0.8).add(0, random.nextBoolean() ? 0.6 : -0.6, 0);
                bolt(level, from, from.add(out), 0.03F, 3, 0, 0.9F, false, 0.3, 0);
            }
        }
        spark(level, top, detail ? 12 : 4, 0.45);
        for (int i = 0; i < (detail ? 3 : 1); i++)
            level.addParticle(i == 0 && size >= 1 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                    top.x + (random.nextDouble() - 0.5) * 0.6, top.y, top.z + (random.nextDouble() - 0.5) * 0.6,
                    0, 0.03 + random.nextDouble() * 0.03, 0);
    }

    /** Current climbing into a machine: a crackling ring rising up the casing, arcs drawn to its heart. */
    private static void charge(ClientLevel level, Vec3 center, float amount, boolean detail) {
        int steps = detail ? 4 : 2;
        for (int step = 0; step < steps; step++) {
            final double height = -0.5 + step / (double) (steps - 1);
            later(step, () -> loop(level, center.add(0, height, 0), UP, 0.72, 0.018F + 0.012F * amount, PALE, 0.85F, 3));
        }
        for (int i = 0; i < (detail ? 3 : 1); i++) {
            Vec3 corner = center.add((level.random.nextBoolean() ? 0.5 : -0.5), (level.random.nextBoolean() ? 0.5 : -0.5), (level.random.nextBoolean() ? 0.5 : -0.5));
            bolt(level, corner, center, 0.02F, 3, i, 0.8F, false, 0.3, 0);
        }
        flash(level, center, 0.3F + 0.3F * amount, 4);
        spark(level, center.add(0, 0.55, 0), detail ? 4 : 1, 0.2);
    }

    /** Blackout's pull: an arc from a powered block to Elle's chest, with light running along it into her. */
    private static void siphon(ClientLevel level, Vec3 from, Vec3 to, float progress, boolean detail) {
        float width = 0.04F + 0.04F * Math.clamp(progress, 0, 1);
        bolt(level, from, to, width, 3, 0, 0.95F, detail, 0.18, detail ? 1 : 0);
        flash(level, from, 0.25F + 0.2F * progress, 3);
        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 0.1) return;
        Vec3 direction = along.scale(1 / length);
        for (int i = 0; i < (detail ? 3 : 1); i++) {
            double t = level.random.nextDouble() * 0.6;
            // Motes run the line in about six ticks, gathering speed: the grid emptying into her.
            mote(level, from.add(along.scale(t)), direction.scale(length * (1 - t) / 6.0), 0.07F, 6, i % 2 == 0 ? WHITE : BLUE);
        }
        if (detail && level.random.nextFloat() < 0.4F) spark(level, from, 3, 0.25);
    }

    /**
     * The pulse: a white flash at her chest, a dome of jittered lightning loops and meridians racing out
     * to {@code radius}, ground forks chasing it, a dark wave close behind, and sparks raining down.
     */
    private static void blackout(ClientLevel level, Vec3 feet, float radius, boolean detail) {
        Vec3 chest = feet.add(0, 1.2, 0);
        flash(level, chest, 2.2F, 6);
        level.setSkyFlashTime(2);
        int frames = detail ? 10 : 5;
        for (int frame = 0; frame < frames; frame++) {
            final float t = (frame + 1) / (float) frames;
            later(frame * (detail ? 1 : 2), () -> domeShell(level, feet, radius * t, 1 - t * 0.7F, detail));
            // The dark wave: a step behind the light, drawn as shadow on the ground.
            later(frame * (detail ? 1 : 2) + 3, () -> darkRing(level, feet, radius * t, 0.55F * (1 - t * 0.4F)));
        }
        int forks = detail ? 14 : 6;
        for (int i = 0; i < forks; i++) {
            double angle = i * Math.PI * 2 / forks + level.random.nextDouble() * 0.3;
            Vec3 ground = feet.add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            Vec3 middle = feet.add(Math.cos(angle) * radius * 0.5, 0.15, Math.sin(angle) * radius * 0.5);
            bolt(level, chest, middle, 0.07F, 4, 0, 1F, detail, 0.22, 1);
            later(3, () -> bolt(level, middle, ground, 0.05F, 4, 0, 0.85F, false, 0.25, 0));
        }
        for (int i = 0; i < (detail ? 5 : 2); i++) {
            Vec3 sky = chest.add((level.random.nextDouble() - 0.5) * 4, 4 + level.random.nextDouble() * 4, (level.random.nextDouble() - 0.5) * 4);
            bolt(level, chest, sky, 0.05F, 4, 1, 0.9F, false, 0.3, 1);
        }
        spark(level, chest, detail ? 30 : 10, 0.7);
        if (detail) for (int i = 0; i < 24; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * radius;
            Vec3 at = feet.add(Math.cos(angle) * r, 3 + level.random.nextDouble() * 3, Math.sin(angle) * r);
            final Vec3 drop = at;
            later(4 + level.random.nextInt(10), () -> level.addParticle(ParticleTypes.ELECTRIC_SPARK, drop.x, drop.y, drop.z, 0, -0.15, 0));
        }
    }

    /** One frame of the dome: latitude loops and a few meridians, jittered like current. */
    private static void domeShell(ClientLevel level, Vec3 feet, double radius, float alpha, boolean detail) {
        float width = (float) Math.clamp(0.04 + radius * 0.004, 0.04, 0.12);
        jaggedLoop(level, feet.add(0, 0.12, 0), radius, width * 1.4F, WHITE, alpha);
        for (double latitude : detail ? new double[] {0.15, 0.5, 0.85} : new double[] {0.3, 0.75}) {
            double ringRadius = radius * Math.cos(latitude * Math.PI / 2), height = radius * Math.sin(latitude * Math.PI / 2);
            jaggedLoop(level, feet.add(0, height, 0), ringRadius, width, latitude < 0.5 ? WHITE : PALE, alpha);
        }
        int meridians = detail ? 6 : 3;
        double phase = level.random.nextDouble() * Math.PI;
        for (int m = 0; m < meridians; m++) {
            double heading = phase + m * Math.PI / meridians;
            List<Vec3> arc = new ArrayList<>();
            for (int i = 0; i <= 12; i++) {
                double theta = Math.PI * i / 12, jitter = 1 + (level.random.nextDouble() - 0.5) * 0.06;
                arc.add(feet.add(Math.cos(heading) * Math.cos(theta) * radius * jitter, Math.sin(theta) * radius * jitter,
                        Math.sin(heading) * Math.cos(theta) * radius * jitter));
            }
            LightningRenderer.stroke(arc, width * 2.4F, width * 2.4F, GLOW, alpha * 0.35F, 3, 0);
            LightningRenderer.stroke(arc, width, width, BLUE, alpha * 0.85F, 2, 0);
        }
    }

    private static void jaggedLoop(ClientLevel level, Vec3 center, double radius, float width, int color, float alpha) {
        if (radius < 0.2) return;
        List<Vec3> points = new ArrayList<>();
        int segments = Math.clamp((int) (radius * 2.5), 16, 40);
        double phase = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i <= segments; i++) {
            double angle = phase + i * Math.PI * 2 / segments, r = radius * (1 + (level.random.nextDouble() - 0.5) * 0.05);
            points.add(center.add(Math.cos(angle) * r, (level.random.nextDouble() - 0.5) * 0.25, Math.sin(angle) * r));
        }
        LightningRenderer.stroke(points, width * 3F, width * 3F, GLOW, alpha * 0.3F, 3, 0);
        LightningRenderer.stroke(points, width, width, color, alpha, 2, 0);
    }

    /** A band of shadow on the ground: drawn with normal blending, since black adds nothing. */
    private static void darkRing(ClientLevel level, Vec3 feet, double radius, float alpha) {
        if (radius < 0.5) return;
        List<Vec3> points = new ArrayList<>();
        int segments = Math.clamp((int) (radius * 2.5), 16, 48);
        for (int i = 0; i <= segments; i++) {
            double angle = i * Math.PI * 2 / segments;
            points.add(feet.add(Math.cos(angle) * radius, 0.45, Math.sin(angle) * radius));
        }
        LightningRenderer.stroke(points, 0.5F, 0.5F, SHADOW, alpha * 0.8F, 6, 0, false);
    }
}
