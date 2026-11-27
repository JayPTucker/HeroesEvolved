package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Density's short effects, in earthbending's colours ({@code EarthArt}'s jade, stone and dark cracks): seismic rings in
 * the ground, cracks under heavy feet, a foe held in the ground, and a stance's shockwave. (Density used to be
 * phasing; the ghostly drawings it had for that are gone with it.)
 */
final class DensityEffects {
    static final int TEAL = 0x5FA36A, PALE = 0xCFEBC9, SLATE = 0x8A8580, STONE = 0x9A9284, DARK = 0x2B241E, WARN = 0xE0806A;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DensityEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        RandomSource random = level.random;
        switch (kind) {
            case DENSITY_RIPPLE -> ripple(origin, target.subtract(origin), size, TEAL, 18, 0);
            case DENSITY_DENSE -> dense(origin, size, random);
            case DENSITY_EMBED -> embed(origin, size, random);
            case DENSITY_SLAM -> slam(origin, size, random);
            case DENSITY_FOOTSTEP -> ripple(origin.add(0, 0.05, 0), UP, 0.45F * size, size > 1 ? WARN : PALE, 12, 0);
            default -> { return false; }
        }
        return true;
    }

    /** Two axes spanning the plane a surface faces out of. */
    private static Vec3[] plane(Vec3 normal) {
        Vec3 n = normal.lengthSqr() < 1.0E-6 ? UP : normal.normalize();
        Vec3 a = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : n.cross(UP).normalize();
        return new Vec3[] {a, a.cross(n).normalize()};
    }

    /**
     * A seismic ring in the ground: rings opening one after another from the point, each a little wider and fainter,
     * with a faint wobble so it never looks machined.
     */
    static void ripple(Vec3 center, Vec3 normal, float radius, int color, int life, int delay) {
        Vec3[] axes = plane(normal);
        for (int i = 0; i < 4; i++) {
            double r = radius * (0.25 + i * 0.28);
            List<Vec3> ring = new ArrayList<>();
            for (int s = 0; s <= 40; s++) {
                double angle = s / 40.0 * Math.PI * 2, wobble = 1 + Math.sin(angle * 5 + i) * 0.04;
                ring.add(center.add(axes[0].scale(Math.cos(angle) * r * wobble)).add(axes[1].scale(Math.sin(angle) * r * wobble)));
            }
            LightningRenderer.stroke(ring, 0.05F, 0.05F, i == 0 ? PALE : color, 0.7F - i * 0.14F, life - i * 2, delay + i * 2);
        }
    }

    /** Dense: cracks spreading in the ground under heavy feet, and stone dust. */
    private static void dense(Vec3 feet, float strength, RandomSource random) {
        Vec3 ground = feet.add(0, 0.03, 0);
        int cracks = strength >= 1 ? 4 : 2;
        for (int i = 0; i < cracks; i++) {
            double angle = random.nextDouble() * Math.PI * 2, length = 0.5 + random.nextDouble() * 0.6 * (1 + strength);
            List<Vec3> path = ElectroEffects.channel(ground, ground.add(Math.cos(angle) * length, 0, Math.sin(angle) * length), random, 0.12, 2);
            List<Vec3> flat = new ArrayList<>();
            for (Vec3 v : path) flat.add(new Vec3(v.x, ground.y, v.z));
            LightningRenderer.stroke(flat, 0.04F, 0.01F, DARK, 0.6F, 14, 0);
        }
        for (int i = 0; i < 3; i++)
            LightningRenderer.star(ground.add((random.nextDouble() - 0.5) * 0.8, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.8),
                    0.05F, STONE, 0.5F, 8, i);
    }

    /** An enemy stuck in the ground: a ring of broken stone around the waist and cracks running out from it. */
    private static void embed(Vec3 ground, float width, RandomSource random) {
        double radius = width * 0.6 + 0.25;
        Vec3 at = ground.add(0, 0.04, 0);
        List<Vec3> ring = new ArrayList<>();
        for (int s = 0; s <= 24; s++) {
            double angle = s / 24.0 * Math.PI * 2, r = radius * (0.9 + random.nextDouble() * 0.2);
            ring.add(at.add(Math.cos(angle) * r, random.nextDouble() * 0.06, Math.sin(angle) * r));
        }
        LightningRenderer.stroke(ring, 0.08F, 0.08F, DARK, 0.7F, 12, 0);
        LightningRenderer.stroke(ring, 0.03F, 0.03F, STONE, 0.6F, 12, 0);
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3 + random.nextDouble() * 0.5;
            Vec3 a = at.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            Vec3 b = a.add(Math.cos(angle) * (0.4 + random.nextDouble() * 0.5), 0, Math.sin(angle) * (0.4 + random.nextDouble() * 0.5));
            LightningRenderer.stroke(List.of(a, a.lerp(b, 0.5).add((random.nextDouble() - 0.5) * 0.15, 0, (random.nextDouble() - 0.5) * 0.15), b),
                    0.04F, 0.01F, DARK, 0.6F, 12, 0);
        }
    }

    /** Bedrock Slam: a shockwave of stone, the ground split in a star around the point of impact. */
    private static void slam(Vec3 center, float radius, RandomSource random) {
        Vec3 at = center.add(0, 0.05, 0);
        for (int i = 0; i < 3; i++)
            LightningRenderer.stroke(LightningRenderer.circle(at.add(0, i * 0.08, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                    radius * (0.4 + i * 0.3), 56), 0.16F - i * 0.03F, 0.16F - i * 0.03F, i == 0 ? PALE : STONE, 0.7F - i * 0.15F, 12, i * 2);
        for (int i = 0; i < 9; i++) {
            double angle = i * Math.PI * 2 / 9 + random.nextDouble() * 0.3;
            List<Vec3> path = ElectroEffects.channel(at, at.add(Math.cos(angle) * radius * 0.9, 0, Math.sin(angle) * radius * 0.9), random, 0.15, 3);
            List<Vec3> flat = new ArrayList<>();
            for (Vec3 v : path) flat.add(new Vec3(v.x, at.y, v.z));
            LightningRenderer.stroke(flat, 0.07F, 0.02F, DARK, 0.75F, 30, 0);
        }
        LightningRenderer.star(center.add(0, 0.4, 0), radius * 0.2F, PALE, 0.9F, 6, 0);
    }
}
