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

/**
 * Claire Bennet's regeneration reads as blood moving the wrong way: drawn back into the body as wounds
 * close, flowing along a thread to someone she saves, pulsing with a heartbeat when someone comes back.
 * Crimson for blood, warm gold for life. Glows use the additive ribbons; droplets are vanilla dust.
 */
final class RegenerationEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int BLOOD = 0xB0101C, BRIGHT = 0xFF4050, LIFE = 0xFFD9A0, PALE = 0xFFC4C8;
    private static final DustParticleOptions DROP = new DustParticleOptions(new Vector3f(0.72F, 0.03F, 0.08F), 1.0F);
    private static final DustParticleOptions BRIGHT_DROP = new DustParticleOptions(new Vector3f(1.0F, 0.28F, 0.3F), 1.2F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.86F, 0.55F), 0.9F);

    private RegenerationEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case REGENERATION -> passive(level, origin, size, detail);
            case REGEN_MEND -> mend(level, origin, size, detail);
            case BLOOD_THREAD -> thread(level, origin, target, size, detail, false);
            case REGEN_BOND -> thread(level, origin, target, 0.6F, detail, true);
            case HEALING_AURA -> bond(level, origin, size, detail);
            case REGEN_EJECT -> eject(level, origin, target, detail);
            case REGEN_RECKLESS -> reckless(level, origin, target, detail);
            case REGEN_SECOND_LIFE -> secondLife(level, origin, size, detail);
            case REGEN_REVIVE -> revive(level, origin, size, detail);
            case REGEN_SEVER -> sever(level, origin, target, detail);
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------

    /** The quiet passive: a few drops drawn back into the body and a faint warm glint. */
    private static void passive(ClientLevel level, Vec3 feet, float size, boolean detail) {
        Vec3 chest = feet.add(0, 1.1, 0);
        for (int i = 0; i < (detail ? 6 : 2); i++) {
            Vec3 from = onShell(level, chest, 0.9 * size);
            vanilla(level, i % 3 == 0 ? BRIGHT_DROP : DROP, from, chest.subtract(from).scale(0.12));
        }
        if (detail) spiral(level, feet, 0.45 * size, 1.9, BRIGHT, 0.45F, 8, 1);
        LightningRenderer.star(chest, 0.25F * size, LIFE, 0.45F, 6, 2);
    }

    /** Rapid Mending: blood rushes back in from all around, bright strands climb the body, and the breaks knit with a glint. */
    private static void mend(ClientLevel level, Vec3 feet, float strength, boolean detail) {
        Vec3 chest = feet.add(0, 1.1, 0);
        for (int i = 0; i < (detail ? 26 : 10); i++) {
            Vec3 from = onShell(level, chest, 1.3 + level.random.nextDouble() * 0.6);
            vanilla(level, i % 3 == 0 ? BRIGHT_DROP : DROP, from, chest.subtract(from).scale(0.14));
        }
        spiral(level, feet, 0.55, 2.0, BRIGHT, 0.85F, 10, 0);
        spiral(level, feet.add(0, 0.05, 0), 0.5, 1.9, LIFE, 0.6F, 12, 2);
        // Where the bones click back into place.
        for (int i = 0; i < (detail ? 4 : 2); i++)
            LightningRenderer.star(feet.add((level.random.nextDouble() - 0.5) * 0.6, 0.3 + level.random.nextDouble() * 1.4,
                    (level.random.nextDouble() - 0.5) * 0.6), 0.22F, 0xFFFFFF, 0.9F, 4, 2 + i * 2);
        LightningRenderer.star(chest, 0.6F + strength * 0.2F, BRIGHT, 0.7F, 6, 0);
        loop(level, feet.add(0, 0.08, 0), UP, 0.8 + strength * 0.3, 0.04F, BLOOD, BRIGHT, 0.6F, 10, 0);
        vanilla(level, ParticleTypes.HEART, feet.add(0, 2.1, 0), new Vec3(0, 0.05, 0));
    }

    /** A thread of blood: a soft crimson ribbon on a gentle arc with drops flowing along it. */
    private static void thread(ClientLevel level, Vec3 from, Vec3 to, float strength, boolean detail, boolean drawnIn) {
        double length = from.distanceTo(to);
        if (length < 0.1) return;
        List<Vec3> arc = new ArrayList<>();
        int segments = (int) Math.clamp(length * 3, 6, 36);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            arc.add(from.lerp(to, t).add(0, Math.sin(t * Math.PI) * Math.min(1.2, length * 0.12), 0));
        }
        float width = 0.05F + 0.04F * strength;
        LightningRenderer.stroke(arc, width * 2.5F, width * 2.5F, BLOOD, 0.45F, drawnIn ? 8 : 12, 0);
        LightningRenderer.stroke(arc, width * 0.5F, width * 0.5F, drawnIn ? BRIGHT : PALE, 0.85F, drawnIn ? 7 : 10, 0);
        for (int i = 0; i < (detail ? 10 : 4); i++) {
            int index = level.random.nextInt(arc.size() - 1);
            Vec3 at = arc.get(index), next = arc.get(index + 1);
            vanilla(level, i % 2 == 0 ? BRIGHT_DROP : DROP, at, next.subtract(at).normalize().scale(0.2));
        }
        LightningRenderer.star(to, 0.35F + 0.2F * strength, drawnIn ? BRIGHT : LIFE, 0.85F, 6, 1);
    }

    /** Blood Bond: a crimson ring on the ground at its reach and warm motes rising inside it. */
    private static void bond(ClientLevel level, Vec3 feet, float radius, boolean detail) {
        loop(level, feet.add(0, 0.1, 0), UP, radius, 0.05F, BLOOD, BRIGHT, 0.6F, 18, 0);
        loop(level, feet.add(0, 0.15, 0), UP, radius * 0.4, 0.04F, BLOOD, LIFE, 0.5F, 14, 3);
        for (int i = 0; i < (detail ? 14 : 5); i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * radius;
            vanilla(level, i % 3 == 0 ? GOLD : DROP, feet.add(Math.cos(angle) * r, 0.2, Math.sin(angle) * r), new Vec3(0, 0.04, 0));
        }
    }

    /** The bullet comes back out: a glint, a puff of blood the way it came, sparks. */
    private static void eject(ClientLevel level, Vec3 wound, Vec3 outward, boolean detail) {
        Vec3 out = outward.subtract(wound);
        if (out.lengthSqr() < 1.0E-4) out = UP;
        out = out.normalize();
        LightningRenderer.star(wound, 0.4F, 0xFFFFFF, 1F, 4, 0);
        for (int i = 0; i < (detail ? 10 : 4); i++)
            vanilla(level, i % 2 == 0 ? BRIGHT_DROP : DROP, wound, out.scale(0.15).add(jitter(level, 0.08)));
        for (int i = 0; i < (detail ? 5 : 2); i++) vanilla(level, ParticleTypes.CRIT, wound, out.scale(0.3).add(jitter(level, 0.15)));
    }

    /** A reckless punch: a white crack where it lands, red crit sparks, a little of her own blood. */
    private static void reckless(ClientLevel level, Vec3 hit, Vec3 from, boolean detail) {
        Vec3 axis = hit.subtract(from);
        axis = axis.lengthSqr() < 1.0E-4 ? UP : axis.normalize();
        LightningRenderer.star(hit, 0.55F, 0xFFFFFF, 1F, 3, 0);
        loop(level, hit, axis, 0.5, 0.05F, BLOOD, BRIGHT, 0.8F, 5, 0);
        for (int i = 0; i < (detail ? 10 : 4); i++) vanilla(level, ParticleTypes.CRIT, hit, jitter(level, 0.4));
        for (int i = 0; i < (detail ? 4 : 1); i++) vanilla(level, DROP, from.lerp(hit, 0.6), jitter(level, 0.06));
    }

    /** Second Life: her blood climbs the friend in gold and crimson, and two heartbeats pulse out from their chest. */
    private static void secondLife(ClientLevel level, Vec3 feet, float height, boolean detail) {
        Vec3 chest = feet.add(0, height * 0.6, 0);
        spiral(level, feet, 0.55, height + 0.2, LIFE, 0.8F, 18, 0);
        spiral(level, feet.add(0, 0.05, 0), 0.5, height + 0.1, BRIGHT, 0.7F, 18, 2);
        for (int beat = 0; beat < 2; beat++) loop(level, chest, UP, 0.7 + beat * 0.25, 0.04F, BLOOD, LIFE, 0.8F, 6, beat * 8);
        LightningRenderer.star(chest, 0.9F, LIFE, 0.8F, 10, 0);
        for (int i = 0; i < (detail ? 12 : 4); i++) vanilla(level, GOLD, chest.add(jitter(level, 0.5)), new Vec3(0, 0.05, 0));
    }

    /** A limb taken: a spray of blood out of the stump and a white glint of bone. */
    private static void sever(ClientLevel level, Vec3 stump, Vec3 outward, boolean detail) {
        Vec3 out = outward.subtract(stump);
        out = out.lengthSqr() < 1.0E-4 ? UP : out.normalize();
        LightningRenderer.star(stump, 0.45F, 0xFFFFFF, 0.9F, 3, 0);
        for (int i = 0; i < (detail ? 26 : 10); i++)
            vanilla(level, i % 3 == 0 ? BRIGHT_DROP : DROP, stump, out.scale(0.18 + level.random.nextDouble() * 0.15).add(jitter(level, 0.12)).add(0, 0.06, 0));
        for (int i = 0; i < (detail ? 4 : 2); i++) vanilla(level, ParticleTypes.DAMAGE_INDICATOR, stump, jitter(level, 0.1));
    }

    /** Coming back from death: a crimson flash, blood rushing in, a heartbeat thrown across the ground. */
    private static void revive(ClientLevel level, Vec3 feet, float size, boolean detail) {
        Vec3 chest = feet.add(0, 1.0 * Math.max(0.6, size * 0.6), 0);
        LightningRenderer.star(chest, 1.4F, BRIGHT, 1F, 8, 0);
        LightningRenderer.star(chest, 2.2F, BLOOD, 0.6F, 10, 0);
        for (int beat = 0; beat < 3; beat++) loop(level, feet.add(0, 0.1, 0), UP, 1.2 + beat * 1.4, 0.06F, BLOOD, BRIGHT, 0.8F - beat * 0.2F, 8, beat * 6);
        for (int i = 0; i < (detail ? 40 : 14); i++) {
            Vec3 from = onShell(level, chest, 2 + level.random.nextDouble());
            vanilla(level, i % 3 == 0 ? BRIGHT_DROP : DROP, from, chest.subtract(from).scale(0.1));
        }
        spiral(level, feet, 0.6, 2.2, LIFE, 0.8F, 16, 4);
    }

    // ------------------------------------------------------------------

    /** A strand winding up a body from its feet. */
    private static void spiral(ClientLevel level, Vec3 feet, double radius, double height, int color, float alpha, int life, int delay) {
        List<Vec3> points = new ArrayList<>();
        double phase = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i <= 24; i++) {
            double t = i / 24.0, angle = phase + t * Math.PI * 4;
            points.add(feet.add(Math.cos(angle) * radius * (1 - t * 0.3), t * height, Math.sin(angle) * radius * (1 - t * 0.3)));
        }
        LightningRenderer.stroke(points, 0.06F, 0.02F, color, alpha * 0.5F, life + 1, delay);
        LightningRenderer.stroke(points, 0.02F, 0.008F, color, alpha, life, delay, false);
    }

    private static void loop(ClientLevel level, Vec3 center, Vec3 axis, double radius, float width, int glow, int color,
                             float alpha, int life, int delay) {
        Vec3 normal = axis.normalize(), right = normal.cross(Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 up = right.cross(normal).normalize();
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            points.add(center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius)));
        }
        LightningRenderer.stroke(points, width * 3, width * 3, glow, alpha * 0.4F, life + 1, delay);
        LightningRenderer.stroke(points, width, width, color, alpha, life, delay);
    }

    private static Vec3 onShell(ClientLevel level, Vec3 center, double radius) {
        return center.add(jitter(level, 1).normalize().scale(radius));
    }

    private static Vec3 jitter(ClientLevel level, double scale) {
        return new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(scale * 2);
    }

    private static void vanilla(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.addParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }
}
