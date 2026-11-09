package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;

/**
 * The Phoenix: a bird drawn in fire. Each tick the server says where it is, which way it faces and how
 * hard it is diving; this draws that pose (see {@link PhoenixGeometry}) in additive layers, a wide ember
 * glow, a flame body and a white-hot core, so it lights up the dark the way the rest of Pyrokinesis does.
 * Wings beat on their own clock; strokes live two ticks, so consecutive poses blur into motion.
 */
final class PhoenixEffects {
    private static final int GLOW = 0xFF3A0E, FLAME = 0xFF8A2A, CORE = 0xFFD98A, WHITE_HOT = 0xFFF6DC;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private PhoenixEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case PYRO_PHOENIX -> bird(level, origin, target.subtract(origin), size, detail);
            case PYRO_PHOENIX_STRIKE -> strike(level, origin, target.subtract(origin), size, detail);
            default -> { return false; }
        }
        return true;
    }

    private static void bird(ClientLevel level, Vec3 center, Vec3 heading, float dive, boolean detail) {
        RandomSource random = level.random;
        long time = level.getGameTime();
        // A deep, slow beat while it wheels; wings swept back and half folded in a dive.
        double sweep = Math.clamp((dive - 1.0) / 0.4, 0, 1);
        double flap = (1 - sweep) * (0.62 * Math.sin(time * 0.55 + center.x * 0.1) + 0.18) - sweep * 0.12;
        PhoenixGeometry.Pose pose = PhoenixGeometry.pose(center, heading, flap, sweep, time * 0.45);

        // Wings: the leading edge is the brightest line on the bird.
        for (List<Vec3> wing : List.of(pose.leftWing(), pose.rightWing())) {
            if (detail) LightningRenderer.stroke(wing, 0.38F, 0.14F, GLOW, 0.5F, 2, 0);
            LightningRenderer.stroke(wing, 0.15F, 0.05F, FLAME, 1.0F, 2, 0);
            LightningRenderer.stroke(wing, 0.055F, 0.015F, CORE, 1.0F, 2, 0);
        }
        // Flight feathers: broad tongues of flame trailing off the wing, close enough to read as a wing.
        for (List<Vec3> feather : pose.feathers()) {
            Vec3 flicker = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(0.14);
            List<Vec3> line = List.of(feather.get(0), feather.get(0).lerp(feather.get(1), 0.55).add(flicker.scale(0.4)), feather.get(1).add(flicker));
            if (detail) LightningRenderer.stroke(line, 0.24F, 0.03F, GLOW, 0.32F, 2, 0);
            LightningRenderer.stroke(line, 0.12F, 0.0F, FLAME, 0.8F, 2, 0);
        }
        // Body, head and crest.
        if (detail) LightningRenderer.stroke(pose.body(), 0.55F, 0.26F, GLOW, 0.55F, 2, 0);
        LightningRenderer.stroke(pose.body(), 0.28F, 0.12F, FLAME, 1.0F, 2, 0);
        LightningRenderer.stroke(pose.body(), 0.11F, 0.04F, WHITE_HOT, 1.0F, 2, 0);
        LightningRenderer.star(pose.head(), 0.42F, WHITE_HOT, 0.95F, 2, 0);
        for (List<Vec3> crest : pose.crest()) LightningRenderer.stroke(crest, 0.07F, 0.0F, CORE, 0.95F, 2, 0);
        // Tail: three long plumes that ripple.
        for (List<Vec3> plume : pose.tail()) {
            if (detail) LightningRenderer.stroke(plume, 0.3F, 0.0F, GLOW, 0.4F, 2, 0);
            LightningRenderer.stroke(plume, 0.14F, 0.0F, FLAME, 0.9F, 2, 0);
            LightningRenderer.stroke(plume, 0.04F, 0.0F, CORE, 0.8F, 2, 0);
        }

        // Embers shed from the wings and tail: glowing motes that drift back and down and fade.
        Vec3 back = heading.lengthSqr() < 1.0E-6 ? Vec3.ZERO : heading.normalize().scale(-0.04);
        List<List<Vec3>> parts = pose.all();
        for (int i = 0; i < (detail ? 4 : 1); i++) {
            List<Vec3> part = parts.get(random.nextInt(parts.size()));
            Vec3 at = part.get(random.nextInt(part.size()));
            mote(level, at, back.add(0, -0.02 - random.nextDouble() * 0.02, 0), 0.06F + random.nextFloat() * 0.04F,
                    10 + random.nextInt(10), i % 3 == 0 ? CORE : i % 3 == 1 ? FLAME : GLOW);
        }
        if (random.nextInt(6) == 0) {
            Vec3 tip = pose.tail().get(random.nextInt(3)).getLast();
            level.addParticle(ParticleTypes.LAVA, tip.x, tip.y, tip.z, 0, 0, 0);
        }
    }

    /** Where it hits (or where it is born and where it comes home): a flash, a ring of fire, flame and feathers. */
    private static void strike(ClientLevel level, Vec3 at, Vec3 velocity, float size, boolean detail) {
        RandomSource random = level.random;
        Vec3 axis = velocity.lengthSqr() < 1.0E-6 ? UP : velocity.normalize();
        // The kit's own fire nova, centred under the hit.
        PyroEffects.play(level, Kind.FIRE_NOVA, at.subtract(0, 1, 0), at, Math.max(1.2F, size), detail);
        LightningRenderer.star(at, 0.6F * size, WHITE_HOT, 1.0F, 4, 0);
        LightningRenderer.star(at, 1.0F * size, GLOW, 0.5F, 5, 0);
        ring(level, at, UP, 0.3F, 1.6F * size, 12, 0, FLAME, 0.65F);
        if (detail) ring(level, at, axis, 0.2F, 1.2F * size, 10, 2, CORE, 0.5F);
        int tongues = detail ? 12 : 5;
        for (int i = 0; i < tongues; i++) {
            Vec3 out = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.2, random.nextDouble() - 0.5).normalize().add(axis.scale(0.6)).normalize();
            Vec3 end = at.add(out.scale((0.7 + random.nextDouble() * 0.7) * size));
            LightningRenderer.stroke(List.of(at, at.lerp(end, 0.5).add(0, 0.1, 0), end), 0.13F * size, 0.0F, i % 3 == 0 ? CORE : FLAME, 0.9F, 5, 0);
        }
        // Loose feathers of flame drifting down.
        for (int i = 0; i < (detail ? 8 : 3); i++) {
            Vec3 drift = new Vec3(random.nextDouble() - 0.5, random.nextDouble() * 0.6, random.nextDouble() - 0.5).scale(0.18 * size);
            mote(level, at, drift, 0.08F, 14 + random.nextInt(8), i % 2 == 0 ? CORE : FLAME);
        }
        for (int i = 0; i < (detail ? 14 : 5); i++) {
            Vec3 v = new Vec3(random.nextDouble() - 0.5, random.nextDouble() * 0.5, random.nextDouble() - 0.5).scale(0.3);
            level.addParticle(i % 4 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }
}
