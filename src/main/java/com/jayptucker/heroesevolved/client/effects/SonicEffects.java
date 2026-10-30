package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Sound as Emma Coolidge sees it: rings of colour that spread out from where a sound is made, waves
 * that roll along a scream in every hue, and a hovering note that pulses as it plays. Hue follows the
 * sound (see {@link SoundSight}); every stroke is a soft additive ribbon, so overlapping colours glow.
 */
public final class SonicEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private SonicEffects() {}

    static int hue(float hue, float saturation) { return Mth.hsvToRgb(((hue % 1F) + 1F) % 1F, saturation, 1F); }

    private static float clock() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : (level.getGameTime() % 400) / 400F;
    }

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        RandomSource random = level.random;
        switch (kind) {
            case SONIC_WAVE -> wave(origin, target, size, detail, false, random);
            case SONIC_CRESCENDO -> wave(origin, target, size, detail, true, random);
            case SONIC_BURST, SONIC_SENSE -> burst(origin, size, kind == Kind.SONIC_SENSE, random);
            case SONIC_NOTE -> note(origin, size, random);
            case SONIC_NOTE_BURST -> noteBurst(origin, size, random, detail);
            case SONIC_WALL -> wall(origin, target, size, random);
            case SONIC_CRESCENDO_CHARGE -> charge(origin, target, size, random);
            case SONIC_LANDING -> ground(origin, size, 0.55F, 4, random);
            default -> { return false; }
        }
        return true;
    }

    /**
     * A sound seen: rings spreading from where it was made. {@code loudness} sets how big and how many,
     * {@code hue} its colour. A footstep is one faint ring; an explosion is a bloom.
     */
    public static void ripple(Vec3 at, float loudness, float hue, boolean ground, RandomSource random) {
        int rings = loudness >= 2 ? 4 : loudness >= 0.8F ? 3 : 1;
        float alpha = Math.min(0.7F, 0.3F + loudness * 0.2F);
        for (int i = 0; i < rings; i++) {
            double radius = (0.25 + i * 0.35) * Math.max(0.6, loudness);
            int color = hue(hue + i * 0.04F, 0.85F);
            if (ground) {
                LightningRenderer.stroke(PrecogEffects.circle(at.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, 24),
                        0.025F, 0.025F, color, alpha * (1 - i * 0.2F), 12, i * 2);
            } else {
                LightningRenderer.stroke(facing(at, radius, 24), 0.03F, 0.03F, color, alpha * (1 - i * 0.2F), 12, i * 2);
            }
        }
        if (loudness >= 0.8F) for (int i = 0; i < 2; i++) mote(at, hue + 0.1F * i, random, 14);
    }

    /** A small rising wisp of colour, like a note leaving an instrument. */
    private static void mote(Vec3 at, float hue, RandomSource random, int life) {
        Vec3 start = at.add((random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.5);
        double phase = random.nextDouble() * Math.PI * 2;
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= 6; i++) path.add(start.add(Math.sin(phase + i * 0.9) * 0.08, i * 0.12, Math.cos(phase + i * 0.9) * 0.08));
        LightningRenderer.stroke(path, 0.03F, 0.005F, hue(hue, 0.5F), 0.6F, life, random.nextInt(3));
    }

    /** A cry: rings of every colour rolling down its length, wound about by two bright ribbons. */
    private static void wave(Vec3 from, Vec3 to, float size, boolean detail, boolean crescendo, RandomSource random) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 0.5) return;
        Vec3 dir = axis.normalize();
        Vec3 a = dir.cross(UP).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.cross(UP).normalize(), b = a.cross(dir).normalize();
        float base = clock();
        // Your own cry starts at your mouth: begin a little way out so it does not fill your view.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double skip = from.distanceToSqr(camera) < 2.5 * 2.5 ? 2.8 : 0;
        double spacing = crescendo ? 1.0 : 1.6;
        int rings = (int) Math.min(crescendo ? 40 : 24, length / spacing);
        for (int i = 1; i <= rings; i++) {
            double d = i * spacing;
            if (d < skip) continue;
            double radius = (0.25 + d * (crescendo ? 0.09 : 0.05)) * size;
            int delay = (int) (d / (crescendo ? 2.5 : 3.0));
            LightningRenderer.stroke(PrecogEffects.circle(from.add(dir.scale(d)), a, b, radius, 28), 0.04F * size, 0.04F * size,
                    hue(base + i * 0.045F, 0.65F), crescendo ? 0.7F : 0.55F, crescendo ? 14 : 9, delay);
        }
        int ribbons = crescendo ? 4 : 2;
        for (int r = 0; r < ribbons; r++) {
            List<Vec3> helix = new ArrayList<>();
            double phase = r * Math.PI * 2 / ribbons;
            int steps = (int) Math.min(80, length * 3);
            for (int s = 0; s <= steps; s++) {
                double d = length * s / steps, angle = phase + d * 1.4, radius = (0.18 + d * 0.035) * size;
                if (d < skip) continue;
                helix.add(from.add(dir.scale(d)).add(a.scale(Math.cos(angle) * radius)).add(b.scale(Math.sin(angle) * radius)));
            }
            LightningRenderer.stroke(helix, 0.05F * size, 0.02F * size, hue(base + 0.25F * r + 0.8F, 0.55F), 0.65F, crescendo ? 14 : 9, 0);
        }
        if (detail) for (int i = 0; i < (crescendo ? 10 : 4); i++) mote(from.add(dir.scale(random.nextDouble() * length)), base + random.nextFloat(), random, 16);
    }

    /** Sound going out in every direction: rings on three planes, growing. */
    private static void burst(Vec3 center, float radius, boolean quiet, RandomSource random) {
        Vec3 body = center.add(0, 1, 0);
        float base = clock();
        int steps = quiet ? 3 : 4;
        for (int i = 1; i <= steps; i++) {
            double r = radius * i / steps;
            int delay = i * 2, color = hue(base + i * 0.08F, 0.6F);
            float alpha = (quiet ? 0.35F : 0.55F) * (1.1F - i / (float) steps * 0.5F);
            LightningRenderer.stroke(PrecogEffects.circle(body, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r, 40), 0.05F, 0.05F, color, alpha, 12, delay);
            if (!quiet) {
                LightningRenderer.stroke(PrecogEffects.circle(body, new Vec3(1, 0, 0), UP, r * 0.8, 36), 0.04F, 0.04F, hue(base + i * 0.08F + 0.3F, 0.6F), alpha * 0.7F, 10, delay);
                LightningRenderer.stroke(PrecogEffects.circle(body, new Vec3(0, 0, 1), UP, r * 0.8, 36), 0.04F, 0.04F, hue(base + i * 0.08F + 0.6F, 0.6F), alpha * 0.7F, 10, delay);
            }
        }
    }

    /** The Resonant Note as it plays: a pulsing orb, a ring that turns, and colour drifting up from it. */
    private static void note(Vec3 at, float pulse, RandomSource random) {
        float base = clock() * 3;
        double radius = 0.22 + 0.1 * Math.sin(pulse * Math.PI * 2);
        LightningRenderer.stroke(facing(at, radius, 20), 0.08F, 0.08F, hue(base, 0.5F), 0.75F, 6, 0);
        LightningRenderer.stroke(facing(at, radius * 0.45, 12), 0.06F, 0.06F, 0xFFFFFF, 0.6F, 6, 0);
        double tilt = base * Math.PI * 2;
        Vec3 a = new Vec3(Math.cos(tilt), 0.35, Math.sin(tilt)).normalize(), b = a.cross(UP).normalize();
        LightningRenderer.stroke(PrecogEffects.circle(at, a, b, radius * 2.4, 32), 0.025F, 0.025F, hue(base + 0.33F, 0.6F), 0.5F, 6, 0);
        if (pulse > 0.4F && pulse < 0.5F || pulse < 0.1F) ripple(at, 1.2F, base + 0.5F, false, random);
        mote(at, base + 0.6F, random, 16);
    }

    /** The Note breaks: a sphere of colour bursts out to its full reach, with streaks thrown outward. */
    private static void noteBurst(Vec3 at, float radius, RandomSource random, boolean detail) {
        float base = clock();
        for (int i = 1; i <= 5; i++) {
            double r = radius * i / 5;
            float alpha = 0.75F - i * 0.08F;
            for (int plane = 0; plane < 3; plane++) {
                Vec3 a = plane == 0 ? new Vec3(1, 0, 0) : plane == 1 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
                Vec3 b = plane == 0 ? new Vec3(0, 0, 1) : UP;
                LightningRenderer.stroke(PrecogEffects.circle(at, a, b, r, 44), 0.07F, 0.07F, hue(base + i * 0.12F + plane * 0.2F, 0.65F), alpha, 14, i);
            }
        }
        if (detail) for (int i = 0; i < 18; i++) {
            Vec3 dir = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.3, random.nextDouble() - 0.5).normalize();
            LightningRenderer.stroke(List.of(at.add(dir.scale(radius * 0.3)), at.add(dir.scale(radius * (0.8 + random.nextDouble() * 0.4)))),
                    0.06F, 0.01F, hue(base + random.nextFloat(), 0.55F), 0.7F, 12, random.nextInt(4));
        }
        LightningRenderer.star(at, 0.9F, 0xFFFFFF, 0.8F, 8, 0);
    }

    /** The Wall of Sound: standing waves of colour across a five-block span. */
    private static void wall(Vec3 center, Vec3 ahead, float half, RandomSource random) {
        Vec3 normal = ahead.subtract(center).normalize();
        Vec3 side = new Vec3(-normal.z, 0, normal.x);
        float base = clock() * 4;
        for (int line = -4; line <= 4; line++) {
            List<Vec3> wave = new ArrayList<>();
            double x = half * line / 4.0;
            for (int s = 0; s <= 12; s++) {
                double y = -1.6 + 3.2 * s / 12;
                double bend = Math.sin(y * 3 + base * Math.PI * 2 + line) * 0.12;
                wave.add(center.add(side.scale(x)).add(0, y, 0).add(normal.scale(bend)));
            }
            LightningRenderer.stroke(wave, 0.04F, 0.04F, hue(base * 0.25F + line * 0.07F + 0.5F, 0.55F), 0.45F, 6, 0);
        }
        for (int row = -1; row <= 1; row++) {
            List<Vec3> across = new ArrayList<>();
            for (int s = 0; s <= 16; s++) {
                double x = -half + 2 * half * s / 16;
                across.add(center.add(side.scale(x)).add(0, row * 1.1 + Math.sin(x * 2 + base * 6) * 0.08, 0));
            }
            LightningRenderer.stroke(across, 0.03F, 0.03F, hue(base * 0.25F + 0.75F + row * 0.1F, 0.5F), 0.35F, 6, 0);
        }
    }

    /** Crescendo building: arcs of every colour circling the singer, drawn inward. */
    private static void charge(Vec3 eye, Vec3 ahead, float built, RandomSource random) {
        float base = clock() * 6;
        for (int i = 0; i < 4; i++) {
            double start = base * Math.PI * 2 + i * Math.PI / 2, radius = 0.6 + built * 0.8;
            List<Vec3> arc = new ArrayList<>();
            for (int s = 0; s <= 10; s++) {
                double angle = start + s * 0.18;
                arc.add(eye.add(Math.cos(angle) * radius, -0.4 + Math.sin(angle * 2) * 0.3, Math.sin(angle) * radius));
            }
            LightningRenderer.stroke(arc, 0.05F, 0.01F, hue(base * 0.2F + i * 0.25F, 0.6F), 0.7F, 6, 0);
        }
        Vec3 out = eye.add(new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize().scale(2 + built * 2));
        LightningRenderer.stroke(List.of(out, eye.lerp(out, 0.25)), 0.03F, 0.01F, hue(random.nextFloat(), 0.5F), 0.6F, 6, 0);
    }

    /** Rings rolling out across the ground. */
    private static void ground(Vec3 at, float radius, float alpha, int rings, RandomSource random) {
        float base = clock();
        for (int i = 1; i <= rings; i++)
            LightningRenderer.stroke(PrecogEffects.circle(at.add(0, 0.08, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius * i / rings, 40),
                    0.07F, 0.07F, hue(base + i * 0.1F, 0.6F), alpha, 12, i * 2);
    }

    private static List<Vec3> facing(Vec3 center, double radius, int points) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 view = center.subtract(camera);
        Vec3 left = UP.cross(view);
        if (left.lengthSqr() < 1.0E-6) left = new Vec3(1, 0, 0);
        left = left.normalize();
        return PrecogEffects.circle(center, left, view.cross(left).normalize(), radius, points);
    }
}
