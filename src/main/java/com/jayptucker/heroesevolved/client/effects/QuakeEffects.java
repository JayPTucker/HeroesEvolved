package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Daisy Johnson's vibrations: clear, shimmering rings of pressure stacked along every blast (the way the
 * air warps around her hands on screen), a crack tearing across the ground with dust thrown up behind
 * it, heartbeats she alone can feel, and the price: her arms shaking, then cracking.
 */
final class QuakeEffects {
    static final int WAVE = 0xCFE6FF, CORE = 0xFFFFFF, DEEP = 0x7FA8D8, DUST = 0xA8946E, CRACK = 0x2A2622, HEART = 0xFF5A5A, CALM = 0x9FD8FF;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private QuakeEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        RandomSource random = level.random;
        switch (kind) {
            case QUAKE_RINGS -> rings(origin, target, size, random, detail);
            case QUAKE_RESONATE -> resonate(level, origin, target, size, random);
            case QUAKE_FISSURE -> fissure(origin, target, random);
            case QUAKE_ERUPT -> erupt(origin, size, random);
            case QUAKE_STRAIN -> strain(level, origin, target, size, random);
            case QUAKE_HEARTBEAT -> heartbeat(origin, size);
            case QUAKE_FRACTURE -> fracture(origin, target, random);
            default -> { return false; }
        }
        return true;
    }

    private static Vec3[] plane(Vec3 normal) {
        Vec3 n = normal.lengthSqr() < 1.0E-6 ? UP : normal.normalize();
        Vec3 a = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : n.cross(UP).normalize();
        return new Vec3[] {a, a.cross(n).normalize()};
    }

    private static List<Vec3> ring(Vec3 center, Vec3[] axes, double radius, int points, double wobble, double phase) {
        List<Vec3> ring = new ArrayList<>();
        for (int s = 0; s <= points; s++) {
            double angle = s / (double) points * Math.PI * 2, r = radius * (1 + Math.sin(angle * 6 + phase) * wobble);
            ring.add(center.add(axes[0].scale(Math.cos(angle) * r)).add(axes[1].scale(Math.sin(angle) * r)));
        }
        return ring;
    }

    /**
     * A blast: a tube of pressure rings opening out from the palm, each one a moment after the last so
     * the wave visibly travels, with two thin wavering strands of shaken air running along it.
     */
    private static void rings(Vec3 from, Vec3 to, float size, RandomSource random, boolean detail) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 1.0E-3) return;
        Vec3 dir = axis.scale(1 / length);
        Vec3[] axes = plane(dir);
        double spacing = detail ? 0.7 : 1.2;
        for (double d = 0.2; d <= length; d += spacing) {
            double t = d / length, radius = (0.18 + 0.55 * Math.sqrt(t)) * size;
            int delay = (int) (d / 3);
            LightningRenderer.stroke(ring(from.add(dir.scale(d)), axes, radius, 20, 0.05, d), 0.06F * size, 0.06F * size,
                    t < 0.15 ? CORE : WAVE, 0.8F - 0.3F * (float) t, 9, delay);
        }
        for (int strand = 0; strand < 2; strand++) {
            List<Vec3> line = new ArrayList<>();
            double phase = random.nextDouble() * Math.PI * 2;
            for (double d = 0; d <= length; d += 0.35) {
                double r = (0.12 + 0.3 * d / length) * size, a = phase + d * 2.2 + strand * Math.PI;
                line.add(from.add(dir.scale(d)).add(axes[0].scale(Math.cos(a) * r)).add(axes[1].scale(Math.sin(a) * r)));
            }
            LightningRenderer.stroke(line, 0.025F, 0.01F, DEEP, 0.35F, 5, 0);
        }
        // The pop where it lands.
        LightningRenderer.stroke(ring(to, axes, 0.9 * size, 28, 0.08, 0), 0.06F, 0.06F, CORE, 0.6F, 6, (int) (length / 3));
    }

    /**
     * Resonate: from her palm, a single trembling line; around the target, rings that shiver up and
     * down its body at its own frequency.
     */
    private static void resonate(ClientLevel level, Vec3 center, Vec3 target, float size, RandomSource random) {
        if (center.distanceToSqr(target) > 0.01) {
            Vec3 dir = target.subtract(center);
            double length = dir.length();
            Vec3[] axes = plane(dir);
            List<Vec3> line = new ArrayList<>();
            for (double d = 0; d <= length; d += 0.2)
                line.add(center.add(dir.scale(d / length)).add(axes[0].scale(Math.sin(d * 9) * 0.08)));
            LightningRenderer.stroke(line, 0.04F, 0.02F, WAVE, 0.7F, 6, 0);
            LightningRenderer.star(target, 0.4F, CORE, 0.8F, 5, 1);
            return;
        }
        double height = Math.max(0.6, size), t = level.getGameTime() * 0.9;
        Vec3[] flat = {new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        for (int i = 0; i < 3; i++) {
            double y = Math.sin(t + i * 2.1) * height * 0.4;
            LightningRenderer.stroke(ring(center.add(0, y, 0), flat, 0.5 + i * 0.1, 22, 0.12, t * 3 + i), 0.07F, 0.07F,
                    i == 0 ? CORE : WAVE, 0.75F, 5, 0);
        }
        Vec3 a = center.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * height, (random.nextDouble() - 0.5) * 0.6);
        LightningRenderer.star(a, 0.12F, WAVE, 0.6F, 4, 0);
    }

    /** One stretch of a fault: a dark jagged crack in the ground, dust kicked up along it. */
    private static void fissure(Vec3 from, Vec3 to, RandomSource random) {
        Vec3 a = from.add(0, 0.04, 0), b = to.add(0, 0.04, 0);
        List<Vec3> path = ElectroEffects.channel(a, b, random, 0.14, 2);
        List<Vec3> flat = new ArrayList<>();
        for (Vec3 v : path) flat.add(new Vec3(v.x, a.y + (v.y - a.y) * 0.1, v.z));
        LightningRenderer.stroke(flat, 0.16F, 0.12F, CRACK, 0.8F, 50, 0);
        LightningRenderer.stroke(flat, 0.05F, 0.04F, DEEP, 0.35F, 10, 0);
        for (int i = 0; i < 3; i++) {
            Vec3 at = a.lerp(b, random.nextDouble());
            LightningRenderer.stroke(List.of(at, at.add((random.nextDouble() - 0.5) * 0.4, 0.4 + random.nextDouble() * 0.8, (random.nextDouble() - 0.5) * 0.4)),
                    0.12F, 0.02F, DUST, 0.45F, 10, random.nextInt(3));
        }
    }

    /** The ground bursting up: a ring of pressure, and dust and stone thrown up out of it. */
    private static void erupt(Vec3 at, float size, RandomSource random) {
        Vec3[] flat = {new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        for (int i = 0; i < 3; i++)
            LightningRenderer.stroke(ring(at.add(0, 0.05 + i * 0.05, 0), flat, (0.5 + i * 0.4) * size, 28, 0.06, i), 0.07F, 0.07F,
                    i == 0 ? CORE : WAVE, 0.6F - i * 0.15F, 8, i);
        for (int i = 0; i < 7; i++) {
            double angle = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 0.6 * size;
            Vec3 a = at.add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r);
            LightningRenderer.stroke(List.of(a, a.add(Math.cos(angle) * 0.3, (0.8 + random.nextDouble() * 1.2) * size, Math.sin(angle) * 0.3)),
                    0.1F, 0.02F, i % 3 == 0 ? CORE : DUST, 0.5F, 10, random.nextInt(3));
        }
    }

    /** Past 80 Strain her forearms visibly shake: tight trembling lines along both arms. */
    private static void strain(ClientLevel level, Vec3 feet, Vec3 ahead, float strain, RandomSource random) {
        Vec3 look = ahead.subtract(feet);
        Vec3 forward = new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        for (int arm = -1; arm <= 1; arm += 2) {
            Vec3 shoulder = feet.add(side.scale(0.38 * arm)).add(0, 1.38, 0);
            Vec3 hand = shoulder.add(0, -0.62, 0).add(forward.scale(0.08));
            List<Vec3> line = new ArrayList<>();
            for (int s = 0; s <= 8; s++) {
                double t = s / 8.0, jitter = Math.sin(level.getGameTime() * 3.1 + s * 2.3) * 0.06 * strain;
                line.add(shoulder.lerp(hand, t).add(side.scale(jitter)).add(forward.scale(0.17)));
            }
            int color = strain >= 0.95F ? HEART : WAVE;
            LightningRenderer.stroke(line, 0.08F, 0.05F, color, 0.75F, 5, 0);
            // Pressure leaking from the palms she can no longer quite hold still.
            LightningRenderer.star(hand.add(forward.scale(0.17)), 0.22F + 0.1F * random.nextFloat(), color, 0.8F, 4, 0);
        }
    }

    /** A heartbeat felt through the walls: a double pulse, red where it is hostile or hurt. */
    private static void heartbeat(Vec3 heart, float size) {
        int color = size > 1 ? HEART : CALM;
        Vec3[] flat = {new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        LightningRenderer.star(heart, 0.18F * size, color, 0.9F, 6, 0);
        LightningRenderer.stroke(ring(heart, flat, 0.35 * size, 20, 0, 0), 0.04F, 0.04F, color, 0.6F, 6, 0);
        LightningRenderer.star(heart, 0.14F * size, color, 0.8F, 5, 5);
        LightningRenderer.stroke(ring(heart, flat, 0.55 * size, 24, 0, 0), 0.03F, 0.03F, color, 0.4F, 6, 5);
    }

    /** The bones give: white cracks up both arms and a red flash. */
    private static void fracture(Vec3 feet, Vec3 ahead, RandomSource random) {
        Vec3 look = ahead.subtract(feet);
        Vec3 forward = new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        for (int arm = -1; arm <= 1; arm += 2) {
            Vec3 shoulder = feet.add(side.scale(0.38 * arm)).add(0, 1.38, 0).add(forward.scale(0.18));
            List<Vec3> crack = ElectroEffects.channel(shoulder, shoulder.add(0, -0.65, 0), random, 0.25, 2);
            LightningRenderer.stroke(crack, 0.16F, 0.08F, HEART, 0.6F, 18, 0);
            LightningRenderer.stroke(crack, 0.07F, 0.03F, CORE, 1F, 18, 0);
            LightningRenderer.star(shoulder.add(0, -0.35, 0), 0.5F, HEART, 0.9F, 14, 0);
        }
        LightningRenderer.star(feet.add(0, 1.1, 0), 0.9F, HEART, 0.8F, 14, 0);
        Vec3[] flat = {new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        LightningRenderer.stroke(ring(feet.add(0, 1.0, 0), flat, 0.9, 32, 0.1, 0), 0.08F, 0.08F, HEART, 0.7F, 14, 0);
    }
}
