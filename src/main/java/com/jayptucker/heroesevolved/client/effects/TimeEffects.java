package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Time reads as clockwork made of light: gold clock faces with twelve hour marks and sweeping hands,
 * in a violet glow. Slowed time is warm amber and drifts; stopped time is cold violet and holds still;
 * rewound time runs backwards (trails draw from now to then, hands spin the wrong way). Glows use the
 * additive ribbons the lightning uses, so they read in daylight.
 */
final class TimeEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final int GOLD = 0xFFD27A, AMBER = 0xE8A43C, VIOLET = 0x9A7CFF, DEEP = 0x5B3FD1, PALE = 0xF4EBFF, ICE = 0xC9C2FF;
    private static final DustParticleOptions SAND = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.38F), 0.7F);
    private static final DustParticleOptions FROZEN = new DustParticleOptions(new Vector3f(0.78F, 0.74F, 1.0F), 0.9F);

    private TimeEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case TEMPORAL_FIELD -> field(level, origin, size, detail);
            case TEMPORAL_BURST -> burst(level, origin, size, detail);
            case TEMPORAL_REWIND -> rewindPath(level, origin, target, size, detail);
            case TIME_FOLD -> fold(level, origin, target, detail);
            case TIME_SLOW_PULSE -> dome(level, origin, size, detail, false);
            case TIME_STOP_PULSE -> dome(level, origin, size, detail, true);
            case TIME_REWIND_ARRIVE -> arrive(level, origin, size, detail);
            case TIME_SNAPSHOT -> snapshot(level, origin, size, detail);
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Clockwork
    // ------------------------------------------------------------------

    /** A clock face lying on the ground: two rings, twelve hour marks (every third longer), and its hands. */
    static void clock(ClientLevel level, Vec3 center, double radius, int life, int delay, float alpha, int color, int glow) {
        loop(center, radius, 0.035F, glow, color, alpha, life, delay);
        loop(center, radius * 0.86, 0.018F, glow, color, alpha * 0.7F, life, delay);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            double inner = radius * (i % 3 == 0 ? 0.66 : 0.76);
            Vec3 a = center.add(Math.cos(angle) * inner, 0, Math.sin(angle) * inner);
            Vec3 b = center.add(Math.cos(angle) * radius * 0.86, 0, Math.sin(angle) * radius * 0.86);
            LightningRenderer.stroke(List.of(a, b), i % 3 == 0 ? 0.05F : 0.03F, i % 3 == 0 ? 0.05F : 0.03F, color, alpha, life, delay);
        }
    }

    /** The clock's two hands at these angles (radians, measured on the ground). */
    static void hands(Vec3 center, double radius, double hour, double minute, int life, int delay, float alpha, int color) {
        Vec3 h = center.add(Math.cos(hour) * radius * 0.42, 0, Math.sin(hour) * radius * 0.42);
        Vec3 m = center.add(Math.cos(minute) * radius * 0.7, 0, Math.sin(minute) * radius * 0.7);
        LightningRenderer.stroke(List.of(center, h), 0.07F, 0.03F, color, alpha, life, delay);
        LightningRenderer.stroke(List.of(center, m), 0.05F, 0.015F, color, alpha, life, delay);
        LightningRenderer.star(center, 0.12F, PALE, alpha, life, delay);
    }

    /** Hands sweeping over {@code ticks}: the minute hand turns {@code turns} times (negative runs backwards). */
    static void sweep(ClientLevel level, Vec3 center, double radius, int ticks, double turns, float alpha, int color) {
        double start = level.random.nextDouble() * Math.PI * 2;
        for (int t = 0; t < ticks; t += 1) {
            double minute = start + turns * Math.PI * 2 * t / ticks;
            final double hour = start / 12 + minute / 12, m = minute;
            final float a = alpha * (1 - 0.6F * t / ticks);
            ElectroEffects.later(t, () -> hands(center, radius, hour, m, 2, 0, a, color));
        }
    }

    // ------------------------------------------------------------------
    // The kit's cues
    // ------------------------------------------------------------------

    /** A time field taking hold: a clock face spreads out at its radius and its hands make one turn. */
    private static void field(ClientLevel level, Vec3 feet, float radius, boolean detail) {
        Vec3 floor = feet.add(0, 0.06, 0);
        // Fields can reach fifty blocks; the clock stays at a readable size around the caster, and a
        // faint ring marks the real edge.
        double r = Math.clamp(radius, 0.8, 5.5);
        clock(level, floor, r, 18, 0, 0.85F, GOLD, VIOLET);
        if (detail) clock(level, floor.add(0, 0.6, 0), r * 0.35, 14, 3, 0.5F, PALE, VIOLET);
        sweep(level, floor.add(0, 0.02, 0), r, 16, 1, 0.9F, GOLD);
        if (radius > r + 1) loop(floor, Math.min(radius, 64), 0.12F, VIOLET, GOLD, 0.55F, 18, 2);
        LightningRenderer.star(feet.add(0, 1, 0), 0.7F, GOLD, 0.7F, 6, 0);
        for (int i = 0; i < (detail ? 14 : 5); i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * r;
            vanilla(level, ParticleTypes.END_ROD, floor.add(Math.cos(angle) * d, 0.1, Math.sin(angle) * d), new Vec3(0, 0.03 + level.random.nextDouble() * 0.03, 0));
        }
    }

    /** A moment snapping: a gold flash and the hour marks thrown outward. */
    private static void burst(ClientLevel level, Vec3 at, float size, boolean detail) {
        float s = Math.clamp(size, 0.3F, 8);
        LightningRenderer.star(at.add(0, 0.9, 0), 0.5F + s * 0.35F, PALE, 0.95F, 5, 0);
        LightningRenderer.star(at.add(0, 0.9, 0), 0.9F + s * 0.5F, VIOLET, 0.45F, 7, 0);
        Vec3 center = at.add(0, 0.9, 0);
        for (int step = 0; step < 5; step++) {
            final double r = 0.4 + s * 0.35 * (step + 1);
            final float a = 0.9F * (1 - step / 5F);
            ElectroEffects.later(step, () -> {
                for (int i = 0; i < 12; i++) {
                    double angle = i * Math.PI / 6;
                    Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    LightningRenderer.stroke(List.of(center.add(dir.scale(r)), center.add(dir.scale(r + 0.25 + s * 0.08))),
                            0.04F, 0.02F, i % 3 == 0 ? GOLD : PALE, a, 2, 0);
                }
            });
        }
        if (detail) for (int i = 0; i < 10; i++) vanilla(level, ParticleTypes.END_ROD, center, jitter(level, 0.18));
    }

    /**
     * One stretch of a Rewind's path. The server sends them newest first, with {@code age} saying how far back
     * (0 to 20); each draws a moment later than the last, so the trail runs backwards from now to then.
     */
    private static void rewindPath(ClientLevel level, Vec3 from, Vec3 to, float age, boolean detail) {
        if (from.distanceToSqr(to) < 1.0E-4) return;
        int delay = (int) Math.clamp(age * 0.6F, 0, 14);
        List<Vec3> line = List.of(from.add(0, 0.9, 0), to.add(0, 0.9, 0));
        LightningRenderer.stroke(line, 0.22F, 0.22F, DEEP, 0.4F, 12, delay);
        LightningRenderer.stroke(line, 0.07F, 0.07F, VIOLET, 0.85F, 10, delay);
        LightningRenderer.stroke(line, 0.025F, 0.025F, PALE, 1F, 8, delay, false);
        if (detail && level.random.nextFloat() < 0.5F) {
            Vec3 mid = from.lerp(to, 0.5).add(0, 0.9, 0);
            ElectroEffects.later(delay, () -> vanilla(level, ParticleTypes.REVERSE_PORTAL, mid, new Vec3(0, 0.02, 0)));
        }
    }

    /** Fold Space: the air where you stood pinches shut, a seam of light runs to where you are, and opens there. */
    private static void fold(ClientLevel level, Vec3 from, Vec3 to, boolean detail) {
        Vec3 axis = to.subtract(from).multiply(1, 0, 1);
        Vec3 side = axis.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(-axis.z, 0, axis.x).normalize();
        // The tear behind you closes: two edges of light pinch together.
        for (int step = 0; step < 5; step++) {
            final double gap = 0.6 * (1 - step / 5.0);
            final float a = 0.9F - step * 0.12F;
            ElectroEffects.later(step, () -> tear(from, side, gap, a, GOLD));
        }
        // The seam.
        LightningRenderer.stroke(List.of(from.add(0, 1, 0), to.add(0, 1, 0)), 0.1F, 0.1F, VIOLET, 0.45F, 5, 0);
        LightningRenderer.stroke(List.of(from.add(0, 1, 0), to.add(0, 1, 0)), 0.025F, 0.025F, PALE, 0.9F, 4, 0, false);
        // The tear ahead opens.
        for (int step = 0; step < 5; step++) {
            final double gap = 0.6 * (step + 1) / 5.0;
            final float a = 0.95F - step * 0.15F;
            ElectroEffects.later(step + 1, () -> tear(to, side, gap, a, PALE));
        }
        burst(level, to, 1.2F, detail);
        clock(level, to.add(0, 0.06, 0), 1.3, 10, 1, 0.7F, GOLD, VIOLET);
    }

    /** Two upright edges of light, {@code gap} apart, a body's height. */
    private static void tear(Vec3 feet, Vec3 side, double gap, float alpha, int color) {
        for (int s : new int[] {-1, 1}) {
            Vec3 base = feet.add(side.scale(gap * s));
            List<Vec3> edge = new ArrayList<>();
            for (int i = 0; i <= 8; i++) edge.add(base.add(side.scale(-s * 0.08 * Math.sin(i * Math.PI / 8))).add(0, 0.05 + i * 0.24, 0));
            LightningRenderer.stroke(edge, 0.06F, 0.06F, color, alpha, 2, 0);
        }
    }

    /**
     * The outline of a time field while it lasts: a dome of rings at its radius. Slowed time is amber,
     * turns slowly and sheds sand that barely falls; stopped time is violet, holds perfectly still, and
     * motes hang in the air where they are.
     */
    private static void dome(ClientLevel level, Vec3 center, float radius, boolean detail, boolean stopped) {
        double r = Math.clamp(radius, 1, 64);
        int color = stopped ? ICE : AMBER, glow = stopped ? DEEP : GOLD;
        Vec3 floor = center.add(0, 0.08, 0);
        // The edge: thick enough to read from across the field.
        float edge = (float) (0.06 + r * 0.004);
        loop(floor, r, edge, glow, color, stopped ? 0.6F : 0.5F, 12, 0);
        loop(floor.add(0, r * 0.18, 0), r * 0.98, edge * 0.7F, glow, color, 0.3F, 12, 0);
        // A clock under the caster: slowed hands crawl round, stopped hands hold at ten past ten.
        double face = Math.min(2.6, r * 0.3);
        clock(level, floor, face, 12, 0, 0.75F, color, glow);
        double hour = stopped ? -Math.PI / 3 : level.getGameTime() * 0.004, minute = stopped ? -Math.PI * 2 / 3 : level.getGameTime() * 0.05;
        hands(floor.add(0, 0.02, 0), face, hour, minute, 12, 0, 0.85F, stopped ? PALE : GOLD);
        // Inside it, the air itself: sand that barely falls, or motes hanging perfectly still, around whoever is looking.
        Vec3 eye = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (eye.distanceToSqr(center) <= r * r) {
            for (int i = 0; i < (detail ? 18 : 6); i++) {
                Vec3 at = eye.add(jitter(level, 6)).add(0, -0.5, 0);
                if (at.distanceToSqr(eye) < 2.5) continue;
                vanilla(level, stopped ? FROZEN : SAND, at, stopped ? Vec3.ZERO : new Vec3(0, -0.003, 0));
            }
        }
    }

    /** Arriving at the end of a Rewind: rings collapse in on you, the hands spin backwards, and the moment closes. */
    private static void arrive(ClientLevel level, Vec3 feet, float size, boolean detail) {
        Vec3 floor = feet.add(0, 0.06, 0);
        double r = 1.4 + size * 0.5;
        for (int step = 0; step < 6; step++) {
            final double ring = r * (1 - step / 6.0) + 0.2;
            final float a = 0.5F + step * 0.08F;
            ElectroEffects.later(step, () -> {
                loop(floor, ring, 0.04F, DEEP, VIOLET, a, 2, 0);
                loop(floor.add(0, 1.0, 0), ring * 0.7, 0.03F, DEEP, GOLD, a * 0.7F, 2, 0);
            });
        }
        clock(level, floor, r, 12, 0, 0.75F, GOLD, VIOLET);
        sweep(level, floor.add(0, 0.02, 0), r, 12, -2, 0.95F, PALE);
        ElectroEffects.later(6, () -> {
            LightningRenderer.star(feet.add(0, 1, 0), 1.0F, PALE, 1F, 5, 0);
            LightningRenderer.star(feet.add(0, 1, 0), 1.6F, VIOLET, 0.5F, 6, 0);
        });
        if (detail) for (int i = 0; i < 16; i++) {
            Vec3 from = feet.add(0, 1, 0).add(jitter(level, 1).normalize().scale(1.8));
            vanilla(level, ParticleTypes.REVERSE_PORTAL, from, feet.add(0, 1, 0).subtract(from).scale(0.08));
        }
    }

    /** Remembering a moment: a golden frame snaps out around you and a flash, like a photograph being taken. */
    private static void snapshot(ClientLevel level, Vec3 feet, float size, boolean detail) {
        Vec3 floor = feet.add(0, 0.06, 0);
        double r = Math.clamp(size, 1.5, 12);
        for (int step = 0; step < 6; step++) {
            final double half = r * (0.3 + 0.7 * step / 5.0);
            final float a = 0.95F - step * 0.1F;
            ElectroEffects.later(step, () -> {
                List<Vec3> frame = List.of(floor.add(-half, 0, -half), floor.add(half, 0, -half), floor.add(half, 0, half),
                        floor.add(-half, 0, half), floor.add(-half, 0, -half));
                LightningRenderer.stroke(frame, 0.07F, 0.07F, GOLD, a, 3, 0);
                LightningRenderer.stroke(frame, 0.2F, 0.2F, VIOLET, a * 0.3F, 3, 0);
            });
        }
        LightningRenderer.star(feet.add(0, 1.2, 0), 1.6F, 0xFFFFFF, 1F, 4, 0);
        clock(level, floor, r * 0.4, 16, 2, 0.6F, GOLD, VIOLET);
        if (detail) for (int i = 0; i < 12; i++) vanilla(level, ParticleTypes.END_ROD, feet.add(0, 1, 0), jitter(level, 0.2));
    }

    // ------------------------------------------------------------------

    /** A ring of glow on the ground plane around {@code center}. */
    private static void loop(Vec3 center, double radius, float width, int glow, int color, float alpha, int life, int delay) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= 32; i++) {
            double angle = i * Math.PI * 2 / 32;
            points.add(center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        LightningRenderer.stroke(points, width * 3, width * 3, glow, alpha * 0.35F, life + 1, delay);
        LightningRenderer.stroke(points, width, width, color, alpha, life, delay);
    }

    private static Vec3 jitter(ClientLevel level, double scale) {
        return new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(scale * 2);
    }

    private static void vanilla(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.addParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }
}
