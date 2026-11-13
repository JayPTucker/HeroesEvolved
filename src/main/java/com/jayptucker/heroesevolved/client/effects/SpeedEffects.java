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
            case SPEED_BOLT -> thrown(origin, target, size, random);
            case SPEED_PERCEPTION -> perception(origin, size, random, detail);
            case SPEED_VIBRATE -> vibrate(origin, target, size, random, detail);
            case SPEED_FOCUS -> focus(origin, target, size, random);
            case SPEED_DODGE -> dodge(origin, target, random, detail);
            case SPEED_MIRAGE -> mirage(level, origin, size, random, detail);
            case SPEED_STRIKE -> strike(origin, target, size, random, detail);
            case SPEED_SLIPSTREAM -> slipstream(origin, target, size, random, detail);
            case SPEED_THROW -> javelin(origin, target, size, random);
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
        LightningRenderer.stroke(LightningRenderer.circle(at.add(0, 0.1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.6 * size, 28), 0.06F, 0.06F, GOLD, 0.6F, 10, 0);
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
            LightningRenderer.stroke(LightningRenderer.circle(at.subtract(dir.scale(back)), a, b, radius, 36), 0.08F, 0.08F, i < 2 ? HOT : 0xD8E8FF,
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
        LightningRenderer.stroke(LightningRenderer.circle(center.add(0, 0.1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, 48), 0.07F, 0.07F, AMBER, 0.55F, 4, 0);
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

    /** Lightning Throw: the runner's own gold lightning, hurled. A fat bolt, a forked twin, and a flash where it lands. */
    private static void thrown(Vec3 from, Vec3 to, float size, RandomSource random) {
        bolt(from, to, 0.06F * size, random, 10, 0);
        bolt(from, to, 0.03F * size, random, 7, 1);
        LightningRenderer.star(to, 0.5F * size, HOT, 1F, 6, 0);
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            bolt(to, to.add(Math.cos(angle) * size * 0.7, (random.nextDouble() - 0.3) * size * 0.6, Math.sin(angle) * size * 0.7), 0.02F, random, 5, 1);
        }
    }

    /**
     * Perception: the world slows around the runner. Gold clock rings ripple out to the edge of the
     * slowed zone, and motes hang in the air, barely drifting, with thin threads of lightning between them.
     */
    private static void perception(Vec3 feet, float radius, RandomSource random, boolean detail) {
        Vec3 center = feet.add(0, 0.1, 0);
        for (int i = 0; i < 3; i++)
            LightningRenderer.stroke(LightningRenderer.circle(center.add(0, i * 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                    radius * (0.35 + i * 0.32), 48), 0.05F, 0.05F, i == 2 ? AMBER : GOLD, 0.5F - i * 0.1F, 14, i * 3);
        int motes = detail ? 14 : 6;
        Vec3 last = null;
        for (int i = 0; i < motes; i++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = 1.5 + random.nextDouble() * (radius - 1.5);
            Vec3 mote = center.add(Math.cos(angle) * distance, 0.4 + random.nextDouble() * 2.4, Math.sin(angle) * distance);
            LightningRenderer.star(mote, 0.08F, i % 3 == 0 ? HOT : GOLD, 0.8F, 20, random.nextInt(6));
            if (last != null && i % 4 == 0 && last.distanceToSqr(mote) < 25) bolt(last, mote, 0.012F, random, 8, 2);
            last = mote;
        }
    }

    /**
     * Reflex Focus: the world narrows. Three gold rings close in at eye height one after another, and a still
     * afterimage is left standing in the runner's place: the stance they will dodge out of.
     */
    private static void focus(Vec3 feet, Vec3 eye, float size, RandomSource random) {
        Vec3 a = new Vec3(1, 0, 0), b = new Vec3(0, 0, 1);
        for (int i = 0; i < 3; i++) {
            double radius = (1.7 - i * 0.5) * size;
            LightningRenderer.stroke(LightningRenderer.circle(eye.subtract(0, 0.15 + i * 0.05, 0), a, b, radius, 40),
                    0.04F, 0.04F, i == 2 ? HOT : GOLD, 0.7F - i * 0.1F, 8, i * 2);
        }
        LightningRenderer.star(eye.subtract(0, 0.2, 0), 0.25F, HOT, 0.8F, 5, 5);
        for (int i = 0; i < 2; i++) {
            Vec3 hand = feet.add((i == 0 ? 0.35 : -0.35), 1.45, 0);
            bolt(hand, hand.add((random.nextDouble() - 0.5) * 0.5, 0.35, (random.nextDouble() - 0.5) * 0.5), 0.02F, random, 5, 1);
        }
        SpeedAfterimages.along(feet, feet, 1);
    }

    /**
     * A dodge: the runner is simply somewhere else. An afterimage stays where they stood (the shot passes through
     * it), a low gold streak runs to where they went, and a glint flashes where the shot would have hit.
     */
    private static void dodge(Vec3 from, Vec3 to, RandomSource random, boolean detail) {
        SpeedAfterimages.along(from, from, 1);
        Vec3 a = from.add(0, 0.55, 0), b = to.add(0, 0.55, 0);
        LightningRenderer.stroke(List.of(a, b), 0.16F, 0.05F, GOLD, 0.55F, 7, 0);
        LightningRenderer.stroke(List.of(a, b), 0.05F, 0.02F, HOT, 0.9F, 5, 0);
        bolt(a, b, 0.025F, random, 5, 0);
        LightningRenderer.star(from.add(0, 1.2, 0), 0.45F, HOT, 0.95F, 4, 0);
        if (detail) {
            // Two skid lines where the feet pushed off.
            Vec3 side = b.subtract(a).normalize().cross(UP).scale(0.15);
            LightningRenderer.stroke(List.of(from.add(side).add(0, 0.03, 0), to.add(side).add(0, 0.03, 0)), 0.05F, 0.05F, EMBER, 0.4F, 18, 0);
            LightningRenderer.stroke(List.of(from.subtract(side).add(0, 0.03, 0), to.subtract(side).add(0, 0.03, 0)), 0.05F, 0.05F, EMBER, 0.4F, 18, 0);
        }
    }

    /**
     * Speed Mirage: the runner is everywhere at once. Each pulse leaves afterimages of them standing round where they
     * really are, facing every way, with thin gold threads of lightning back to the real body. A remnant that
     * strikes (Time Remnant, scale 2+) also throws a bolt.
     */
    private static void mirage(ClientLevel level, Vec3 feet, float size, RandomSource random, boolean detail) {
        long now = level.getGameTime();
        int count = detail ? 3 : 1;
        for (int i = 0; i < count; i++) {
            // Placed by the clock, not the random source: every viewer sees the same crowd.
            double angle = (now * 0.77 + i * 2.1) % (Math.PI * 2), distance = 1.2 + ((now * 31 + i * 17) % 13) / 10.0;
            Vec3 spot = feet.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            float yaw = (float) ((now * 47 + i * 120) % 360);
            SpeedAfterimages.at(feet, spot, yaw);
            if (detail) bolt(feet.add(0, 1.1, 0), spot.add(0, 1.1, 0), 0.012F, random, 5, 0);
        }
        LightningRenderer.stroke(LightningRenderer.circle(feet.add(0, 0.06, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 2.4, 40),
                0.03F, 0.03F, GOLD, 0.35F, 6, 0);
    }

    /**
     * Momentum Strike: the rush strung out as afterimages, a gold lane to the target, and the blow itself, a cone of
     * shock rings driven out through the body that was hit.
     */
    private static void strike(Vec3 from, Vec3 at, float size, RandomSource random, boolean detail) {
        Vec3 dir = at.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 a = from.add(0, 0.9, 0);
        LightningRenderer.stroke(List.of(a, at), 0.2F * size, 0.06F * size, GOLD, 0.55F, 8, 0);
        bolt(a, at, 0.035F * size, random, 6, 0);
        if (detail) SpeedAfterimages.along(from, at.subtract(dir.scale(1.1)).subtract(0, 1.3, 0), (int) Math.clamp(from.distanceTo(at) / 1.6, 2, 8));
        Vec3 side = dir.cross(UP).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.cross(UP).normalize(), up = side.cross(dir).normalize();
        for (int i = 0; i < 4; i++) {
            Vec3 center = at.add(dir.scale(0.3 + i * 0.55));
            LightningRenderer.stroke(LightningRenderer.circle(center, side, up, (0.35 + i * 0.3) * size, 30), 0.06F, 0.06F,
                    i == 0 ? HOT : i < 3 ? GOLD : AMBER, 0.85F - i * 0.15F, 7, i);
        }
        LightningRenderer.star(at, 0.7F * size, HOT, 1F, 5, 0);
        for (int i = 0; i < 4; i++) {
            Vec3 spray = dir.add((random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2).normalize();
            bolt(at, at.add(spray.scale(0.8 + random.nextDouble())), 0.02F, random, 5, 1);
        }
    }

    /**
     * Slipstream Rescue: two bodies carried in one wake. The runner's path is a gold ribbon with afterimages; the
     * passenger's (scale 10+) is a pale ribbon of pressed wind, and rings of wind wrap the pair along the way.
     */
    private static void slipstream(Vec3 from, Vec3 to, float size, RandomSource random, boolean detail) {
        boolean passenger = size >= 10;
        Vec3 a = from.add(0, 0.9, 0), b = to.add(0, 0.9, 0);
        if (a.distanceToSqr(b) < 1.0E-4) return;
        int color = passenger ? 0xDBE6F5 : GOLD;
        LightningRenderer.stroke(List.of(a, b), 0.2F, 0.08F, color, passenger ? 0.4F : 0.55F, 10, 0);
        LightningRenderer.stroke(List.of(a, b), 0.05F, 0.02F, HOT, 0.8F, 7, 0);
        if (passenger) {
            Vec3 dir = b.subtract(a).normalize();
            Vec3 side = dir.cross(UP).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.cross(UP).normalize(), up = side.cross(dir).normalize();
            double length = a.distanceTo(b);
            for (double d = 1; d < length; d += 2) {
                LightningRenderer.stroke(LightningRenderer.circle(a.add(dir.scale(d)), side, up, 0.85, 24), 0.035F, 0.035F,
                        0xDBE6F5, 0.45F, 8, (int) (d / 3));
            }
        } else {
            bolt(a, b, 0.03F, random, 6, 0);
            if (detail) SpeedAfterimages.along(from, to, (int) Math.clamp(from.distanceTo(to) / 1.5, 2, 7));
        }
    }

    /**
     * Lightning Throw: lightning gathers in the cocked hand, and as the arm whips through, the bolt leaves it (a fat
     * bolt and a forked twin) and bursts where it lands. A hop to the next target (scale 10+) has no wind-up.
     */
    private static void javelin(Vec3 hand, Vec3 to, float size, RandomSource random) {
        boolean hop = size >= 10;
        float s = hop ? size - 10 : size;
        int delay = hop ? 4 : 3;
        if (!hop) {
            LightningRenderer.star(hand, 0.3F * s, HOT, 0.9F, 4, 0);
            for (int i = 0; i < 3; i++)
                bolt(hand, hand.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.2) * 0.6, (random.nextDouble() - 0.5) * 0.6),
                        0.018F, random, 3, 0);
        }
        bolt(hand, to, 0.06F * s, random, 10, delay);
        bolt(hand, to, 0.03F * s, random, 7, delay + 1);
        LightningRenderer.stroke(List.of(hand, to), 0.14F * s, 0.05F * s, GOLD, 0.35F, 6, delay);
        LightningRenderer.star(to, 0.5F * s, HOT, 1F, 6, delay);
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            bolt(to, to.add(Math.cos(angle) * s * 0.7, (random.nextDouble() - 0.3) * s * 0.6, Math.sin(angle) * s * 0.7), 0.02F, random, 5, delay + 1);
        }
    }

    /**
     * Vibrate: the wall shivers like water where you go in and where you come out, gold rings on both
     * faces, a blur of you strung through the stone, and a buzzing jitter of lightning at the exit.
     */
    private static void vibrate(Vec3 from, Vec3 to, float size, RandomSource random, boolean detail) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 in = from.add(dir.scale(0.6)).add(0, 1, 0), out = to.subtract(dir.scale(0.6)).add(0, 1, 0);
        DensityEffects.ripple(in, dir, 1.1F * size, GOLD, 16, 0);
        DensityEffects.ripple(out, dir, 1.1F * size, HOT, 16, 2);
        LightningRenderer.stroke(List.of(from.add(0, 0.9, 0), to.add(0, 0.9, 0)), 0.16F * size, 0.06F * size, GOLD, 0.45F, 8, 0);
        // The jitter: a body shaking so fast it smears sideways, at the far face.
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int i = 0; i < 6; i++) {
            Vec3 a = out.add(side.scale((random.nextDouble() - 0.5) * 0.7)).add(0, (random.nextDouble() - 0.5) * 1.4, 0);
            bolt(a, a.add(side.scale((random.nextDouble() - 0.5) * 0.6)).add(0, (random.nextDouble() - 0.5) * 0.5, 0), 0.02F, random, 5, 2);
        }
        if (detail) SpeedAfterimages.along(from, to, (int) Math.clamp(from.distanceTo(to) / 1.2, 2, 6));
    }
}
