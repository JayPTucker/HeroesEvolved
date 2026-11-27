package com.jayptucker.heroesevolved.speed;

import net.minecraft.world.phys.Vec3;

/**
 * The speedster's real orbit, shared by movement and its drawing. How fast the runner laps the ring follows the
 * Cyclone's speed setting (the scroll wheel): a slow vacuum is a lap a second, the top setting nearly three. A change
 * of setting never jumps: the runner speeds up or slows down at {@link #ACCEL} from wherever they are, and the server
 * sends the new {@link Laps} so every client runs the same schedule the server moves the body along.
 */
public final class CycloneGeometry {
    /** Radians a tick at the old fixed pace (setting 3), and ticks the ring takes to open out from the eye. */
    public static final float LAP_SPEED = .42F, SPIN_UP = 6;
    /** How quickly the lap speed changes, radians a tick per tick: from a standstill to the old pace in six ticks. */
    public static final float ACCEL = .07F;
    /** Radians a tick round the ring at speed settings 1 to 6 (beyond 6, the last). */
    private static final float[] LAPS = {.26F, .32F, LAP_SPEED, .56F, .72F, .9F};

    private CycloneGeometry() {}

    /** Radians a tick round the ring at this speed setting. */
    public static float lapSpeed(int setting) { return LAPS[Math.clamp(setting, 1, LAPS.length) - 1]; }

    /** How fast this lap speed is, 0 at the slowest setting to 1 at the fastest, for the drawing to scale with. */
    public static float pace(float omega) {
        return Math.clamp((omega - LAPS[0]) / (LAPS[LAPS.length - 1] - LAPS[0]), 0, 1);
    }

    /**
     * One stretch of the schedule: from {@code from} (ticks since the Cyclone began) the runner is at {@code angle},
     * lapping at {@code omega} radians a tick and changing at {@link #ACCEL} toward {@code target}.
     */
    public record Laps(float from, float angle, float omega, float target) {
        /** A Cyclone setting off from a standstill at {@code phase}. */
        public static Laps start(float phase, int setting) { return new Laps(0, phase, 0, lapSpeed(setting)); }

        private float ramp() { return Math.max(1, Math.abs(target - omega) / ACCEL); }

        /** The runner's angle round the ring at this age. */
        public float angle(float age) {
            float dt = Math.max(0, age - from), ramp = ramp();
            if (dt < ramp) return angle + omega * dt + (target - omega) * dt * dt / (2 * ramp);
            return angle + (omega + target) * ramp / 2 + target * (dt - ramp);
        }

        /** Radians a tick at this age. */
        public float omega(float age) {
            float dt = Math.max(0, age - from), ramp = ramp();
            return dt < ramp ? omega + (target - omega) * dt / ramp : target;
        }

        /** The same run from {@code age} on, heading for a new lap speed. */
        public Laps toward(float age, float speed) { return new Laps(age, angle(age), omega(age), speed); }
    }

    /** Where the runner is at this age: opening out from the eye over the first ticks, then on the ring. */
    public static Vec3 runner(Vec3 center, double radius, Laps laps, float age) {
        double ramp = Math.clamp(age / SPIN_UP, 0, 1);
        ramp = ramp * ramp * (3 - 2 * ramp);
        float angle = laps.angle(age);
        return center.add(Math.cos(angle) * radius * ramp, 0, Math.sin(angle) * radius * ramp);
    }
}
