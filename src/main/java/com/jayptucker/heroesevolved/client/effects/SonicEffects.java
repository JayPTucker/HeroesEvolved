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
 * Sound as Emma Coolidge sees it: Sound Sight's rings of colour where a sound is made (see {@link SoundSight}),
 * and the quick strokes other powers borrow (Sylar's scream, the awakening's burst). Sonic Resonance's own moves
 * are set pieces drawn by {@code SonicArt}.
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
            case SONIC_WAVE -> wave(origin, target, size, detail, random);
            case SONIC_BURST, SONIC_SENSE -> burst(origin, size, kind == Kind.SONIC_SENSE, random);
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
    private static void wave(Vec3 from, Vec3 to, float size, boolean detail, RandomSource random) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 0.5) return;
        Vec3 dir = axis.normalize();
        Vec3 a = dir.cross(UP).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.cross(UP).normalize(), b = a.cross(dir).normalize();
        float base = clock();
        // Your own cry starts at your mouth: begin a little way out so it does not fill your view.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double skip = from.distanceToSqr(camera) < 2.5 * 2.5 ? 2.8 : 0;
        double spacing = 1.6;
        int rings = (int) Math.min(24, length / spacing);
        for (int i = 1; i <= rings; i++) {
            double d = i * spacing;
            if (d < skip) continue;
            double radius = (0.25 + d * 0.05) * size;
            int delay = (int) (d / 3.0);
            LightningRenderer.stroke(PrecogEffects.circle(from.add(dir.scale(d)), a, b, radius, 28), 0.04F * size, 0.04F * size,
                    hue(base + i * 0.045F, 0.65F), 0.55F, 9, delay);
        }
        int ribbons = 2;
        for (int r = 0; r < ribbons; r++) {
            List<Vec3> helix = new ArrayList<>();
            double phase = r * Math.PI * 2 / ribbons;
            int steps = (int) Math.min(80, length * 3);
            for (int s = 0; s <= steps; s++) {
                double d = length * s / steps, angle = phase + d * 1.4, radius = (0.18 + d * 0.035) * size;
                if (d < skip) continue;
                helix.add(from.add(dir.scale(d)).add(a.scale(Math.cos(angle) * radius)).add(b.scale(Math.sin(angle) * radius)));
            }
            LightningRenderer.stroke(helix, 0.05F * size, 0.02F * size, hue(base + 0.25F * r + 0.8F, 0.55F), 0.65F, 9, 0);
        }
        if (detail) for (int i = 0; i < 4; i++) mote(from.add(dir.scale(random.nextDouble() * length)), base + random.nextFloat(), random, 16);
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






    private static List<Vec3> facing(Vec3 center, double radius, int points) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 view = center.subtract(camera);
        Vec3 left = UP.cross(view);
        if (left.lengthSqr() < 1.0E-6) left = new Vec3(1, 0, 0);
        left = left.normalize();
        return PrecogEffects.circle(center, left, view.cross(left).normalize(), radius, points);
    }
}
