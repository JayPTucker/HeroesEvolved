package com.jayptucker.heroesevolved.speed;

import net.minecraft.world.phys.Vec3;

/** The speedster's real orbit, shared by movement and its drawing. */
public final class CycloneGeometry {
    public static final float LAP_SPEED = .42F, SPIN_UP = 6;
    private CycloneGeometry() {}
    public static float angle(float phase, float age) {
        return phase + (age < SPIN_UP ? LAP_SPEED * age * age / (2 * SPIN_UP)
                : LAP_SPEED * (age - SPIN_UP / 2));
    }
    public static Vec3 runner(Vec3 center, double radius, float phase, float age) {
        double ramp = Math.clamp(age / SPIN_UP, 0, 1);
        ramp = ramp * ramp * (3 - 2 * ramp);
        float angle = angle(phase, age);
        return center.add(Math.cos(angle) * radius * ramp, 0, Math.sin(angle) * radius * ramp);
    }
}
