package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.ring;

/**
 * Ted Sprague's radiation, in two colours: heat (Radiant Heat and the beam) is orange going white-hot;
 * contamination and sickness are a sickly yellow-green. Glows use the additive ribbons the lightning
 * uses, so they read in daylight; embers, ash and motes are vanilla particles.
 */
final class RadiationEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int EMBER = 0xFF4A0C, HEAT = 0xFF9330, WHITE_HOT = 0xFFF2D2;
    private static final int TOXIC = 0xC4F23C, TOXIC_DEEP = 0x5FAE1E, EMP = 0x9ADCFF;
    private static final DustParticleOptions TOXIC_MOTE = new DustParticleOptions(new Vector3f(0.75F, 1.0F, 0.3F), 1.2F);
    private static final DustParticleOptions TOXIC_DARK = new DustParticleOptions(new Vector3f(0.35F, 0.65F, 0.12F), 1.0F);

    private RadiationEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case RADIATION_CONE -> cone(level, origin, target, size, detail);
            case RADIATION_PULSE -> pulse(level, origin, size, detail);
            case RADIATION_FIELD -> field(level, origin, size, detail);
            case RADIATION_BEAM, NUCLEAR_RAY -> beam(level, origin, target, kind == Kind.NUCLEAR_RAY ? 1.2F : size, detail);
            case RADIATION_DOSE -> dose(level, origin, size, detail);
            case RADIATION_CHAIN -> chain(level, origin, size, detail);
            case NUCLEAR_EMP -> emp(level, origin, size, detail);
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------

    /** Radiant Heat: white-hot rays fanning from the palms, rippling heat, embers carried outward. */
    private static void cone(ClientLevel level, Vec3 hand, Vec3 end, float size, boolean detail) {
        Vec3 delta = end.subtract(hand);
        double length = delta.length();
        if (length < 0.1) return;
        Vec3 axis = delta.normalize(), right = side(axis), up = right.cross(axis).normalize();
        double spread = 0.62;
        int rays = (detail ? 9 : 5) + (size > 1.2F ? 4 : 0);
        for (int i = 0; i < rays; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * spread;
            Vec3 direction = axis.add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r)).normalize();
            List<Vec3> ray = wavy(hand, hand.add(direction.scale(length * (0.7 + level.random.nextDouble() * 0.3))), right, up, 0.08, level);
            int delay = level.random.nextInt(2);
            LightningRenderer.stroke(ray, 0.05F, 0.34F, EMBER, 0.45F, 5, delay);
            LightningRenderer.stroke(ray, 0.02F, 0.12F, size > 1.2F ? WHITE_HOT : HEAT, 0.9F, 4, delay);
        }
        // Heat ripples: short arcs across the cone, not full hoops, and never right in front of your own eyes.
        for (int i = 2; i <= (detail ? 4 : 3); i++) {
            double distance = length * i / 5.0;
            Vec3 at = hand.add(axis.scale(distance));
            if (!nearCamera(at, 2.5)) arc(level, at, axis, distance * spread * 0.8, 0.04F, EMBER, HEAT, 0.45F, 6, i - 2, 1.6);
        }
        if (!nearCamera(hand, 1.5)) LightningRenderer.star(hand, 0.35F, WHITE_HOT, 0.9F, 4, 0);
        for (int i = 0; i < (detail ? 14 : 5); i++) {
            Vec3 velocity = axis.add(right.scale((level.random.nextDouble() - 0.5) * 1.2)).add(up.scale((level.random.nextDouble() - 0.5) * 1.2))
                    .normalize().scale(0.25 + level.random.nextDouble() * 0.25);
            vanilla(level, i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, hand.add(axis.scale(0.3)), velocity);
        }
    }

    /** Contamination: a sickly wave rolling out along the ground, spores lifting off it. */
    private static void pulse(ClientLevel level, Vec3 feet, float radius, boolean detail) {
        Vec3 floor = feet.add(0, 0.12, 0);
        ring(level, floor, UP, 0.4F, radius, 16, 0, TOXIC, 0.6F);
        for (int i = 0; i < 6; i++) loop(level, floor.add(0, 0.05 * i, 0), UP, radius * (i + 1) / 6.0, 0.05F, TOXIC_DEEP, TOXIC, 0.7F - i * 0.07F, 5, i);
        LightningRenderer.star(feet.add(0, 1, 0), 0.9F, TOXIC, 0.8F, 5, 0);
        for (int i = 0; i < (detail ? 36 : 12); i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            vanilla(level, i % 2 == 0 ? TOXIC_MOTE : TOXIC_DARK, floor.add(out.scale(0.5 + level.random.nextDouble() * radius * 0.4)),
                    out.scale(0.25 + level.random.nextDouble() * 0.2).add(0, 0.03, 0));
        }
    }

    /** A contaminated patch: a faint boundary and motes rising from the ground inside it. */
    private static void field(ClientLevel level, Vec3 center, float radius, boolean detail) {
        loop(level, center.add(0, 0.1, 0), UP, radius, 0.04F, TOXIC_DEEP, TOXIC, 0.45F, 12, 0);
        for (int i = 0; i < (detail ? 10 : 4); i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * radius;
            Vec3 at = center.add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);
            vanilla(level, i % 3 == 0 ? TOXIC_DARK : TOXIC_MOTE, at, new Vec3(0, 0.02 + level.random.nextDouble() * 0.03, 0));
            if (detail && i % 4 == 0) vanilla(level, ParticleTypes.MYCELIUM, at, Vec3.ZERO);
        }
    }

    /** Controlled Emission: a white-hot core in an orange halo, heat rings travelling along it, a molten splash where it lands. */
    private static void beam(ClientLevel level, Vec3 hand, Vec3 end, float intensity, boolean detail) {
        Vec3 delta = end.subtract(hand);
        if (delta.lengthSqr() < 0.01) return;
        Vec3 axis = delta.normalize(), right = side(axis), up = right.cross(axis).normalize();
        List<Vec3> line = wavy(hand, end, right, up, 0.03, level);
        float width = 0.12F + 0.12F * intensity;
        LightningRenderer.stroke(line, width * 2.4F, width * 3.2F, EMBER, 0.5F, 3, 0);
        LightningRenderer.stroke(line, width, width * 1.2F, HEAT, 0.9F, 3, 0);
        LightningRenderer.stroke(line, width * 0.35F, width * 0.4F, WHITE_HOT, 1F, 3, 0, false);
        double length = delta.length();
        int rings = (int) Math.min(detail ? 10 : 4, length / 3);
        double phase = (level.getGameTime() % 10) / 10.0;
        for (int i = 0; i < rings; i++) {
            // Heat rings travel along the beam, starting a few blocks out so they never fill your view.
            Vec3 at = hand.add(axis.scale(3 + (length - 3) * (i + phase) / Math.max(1, rings)));
            if (!nearCamera(at, 3)) loop(level, at, axis, width * 1.6, 0.025F, EMBER, HEAT, 0.5F, 3, 0);
        }
        LightningRenderer.star(end, 0.6F + intensity * 0.3F, WHITE_HOT, 0.95F, 3, 0);
        if (!nearCamera(hand, 1.5)) LightningRenderer.star(hand, 0.3F, WHITE_HOT, 0.8F, 3, 0);
        for (int i = 0; i < (detail ? 4 : 1); i++)
            vanilla(level, i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LAVA, end, jitter(level, 0.12).add(axis.scale(-0.08)));
        if (detail && level.random.nextFloat() < 0.4F) vanilla(level, ParticleTypes.LARGE_SMOKE, end, new Vec3(0, 0.05, 0));
    }

    /** A creature taking a dose: a sickly glint and a few motes around it. */
    private static void dose(ClientLevel level, Vec3 center, float width, boolean detail) {
        LightningRenderer.star(center, 0.5F + width * 0.4F, TOXIC, 0.75F, 5, 1);
        for (int i = 0; i < (detail ? 8 : 3); i++) vanilla(level, TOXIC_MOTE, center.add(jitter(level, 0.4 + width * 0.3)), new Vec3(0, 0.03, 0));
    }

    /** Chain reaction: a sick creature bursts in a green-white flash that throws contamination outward. */
    private static void chain(ClientLevel level, Vec3 center, float radius, boolean detail) {
        LightningRenderer.star(center, 1.4F, WHITE_HOT, 1F, 5, 0);
        LightningRenderer.star(center, 2.2F, TOXIC, 0.7F, 7, 0);
        ring(level, center, UP, 0.3F, radius, 12, 0, TOXIC, 0.7F);
        for (int i = 0; i < (detail ? 8 : 4); i++) {
            double angle = i * Math.PI * 2 / (detail ? 8 : 4) + level.random.nextDouble() * 0.3;
            Vec3 tip = center.add(Math.cos(angle) * radius, (level.random.nextDouble() - 0.3) * radius * 0.5, Math.sin(angle) * radius);
            List<Vec3> streak = wavy(center, tip, side(tip.subtract(center).normalize()), UP, 0.12, level);
            LightningRenderer.stroke(streak, 0.12F, 0.02F, TOXIC, 0.85F, 6, 0);
        }
        for (int i = 0; i < (detail ? 24 : 8); i++)
            vanilla(level, i % 2 == 0 ? TOXIC_MOTE : ParticleTypes.POOF, center, jitter(level, 0.3));
    }

    /** EMP (and the bright flash): a blue-white shell expanding over a few ticks, crackling as it goes. */
    private static void emp(ClientLevel level, Vec3 feet, float radius, boolean detail) {
        Vec3 chest = feet.add(0, 1, 0);
        LightningRenderer.star(chest, 1.2F, 0xFFFFFF, 1F, 4, 0);
        int shells = detail ? 6 : 3;
        for (int i = 1; i <= shells; i++) {
            double r = radius * i / shells;
            for (int k = 0; k < (detail ? 3 : 2); k++) {
                Vec3 axis = k == 0 ? UP : new Vec3(Math.cos(k * 2.1 + i), 0.3, Math.sin(k * 2.1 + i)).normalize();
                loop(level, chest, axis, r, 0.04F + 0.01F * i, EMP, 0xFFFFFF, 0.75F - i * 0.08F, 4, i);
            }
        }
        ring(level, feet.add(0, 0.1, 0), UP, 0.4F, radius, 10, 0, EMP, 0.6F);
        if (detail) for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            ElectroEffects.bolt(level, chest, chest.add(Math.cos(angle) * radius * 0.6, (level.random.nextDouble() - 0.5) * 2, Math.sin(angle) * radius * 0.6),
                    0.03F, 3, level.random.nextInt(3), 0.7F, false, 0.3, 0);
        }
    }

    // ------------------------------------------------------------------

    /** A ring of glow around {@code axis}, slightly irregular, drawn {@code delay} ticks from now. */
    private static void loop(ClientLevel level, Vec3 center, Vec3 axis, double radius, float width, int glow, int color,
                             float alpha, int life, int delay) {
        Vec3 normal = axis.normalize(), right = side(normal), up = right.cross(normal).normalize();
        List<Vec3> points = new ArrayList<>();
        double phase = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i <= 20; i++) {
            double angle = phase + i * Math.PI * 2 / 20, r = radius * (0.94 + level.random.nextDouble() * 0.12);
            points.add(center.add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r)));
        }
        LightningRenderer.stroke(points, width * 3, width * 3, glow, alpha * 0.4F, life + 1, delay);
        LightningRenderer.stroke(points, width, width, color, alpha, life, delay);
    }

    /** Part of a ring: {@code span} radians of it, starting at a random angle. */
    private static void arc(ClientLevel level, Vec3 center, Vec3 axis, double radius, float width, int glow, int color,
                            float alpha, int life, int delay, double span) {
        Vec3 normal = axis.normalize(), right = side(normal), up = right.cross(normal).normalize();
        List<Vec3> points = new ArrayList<>();
        double start = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i <= 8; i++) {
            double angle = start + span * i / 8, r = radius * (0.95 + level.random.nextDouble() * 0.1);
            points.add(center.add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r)));
        }
        LightningRenderer.stroke(points, width * 0.3F, width * 2.5F, glow, alpha * 0.4F, life + 1, delay);
        LightningRenderer.stroke(points, width * 0.2F, width, color, alpha, life, delay);
    }

    private static boolean nearCamera(Vec3 at, double distance) {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(at) < distance * distance;
    }

    /** A straight path with a gentle shimmer, like air over a hot road. */
    private static List<Vec3> wavy(Vec3 from, Vec3 to, Vec3 right, Vec3 up, double amount, ClientLevel level) {
        List<Vec3> points = new ArrayList<>();
        double phase = level.random.nextDouble() * Math.PI * 2, length = from.distanceTo(to);
        int segments = (int) Math.clamp(length * 2, 4, 32);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments, wave = Math.sin(t * length * 2.4 + phase) * amount * Math.sin(t * Math.PI);
            points.add(from.lerp(to, t).add(right.scale(wave)).add(up.scale(Math.cos(t * length * 1.7 + phase) * amount * 0.6 * Math.sin(t * Math.PI))));
        }
        return points;
    }

    private static Vec3 side(Vec3 axis) {
        return axis.cross(Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP).normalize();
    }

    private static Vec3 jitter(ClientLevel level, double scale) {
        return new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(scale * 2);
    }

    private static void vanilla(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.addParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }
}
