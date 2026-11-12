package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;

/** Fire reads as rising heat: tapered tongues, embers and smoke. Blue flame is Flint's hotter core. */
final class PyroEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int CORE = 0xFFE08A, FLAME = 0xFF8A2A, EMBER = 0xFF4A1A;
    private static final int BLUE = 0x5AB4FF, WHITE_BLUE = 0xD6F1FF, STEAM = 0xE8EEF2;
    private PyroEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case FIRE_BOLT -> bolt(level, origin, target, false, detail);
            case BLUE_FLAME -> {
                if (origin.distanceToSqr(target) > 1.5) bolt(level, origin, target, true, detail);
                else nova(level, origin, size, true, detail);
            }
            case FIRE_STREAM -> {
                Vec3 direction = target.subtract(origin);
                int puffs = detail ? 10 : 4;
                for (int i = 0; i < puffs; i++) {
                    double t = (i + level.random.nextDouble()) / puffs;
                    Vec3 point = origin.add(direction.scale(t));
                    Vec3 drift = direction.normalize().scale(0.12).add(jitter(level, 0.04 + t * 0.06)).add(0, 0.02, 0);
                    vanilla(level, t > 0.7 && level.random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.FLAME, point, drift);
                }
                streak(level, origin, target, 0.09F * size, 6, FLAME);
                if (detail) streak(level, origin, origin.lerp(target, 0.6), 0.05F * size, 5, CORE);
            }
            case FIRE_RING -> {
                ring(level, origin.add(0, 0.06, 0), UP, size * 0.85F, size, 18, 0, FLAME, 0.55F);
                if (detail) ring(level, origin.add(0, 0.4, 0), UP, size * 0.9F, size * 1.05F, 14, 3, EMBER, 0.4F);
                int tongues = Math.clamp((int) (size * (detail ? 6 : 3)), 4, 32);
                for (int i = 0; i < tongues; i++) {
                    double angle = i * Math.PI * 2 / tongues + level.random.nextDouble() * 0.2;
                    Vec3 base = origin.add(Math.cos(angle) * size, 0.1, Math.sin(angle) * size);
                    vanilla(level, ParticleTypes.FLAME, base, new Vec3(0, 0.06 + level.random.nextDouble() * 0.05, 0));
                }
            }
            case FIRE_NOVA -> nova(level, origin, size, false, detail);
            case STEAM -> {
                int puffs = Math.clamp((int) (size * (detail ? 8 : 3)), 4, 36);
                for (int i = 0; i < puffs; i++) {
                    Vec3 point = origin.add(jitter(level, size * 0.7)).add(0, level.random.nextDouble() * size * 0.5, 0);
                    vanilla(level, ParticleTypes.CLOUD, point, new Vec3(0, 0.03 + level.random.nextDouble() * 0.03, 0).add(jitter(level, 0.02)));
                }
                ring(level, origin.add(0, 0.2, 0), UP, 0.3F, size * 1.2F, 20, 0, STEAM, 0.45F);
            }
            case PYRO_AWAKEN_CHARGE -> {
                float progress = Math.clamp(size, 0, 1);
                Vec3 chest = origin.add(0, 1.1, 0);
                helix(level, origin, 0.5F + progress * 0.3F, 2.0F, progress > 0.7 ? BLUE : FLAME, detail ? 10 : 4, 12);
                for (int i = 0; i < (detail ? 6 : 2); i++) {
                    Vec3 from = chest.add(jitter(level, 1.6 - progress));
                    mote(level, from, chest.subtract(from).scale(0.12), 0.06F, 10, i % 2 == 0 ? CORE : EMBER);
                }
                vanilla(level, ParticleTypes.FLAME, chest.add(jitter(level, 0.4)), new Vec3(0, 0.04, 0));
            }
            case PYRO_AWAKEN_RELEASE -> {
                nova(level, origin, size, false, detail);
                ring(level, origin.add(0, 0.1, 0), UP, size * 0.4F, size * 1.1F, 26, 4, EMBER, 0.5F);
            }
            default -> { return false; }
        }
        return true;
    }

    private static void bolt(ClientLevel level, Vec3 origin, Vec3 target, boolean blue, boolean detail) {
        int outer = blue ? BLUE : FLAME, inner = blue ? WHITE_BLUE : CORE;
        streak(level, origin, target, blue ? 0.08F : 0.065F, 10, outer);
        if (detail) streak(level, origin, target, 0.035F, 8, inner);
        double length = origin.distanceTo(target);
        int puffs = Math.clamp((int) (length * (detail ? 1.5 : 0.6)), 2, 40);
        ParticleOptions flame = blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        for (int i = 0; i < puffs; i++) vanilla(level, flame, origin.lerp(target, level.random.nextDouble()), jitter(level, 0.015));
        burst(level, target, UP, outer, detail ? 12 : 5, 0.12F);
        vanilla(level, ParticleTypes.LAVA, target, Vec3.ZERO);
    }

    private static void nova(ClientLevel level, Vec3 origin, float radius, boolean blue, boolean detail) {
        int outer = blue ? BLUE : FLAME, inner = blue ? WHITE_BLUE : CORE;
        ring(level, origin.add(0, 0.15, 0), UP, 0.3F, radius, 14, 0, inner, 0.75F);
        ring(level, origin.add(0, 0.6, 0), UP, 0.5F, radius * 0.9F, 18, 2, outer, 0.6F);
        if (detail) ring(level, origin.add(0, 1.2, 0), UP, 0.4F, radius * 0.7F, 20, 4, EMBER, 0.45F);
        burst(level, origin.add(0, 1, 0), UP, outer, detail ? 24 : 8, 0.25F + radius * 0.02F);
        ParticleOptions flame = blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        int jets = Math.clamp((int) (radius * (detail ? 5 : 2)), 6, 60);
        for (int i = 0; i < jets; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            Vec3 direction = new Vec3(Math.cos(angle), 0.15 + level.random.nextDouble() * 0.25, Math.sin(angle));
            vanilla(level, flame, origin.add(0, 0.5, 0), direction.scale(radius * 0.045));
        }
        if (detail) for (int i = 0; i < 8; i++)
            vanilla(level, ParticleTypes.LARGE_SMOKE, origin.add(jitter(level, radius * 0.4)).add(0, 1, 0), new Vec3(0, 0.08, 0));
    }

    private static Vec3 jitter(ClientLevel level, double scale) {
        return new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(scale * 2);
    }

    private static void vanilla(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.addParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }
}
