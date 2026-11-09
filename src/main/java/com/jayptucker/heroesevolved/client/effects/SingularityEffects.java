package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.PowerParticle.Shape.MOTE;

/**
 * Singularity: a black hole the size of a person.
 *
 * <p>The core is a black particle, so it writes depth: the far side of the accretion disk disappears behind
 * it while the near side crosses in front, the way a real one reads. Around it a thin photon ring faces you,
 * the disk turns in bands that spin faster the closer they are (hot gold inside, violet out), streaks of
 * matter spiral in, and dust is torn off the ground below. Nothing here touches the world.
 */
final class SingularityEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int HOT = 0xFFE3B0, GOLD = 0xFFC46A, VIOLET = 0x9A7CFF, DEEP = 0x5B3FD1, PALE = 0xF4EBFF, LENS = 0xC9B8FF;

    private SingularityEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case TIME_SINGULARITY -> hole(level, origin, axis(target.subtract(origin)), Math.clamp(size, 0, 1), detail);
            case TIME_SINGULARITY_COLLAPSE -> collapse(level, origin, axis(target.subtract(origin)), size, detail);
            default -> { return false; }
        }
        return true;
    }

    private static Vec3 axis(Vec3 v) { return v.lengthSqr() < 1.0E-6 ? UP : v.normalize(); }

    /** Two unit vectors spanning the plane with normal {@code n}. */
    static Vec3[] plane(Vec3 n) {
        Vec3 a = n.cross(Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : UP).normalize();
        return new Vec3[] {a, n.cross(a).normalize()};
    }

    /** Points on a circle (or an arc of it) around {@code center} in the plane with normal {@code n}. */
    static List<Vec3> arc(Vec3 center, Vec3 n, double radius, double from, double span, int points) {
        Vec3[] basis = plane(n);
        List<Vec3> result = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) {
            double angle = from + span * i / points;
            result.add(center.add(basis[0].scale(Math.cos(angle) * radius)).add(basis[1].scale(Math.sin(angle) * radius)));
        }
        return result;
    }

    private static Vec3 toCamera(Vec3 center) {
        Vec3 view = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(center);
        return view.lengthSqr() < 1.0E-4 ? UP : view.normalize();
    }

    private static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static void hole(ClientLevel level, Vec3 center, Vec3 normal, float growth, boolean detail) {
        RandomSource random = level.random;
        double core = 0.25 + 0.85 * growth;
        Vec3 view = toCamera(center);
        long time = level.getGameTime();

        // The core: black and soft-edged. Two layers so the middle is truly dark.
        for (int i = 0; i < 2; i++)
            add(new PowerParticle(level, MOTE, center, UP, (float) core * 1.2F, (float) core * 1.15F, 4, 0, 0x000000, 1.0F));

        // The photon ring, always facing you, and a soft violet glow just outside it.
        LightningRenderer.stroke(arc(center, view, core * 1.06, time * 0.3, Math.PI * 2, 32), 0.035F, 0.035F, PALE, 0.95F, 3, 0);
        LightningRenderer.stroke(arc(center, view, core * 1.28, 0, Math.PI * 2, 32), 0.2F, 0.2F, VIOLET, 0.32F, 3, 0);

        // The accretion disk: bands of an arc each, inner ones hot and fast, outer ones violet and slow.
        int bands = detail ? 6 : 3;
        for (int k = 0; k < bands; k++) {
            double t = bands == 1 ? 0 : k / (double) (bands - 1);
            double radius = core * (1.55 + k * (detail ? 0.42 : 0.8));
            double speed = 0.55 / Math.pow(radius / core, 1.5);
            double start = time * speed + k * 1.9;
            int color = blend(HOT, VIOLET, t);
            float width = (float) (0.05 + 0.025 * k) * (float) Math.max(0.5, growth);
            float alpha = (float) (0.95 - 0.45 * t) * Math.max(0.35F, growth);
            List<Vec3> lead = arc(center, normal, radius, start, Math.PI * (1.25 + 0.1 * k), detail ? 28 : 16);
            LightningRenderer.stroke(lead, width * 2.6F, width * 0.4F, k < 2 ? GOLD : DEEP, alpha * 0.35F, 3, 0);
            LightningRenderer.stroke(lead, width, width * 0.15F, color, alpha, 3, 0);
            if (detail) LightningRenderer.stroke(arc(center, normal, radius * 1.04, start + Math.PI * 1.45, Math.PI * 0.4, 8),
                    width * 0.7F, width * 0.1F, color, alpha * 0.6F, 3, 0);
        }

        // Matter falling in: streaks that wind three-quarters of a turn into the core.
        for (int i = 0; i < (detail ? 3 : 1); i++) {
            Vec3 tilt = normal.add(new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(0.5)).normalize();
            Vec3[] basis = plane(tilt);
            double from = 2.5 + random.nextDouble() * 3, phase = random.nextDouble() * Math.PI * 2;
            List<Vec3> spiral = new ArrayList<>();
            for (int s = 0; s <= 20; s++) {
                double u = s / 20.0, r = from + (core * 1.3 - from) * u * u, angle = phase + u * Math.PI * 1.5;
                spiral.add(center.add(basis[0].scale(Math.cos(angle) * r)).add(basis[1].scale(Math.sin(angle) * r)));
            }
            LightningRenderer.stroke(spiral, 0.008F, 0.04F, i % 2 == 0 ? LENS : GOLD, 0.6F, 4, 0);
        }

        // Light bending in: vanilla portal motes converge on the core.
        for (int i = 0; i < (detail ? 6 : 2); i++) {
            Vec3 offset = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize().scale(2.5 + random.nextDouble() * 3);
            level.addParticle(ParticleTypes.PORTAL, center.x, center.y, center.z, offset.x, offset.y, offset.z);
        }

        // Space rippling inward now and then.
        if (random.nextFloat() < (detail ? 0.2F : 0.08F)) for (int frame = 0; frame < 6; frame++) {
            final double r = core + (6.0 - core) * (1 - frame / 6.0);
            final float a = 0.1F + 0.04F * frame;
            ElectroEffects.later(frame, () -> LightningRenderer.stroke(arc(center, normal, r, 0, Math.PI * 2, 40), 0.03F, 0.03F, LENS, a, 2, 0));
        }

        // Dust torn off the ground below: block particles flung up and in. The ground itself is untouched.
        if (detail) for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble() * 6;
            BlockPos column = BlockPos.containing(center.x + Math.cos(angle) * r, center.y, center.z + Math.sin(angle) * r);
            for (int dy = 0; dy < 8; dy++) {
                BlockPos below = column.below(dy);
                BlockState state = level.getBlockState(below);
                if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) continue;
                Vec3 from = Vec3.atCenterOf(below).add(0, 0.55, 0);
                Vec3 pull = center.subtract(from).normalize().scale(0.35).add(0, 0.25, 0);
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), from.x, from.y, from.z, pull.x, pull.y, pull.z);
                break;
            }
        }
    }

    /** The fold snaps back: rings pinch to a point, a flash, then a shockwave and shells racing out. */
    private static void collapse(ClientLevel level, Vec3 center, Vec3 normal, float size, boolean detail) {
        boolean fizzle = size < 1;
        float radius = fizzle ? 2.0F : size;
        Vec3 view = toCamera(center);
        for (int frame = 0; frame < 4; frame++) {
            final double pinch = 1 - frame / 4.0;
            ElectroEffects.later(frame, () -> {
                LightningRenderer.stroke(arc(center, view, 3.2 * pinch, 0, Math.PI * 2, 40), 0.05F, 0.05F, PALE, 0.85F, 2, 0);
                LightningRenderer.stroke(arc(center, normal, 4.5 * pinch, 0, Math.PI * 2, 40), 0.06F, 0.06F, VIOLET, 0.6F, 2, 0);
            });
        }
        for (int i = 0; i < 2; i++) add(new PowerParticle(level, MOTE, center, UP, 1.2F, 0.05F, 5, 0, 0x000000, 1.0F));
        ElectroEffects.later(4, () -> {
            LightningRenderer.star(center, fizzle ? 1.0F : 2.6F, PALE, 1.0F, 5, 0);
            LightningRenderer.star(center, fizzle ? 1.6F : 4.2F, VIOLET, 0.5F, 6, 0);
            burst(level, center, normal, VIOLET, detail ? 24 : 8, 0.5F);
            for (int i = 0; i < (detail ? 16 : 6); i++) {
                Vec3 out = new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).normalize();
                level.addParticle(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, out.x * 0.6, out.y * 0.6, out.z * 0.6);
            }
            if (fizzle) return;
            // Shells: smooth loops in three planes, racing outward and fading.
            Vec3 third = normal.cross(view).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : normal.cross(view).normalize();
            int frames = detail ? 7 : 4;
            for (int frame = 0; frame < frames; frame++) {
                final double r = radius * (frame + 1) / frames;
                final float alpha = 0.8F * (1 - frame / (float) frames);
                ElectroEffects.later(frame, () -> {
                    for (Vec3 n : new Vec3[] {normal, view, third})
                        LightningRenderer.stroke(arc(center, n, r, 0, Math.PI * 2, 48), 0.06F, 0.06F, n == normal ? PALE : LENS, alpha, 2, 0);
                    // The shockwave proper: a bright band racing out across the disk plane.
                    LightningRenderer.stroke(arc(center, normal, r * 1.3, 0, Math.PI * 2, 56), 0.22F, 0.22F, VIOLET, alpha * 0.5F, 2, 0);
                    LightningRenderer.stroke(arc(center, normal, r * 1.3, 0, Math.PI * 2, 56), 0.07F, 0.07F, PALE, alpha, 2, 0);
                });
            }
        });
    }
}
