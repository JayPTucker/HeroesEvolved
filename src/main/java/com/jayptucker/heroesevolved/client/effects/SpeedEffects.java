package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Superspeed drawn as the Flash's lightning and Daphne's blur: running leaves a golden ribbon with a
 * jagged white-gold bolt through it, fast running makes the body crackle, a burst leaves afterimages
 * strung along its path, and breaking the sound barrier throws a cone of white rings behind you.
 */
final class SpeedEffects {
    static final int GOLD = 0xFFC23A, AMBER = 0xFF8A1E, HOT = 0xFFF6D8, EMBER = 0xFF5A1A;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private SpeedEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        RandomSource random = level.random;
        switch (kind) {
            case SPEED_TRAIL -> trail(origin, target, size, random, detail);
            case SPEED_BURST -> burst(origin, size, random);
            case SPEED_IMPACT -> impact(origin, target, size, random);
            case SPEED_AURA -> aura(origin, target, size, random);
            case SPEED_BOOM -> boom(origin, target, size);
            case SPEED_ZIP -> zip(origin, target, size, random, detail);
            case SPEED_CYCLONE -> cyclone(level, origin, target, size, random, detail);
            case SPEED_STREAK -> streak(origin, target, size, random, detail);
            default -> { return false; }
        }
        return true;
    }

    /** A bolt from {@code a} to {@code b}: a jagged channel and a softer glow around it. */
    static void bolt(Vec3 a, Vec3 b, float width, RandomSource random, int life, int delay) {
        List<Vec3> path = ElectroEffects.channel(a, b, random, 0.16, 3);
        LightningRenderer.stroke(path, width * 3, width * 2, AMBER, 0.35F, life, delay);
        LightningRenderer.stroke(path, width, width * 0.6F, HOT, 0.9F, life, delay);
    }

    /** The running trail: a golden ribbon at waist height with lightning through it. */
    private static void trail(Vec3 from, Vec3 to, float size, RandomSource random, boolean detail) {
        Vec3 a = from.add(0, 0.9, 0), b = to.add(0, 0.9, 0);
        LightningRenderer.stroke(List.of(a, b), 0.22F * size, 0.12F * size, GOLD, 0.35F, 9, 0);
        LightningRenderer.stroke(List.of(a.add(0, 0.5, 0), b.add(0, 0.5, 0)), 0.08F * size, 0.05F * size, AMBER, 0.3F, 7, 0);
        bolt(a, b, 0.03F * size, random, 6, 0);
        if (detail && random.nextFloat() < 0.5F) {
            Vec3 off = a.lerp(b, random.nextDouble());
            bolt(off, off.add((random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.3) * 0.8, (random.nextDouble() - 0.5) * 1.2), 0.02F, random, 4, 0);
        }
    }

    private static void burst(Vec3 at, float size, RandomSource random) {
        Vec3 c = at.add(0, 0.9, 0);
        LightningRenderer.stroke(PrecogEffects.circle(at.add(0, 0.1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.6 * size, 28), 0.06F, 0.06F, GOLD, 0.6F, 10, 0);
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            bolt(c, c.add(Math.cos(angle) * size * 0.8, (random.nextDouble() - 0.3) * size * 0.6, Math.sin(angle) * size * 0.8), 0.025F, random, 6, 0);
        }
    }

    /** A blow landing: a white-gold flash at the point of contact, and sparks thrown off it. */
    private static void impact(Vec3 from, Vec3 at, float size, RandomSource random) {
        LightningRenderer.star(at, 0.35F * size, HOT, 0.9F, 5, 0);
        Vec3 away = at.subtract(from).normalize();
        for (int i = 0; i < 3; i++) {
            Vec3 spray = away.add((random.nextDouble() - 0.5) * 1.4, (random.nextDouble() - 0.5) * 1.4, (random.nextDouble() - 0.5) * 1.4).normalize();
            bolt(at, at.add(spray.scale(0.5 + random.nextDouble() * 0.5 * size)), 0.02F, random, 4, 0);
        }
    }

    /** Lightning crawling over a fast runner, more of it the more Momentum: little arcs, and streaks behind. */
    private static void aura(Vec3 feet, Vec3 ahead, float momentum, RandomSource random) {
        int arcs = momentum >= 0.99F ? 5 : momentum >= 0.75F ? 4 : 3;
        for (int i = 0; i < arcs; i++) {
            double angle = random.nextDouble() * Math.PI * 2, y = 0.1 + random.nextDouble() * 1.7;
            Vec3 a = feet.add(Math.cos(angle) * 0.38, y, Math.sin(angle) * 0.38);
            Vec3 b = a.add(Math.cos(angle) * 0.4 + (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 1.1,
                    Math.sin(angle) * 0.4 + (random.nextDouble() - 0.5) * 0.6);
            bolt(a, b, 0.035F, random, 4, 0);
        }
        Vec3 behind = feet.subtract(ahead).multiply(1, 0, 1);
        if (momentum >= 0.75F && behind.lengthSqr() > 1.0E-4) {
            behind = behind.normalize();
            for (int i = 0; i < 2; i++) {
                Vec3 a = feet.add(0, 0.5 + random.nextDouble(), 0);
                LightningRenderer.stroke(List.of(a, a.add(behind.scale(1.5 + random.nextDouble()))), 0.04F, 0.005F, HOT, 0.5F, 4, 0);
            }
        }
    }

    /** Breaking the sound barrier: a cone of white rings opening behind you. */
    private static void boom(Vec3 at, Vec3 toward, float size) {
        Vec3 dir = toward.subtract(at);
        dir = dir.lengthSqr() < 1.0E-4 ? UP : dir.normalize();
        Vec3 a = dir.cross(UP).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.cross(UP).normalize(), b = a.cross(dir).normalize();
        for (int i = 0; i < 5; i++) {
            double back = i * 0.7 * size, radius = (0.6 + i * 0.45) * size;
            LightningRenderer.stroke(PrecogEffects.circle(at.subtract(dir.scale(back)), a, b, radius, 36), 0.08F, 0.08F, i < 2 ? HOT : 0xD8E8FF,
                    0.75F - i * 0.1F, 12, i);
        }
        LightningRenderer.star(at, 0.8F * size, HOT, 0.9F, 6, 0);
    }

    /** A burst of movement: a bright streak, a bolt, and afterimages hanging in the air along it. */
    private static void zip(Vec3 from, Vec3 to, float size, RandomSource random, boolean detail) {
        if (from.distanceToSqr(to) < 1.0E-4) {
            SpeedAfterimages.along(from, from, 1);
            burst(from, size * 0.6F, random);
            return;
        }
        Vec3 a = from.add(0, 0.9, 0), b = to.add(0, 0.9, 0);
        LightningRenderer.stroke(List.of(a, b), 0.12F * size, 0.05F * size, GOLD, 0.6F, 8, 0);
        bolt(a, b, 0.03F * size, random, 6, 0);
        if (detail) SpeedAfterimages.along(from, to, (int) Math.clamp(from.distanceTo(to) / 1.4, 1, 8));
    }

    /** Cyclone: you running the ring as afterimages, wind spiralling up inside it, lightning around the rim. */
    private static void cyclone(ClientLevel level, Vec3 center, Vec3 progress, float radius, RandomSource random, boolean detail) {
        double built = progress.y - center.y;
        double phase = (level.getGameTime() % 40) / 40.0 * Math.PI * 2;
        if (detail && level.getGameTime() % 6 == 0) SpeedAfterimages.ring(center, radius * 0.75, 4, phase);
        for (int strand = 0; strand < 3; strand++) {
            List<Vec3> spiral = new ArrayList<>();
            double start = phase * 2 + strand * Math.PI * 2 / 3;
            for (int s = 0; s <= 24; s++) {
                double t = s / 24.0, angle = start + t * Math.PI * 3, r = radius * (0.35 + 0.65 * t);
                spiral.add(center.add(Math.cos(angle) * r, t * (2.5 + built * 2.5), Math.sin(angle) * r));
            }
            LightningRenderer.stroke(spiral, 0.12F, 0.28F, strand == 0 ? HOT : GOLD, 0.6F, 4, 0);
        }
        // Wind streaks standing around the rim, leaning the way the ring turns.
        for (int i = 0; i < 10; i++) {
            double a = phase * 2 + i * Math.PI / 5;
            Vec3 foot = center.add(Math.cos(a) * radius * 0.9, 0.2, Math.sin(a) * radius * 0.9);
            Vec3 top = center.add(Math.cos(a + 0.5) * radius, 2.8 + built * 1.5, Math.sin(a + 0.5) * radius);
            LightningRenderer.stroke(List.of(foot, foot.lerp(top, 0.5).add(0, 0.2, 0), top), 0.06F, 0.02F, i % 3 == 0 ? HOT : AMBER, 0.4F, 4, 0);
        }
        LightningRenderer.stroke(PrecogEffects.circle(center.add(0, 0.1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, 48), 0.07F, 0.07F, AMBER, 0.55F, 4, 0);
        double angle = phase * 3 + random.nextDouble();
        Vec3 rim = center.add(Math.cos(angle) * radius, 0.3 + random.nextDouble() * 2, Math.sin(angle) * radius);
        bolt(rim, rim.add(Math.cos(angle + 0.6) * 1.2, (random.nextDouble() - 0.5), Math.sin(angle + 0.6) * 1.2), 0.025F, random, 4, 0);
    }

    /** Lightspeed: three ribbons of light down the whole line, lightning along it, and a scorched scar left on the ground. */
    private static void streak(Vec3 from, Vec3 to, float size, RandomSource random, boolean detail) {
        Vec3 dir = to.subtract(from).normalize(), side = new Vec3(-dir.z, 0, dir.x);
        for (int i = -1; i <= 1; i++) {
            Vec3 off = side.scale(i * 0.35).add(0, 0.9 + i * 0.25, 0);
            LightningRenderer.stroke(List.of(from.add(off), to.add(off)), 0.3F * size, 0.12F * size, i == 0 ? HOT : GOLD, 0.7F, 16, 0);
        }
        double length = from.distanceTo(to);
        for (double d = 0; d < length; d += 3) {
            Vec3 a = from.add(dir.scale(d)).add(0, 0.9, 0);
            bolt(a, a.add(dir.scale(3)).add(side.scale((random.nextDouble() - 0.5) * 1.5)).add(0, (random.nextDouble() - 0.5), 0), 0.03F, random, 10, (int) (d / 8));
        }
        // The scar fades slowly: friction burned into the ground.
        LightningRenderer.stroke(List.of(from, to), 0.25F, 0.25F, EMBER, 0.45F, 60, 0);
        if (detail) SpeedAfterimages.along(from, to, 8);
    }
}
