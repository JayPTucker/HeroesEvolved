package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The gold thread an awakening draws between a new power's owner and the ally it saves (and that Empathic
 * Mimicry's awakening draws to every mind nearby): a gently waving, hand-drawn stroke with two thin bristles.
 */
final class WardThreadEffects {
    private static final int GOLD = 0xF7D88D;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private WardThreadEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target) {
        if (kind != Kind.PROPHECY_THREAD) return false;
        RandomSource random = level.random;
        List<Vec3> points = wobble(origin, target, random, 0.05, 10);
        // A little wider and brighter than a bolt of the same width, so it reads as paint.
        LightningRenderer.stroke(points, 0.07F, 0.021F, GOLD, 0.66F, 16, 0);
        for (int b = 0; b < 2; b++) {
            Vec3 shift = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(0.11);
            List<Vec3> bristle = new ArrayList<>(points.size());
            for (Vec3 p : points) bristle.add(p.add(shift));
            LightningRenderer.stroke(bristle, 0.021F, 0.004F, GOLD, 0.4F, 16, 0);
        }
        return true;
    }

    /** A gently waving line from {@code a} to {@code b}. */
    private static List<Vec3> wobble(Vec3 a, Vec3 b, RandomSource random, double amount, int segments) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(UP).lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : dir.cross(UP).normalize();
        double phase = random.nextDouble() * Math.PI * 2, amplitude = Math.min(0.4, dir.length() * amount);
        List<Vec3> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            points.add(a.add(dir.scale(t)).add(side.scale(Math.sin(t * Math.PI * 2 + phase) * amplitude * Math.sin(t * Math.PI))));
        }
        return points;
    }
}
