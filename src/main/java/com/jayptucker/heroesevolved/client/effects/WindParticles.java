package com.jayptucker.heroesevolved.client.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Air you can see in daylight. Flight's ribbons are faint against a bright sky, so its effects also
 * use vanilla's textured wind: Breeze gusts, small gusts, cloud and smoke puffs, and real dust kicked
 * off the block underneath. Counts follow the particle setting.
 */
final class WindParticles {
    private WindParticles() {}

    private static int scaled(int count) {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> count;
            case DECREASED -> Math.max(1, count / 2);
            case MINIMAL -> Math.max(1, count / 4);
        };
    }

    private static void spawn(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.addParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    /** A burst of compressed air: the wind charge's own gust, with small gusts around it. */
    static void gust(ClientLevel level, Vec3 at, int small) {
        spawn(level, ParticleTypes.GUST, at, Vec3.ZERO);
        for (int i = 0; i < scaled(small); i++)
            spawn(level, ParticleTypes.SMALL_GUST, at.add(jitter(level, 0.9)), Vec3.ZERO);
    }

    /** A ring of dust and ground debris thrown outward along the ground, like a shockwave landing. */
    static void groundBlast(ClientLevel level, Vec3 center, float radius, int count) {
        BlockState ground = groundBelow(level, center);
        int n = scaled(count);
        for (int i = 0; i < n; i++) {
            double angle = i * Math.PI * 2 / n + level.random.nextDouble() * 0.3;
            Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 at = center.add(out.scale(0.4 + level.random.nextDouble() * 0.6)).add(0, 0.15, 0);
            double speed = 0.18 + radius * 0.035;
            spawn(level, i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF, at, out.scale(speed).add(0, 0.02, 0));
            if (ground != null && i % 2 == 0)
                spawn(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), at, out.scale(speed * 1.6).add(0, 0.25 + level.random.nextDouble() * 0.2, 0));
        }
        for (int i = 0; i < scaled(4); i++) spawn(level, ParticleTypes.DUST_PLUME, center.add(jitter(level, radius * 0.3)).add(0, 0.2, 0), new Vec3(0, 0.05, 0));
    }

    /** Puffs drawn in from a sphere toward the centre: air being gathered. */
    static void gather(ClientLevel level, Vec3 center, double radius, int count) {
        for (int i = 0; i < scaled(count); i++) {
            Vec3 from = center.add(jitter(level, 1).normalize().scale(radius));
            spawn(level, ParticleTypes.WHITE_SMOKE, from, center.subtract(from).scale(0.09));
        }
    }

    /** Dust carried round and up a vertical column: a visible vortex. */
    static void swirl(ClientLevel level, Vec3 base, double radius, double height, int count, boolean clockwise) {
        BlockState ground = groundBelow(level, base);
        for (int i = 0; i < scaled(count); i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, t = level.random.nextDouble();
            double r = radius * (0.35 + 0.65 * t);
            Vec3 at = base.add(Math.cos(angle) * r, t * height, Math.sin(angle) * r);
            Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(clockwise ? -0.32 : 0.32);
            Vec3 velocity = tangent.add(new Vec3(-Math.cos(angle), 0, -Math.sin(angle)).scale(0.06)).add(0, 0.12, 0);
            spawn(level, i % 4 == 0 && ground != null && t < 0.3 ? new BlockParticleOption(ParticleTypes.BLOCK, ground)
                    : i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_SMOKE, at, velocity);
        }
    }

    /** A short wake of puffs behind something moving fast. */
    static void wake(ClientLevel level, Vec3 at, Vec3 axis, int count) {
        for (int i = 0; i < scaled(count); i++)
            spawn(level, ParticleTypes.CLOUD, at.subtract(axis.scale(0.4 + i * 0.4)).add(jitter(level, 0.25)),
                    axis.scale(-0.05).add(jitter(level, 0.02)));
    }

    /** A current along a lane: puffs carried from one end toward the other. */
    static void current(ClientLevel level, Vec3 from, Vec3 to, int count) {
        Vec3 along = to.subtract(from);
        Vec3 push = along.lengthSqr() < 1.0E-4 ? Vec3.ZERO : along.normalize().scale(0.35);
        for (int i = 0; i < scaled(count); i++)
            spawn(level, ParticleTypes.CLOUD, from.add(along.scale(level.random.nextDouble())).add(jitter(level, 0.5)).add(0, 0.3, 0), push);
    }

    /** An updraft: puffs rising fast in a column. */
    static void updraft(ClientLevel level, Vec3 base, double radius, int count) {
        for (int i = 0; i < scaled(count); i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, r = level.random.nextDouble() * radius;
            spawn(level, i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_SMOKE,
                    base.add(Math.cos(angle) * r, 0.2, Math.sin(angle) * r), new Vec3(0, 0.35 + level.random.nextDouble() * 0.25, 0));
        }
    }

    private static BlockState groundBelow(ClientLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 4; i++, pos = pos.below()) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE && state.getFluidState().isEmpty()) return state;
        }
        return null;
    }

    private static Vec3 jitter(ClientLevel level, double scale) {
        return new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(scale * 2);
    }
}
