package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Density drawn as surfaces: wherever D.L. Hawkins passes into something, its face ripples like water.
 * Ghost is a cold teal shimmer and a pale outline around the body; Dense is slate, stone dust and
 * cracks spreading under the feet.
 */
final class DensityEffects {
    static final int TEAL = 0x7FE8D8, PALE = 0xD8F4EE, SLATE = 0x6E7F86, STONE = 0x9A9284, DARK = 0x3E4A4E, WARN = 0xE0806A;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DensityEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        RandomSource random = level.random;
        switch (kind) {
            case DENSITY_RIPPLE -> ripple(origin, target.subtract(origin), size, TEAL, 18, 0);
            case DENSITY_SHIFT -> shift(origin, target, size);
            case DENSITY_GHOST -> ghost(level, origin, size, random);
            case DENSITY_DENSE -> dense(origin, size, random);
            case DENSITY_REACH -> reach(origin, target, size, random);
            case DENSITY_EMBED -> embed(origin, size, random);
            case DENSITY_SURFACE -> surface(origin, target, size, random);
            case DENSITY_HOLLOW -> hollow(origin, size, random, detail);
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
     * A surface rippling like water: rings opening one after another from the point of entry, each a
     * little wider and fainter, with a faint wobble so it never looks machined.
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

    /** A turn of the dial: rings climbing the body toward Ghost, or sinking down it into the ground toward Dense. */
    private static void shift(Vec3 feet, Vec3 target, float size) {
        double way = target.y - feet.y;
        int color = way < -0.5 ? TEAL : way > 0.5 ? STONE : PALE;
        for (int i = 0; i < 5; i++) {
            double h = way > 0.5 ? 1.9 - i * 0.45 : 0.1 + i * 0.45;
            LightningRenderer.stroke(PrecogEffects.circle(feet.add(0, h, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.55, 28),
                    0.04F * size, 0.04F * size, i == 2 ? PALE : color, 0.65F, 8, i);
        }
        if (way > 0.5) ripple(feet.add(0, 0.05, 0), UP, 1.2F, STONE, 14, 4);
    }

    /** Ghost: a pale, wavering outline around the body, as if it were only partly there. */
    private static void ghost(ClientLevel level, Vec3 feet, float strength, RandomSource random) {
        double t = level.getGameTime() * 0.3;
        int strands = strength >= 1 ? 6 : 3;
        for (int i = 0; i < strands; i++) {
            double angle = i * Math.PI * 2 / strands + t * 0.2;
            List<Vec3> strand = new ArrayList<>();
            for (int s = 0; s <= 8; s++) {
                double h = s / 8.0 * 1.9, sway = Math.sin(t + s * 0.9 + i) * 0.05;
                double r = 0.36 + (h > 1.45 ? -0.08 : 0) + sway;
                strand.add(feet.add(Math.cos(angle) * r, h, Math.sin(angle) * r));
            }
            LightningRenderer.stroke(strand, 0.045F, 0.03F, i % 2 == 0 ? TEAL : PALE, 0.4F * strength + 0.15F, 5, 0);
        }
        if (strength >= 1 && random.nextFloat() < 0.5F)
            LightningRenderer.stroke(PrecogEffects.circle(feet.add(0, 0.2 + random.nextDouble() * 1.5, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                    0.42, 24), 0.03F, 0.03F, PALE, 0.35F, 6, 0);
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

    /** Reach Through: the arm passing in, a ripple where it enters, and a slow pulse inside. */
    private static void reach(Vec3 hand, Vec3 at, float size, RandomSource random) {
        Vec3 dir = at.subtract(hand);
        if (dir.lengthSqr() < 1.0E-4) dir = UP;
        LightningRenderer.stroke(List.of(hand, at), 0.14F * size, 0.05F * size, TEAL, 0.45F, 8, 0);
        LightningRenderer.stroke(List.of(hand, at), 0.04F * size, 0.02F * size, PALE, 0.8F, 6, 0);
        ripple(at.subtract(dir.normalize().scale(0.3)), dir, 0.7F * size, TEAL, 14, 1);
        LightningRenderer.star(at, 0.25F * size, PALE, 0.8F, 6, 2);
        LightningRenderer.star(at, 0.18F * size, TEAL, 0.6F, 6, 8);
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

    /** Coming up out of the ground (or going down into it): shards of stone thrown up around a ripple. */
    private static void surface(Vec3 at, Vec3 toward, float size, RandomSource random) {
        boolean up = toward.y >= at.y;
        ripple(at.add(0, 0.05, 0), UP, 1.3F * size, up ? STONE : TEAL, 16, 0);
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2, r = 0.3 + random.nextDouble() * 0.5;
            Vec3 a = at.add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r);
            Vec3 b = a.add(Math.cos(angle) * 0.3, (up ? 1 : -0.4) * (0.6 + random.nextDouble()) * size, Math.sin(angle) * 0.3);
            LightningRenderer.stroke(List.of(a, b), 0.06F, 0.01F, i % 3 == 0 ? PALE : STONE, 0.6F, 10, random.nextInt(3));
        }
    }

    /** Hollow Ground: the ground itself goes soft, ripples rolling out across it from where you stand. */
    private static void hollow(Vec3 center, float radius, RandomSource random, boolean detail) {
        Vec3 at = center.add(0, 0.06, 0);
        int waves = detail ? 7 : 4;
        for (int i = 0; i < waves; i++) {
            double r = radius * (i + 1) / waves;
            List<Vec3> ring = new ArrayList<>();
            for (int s = 0; s <= 64; s++) {
                double angle = s / 64.0 * Math.PI * 2, wobble = 1 + Math.sin(angle * 7 + i * 1.3) * 0.03;
                ring.add(at.add(Math.cos(angle) * r * wobble, 0, Math.sin(angle) * r * wobble));
            }
            LightningRenderer.stroke(ring, 0.09F, 0.09F, i % 2 == 0 ? TEAL : PALE, 0.6F - i * 0.05F, 20, i * 3);
        }
        LightningRenderer.star(center.add(0, 0.2, 0), 0.6F, PALE, 0.8F, 8, 0);
    }

    /** Bedrock Slam: a shockwave of stone, the ground split in a star around the point of impact. */
    private static void slam(Vec3 center, float radius, RandomSource random) {
        Vec3 at = center.add(0, 0.05, 0);
        for (int i = 0; i < 3; i++)
            LightningRenderer.stroke(PrecogEffects.circle(at.add(0, i * 0.08, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
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
