package com.jayptucker.heroesevolved.time.intro;

import net.minecraft.world.phys.Vec3;

/**
 * The light at 6:14, as one model both sides share: the server decides from it when time stops for
 * each visitor, and the client draws the fireball and the wall of dust from it, so the wave a player
 * sees is exactly where the server thinks it is.
 */
public final class IntroBlast {
    /** Ground zero: where Ted Sprague stands at the north end of the avenue. */
    public static final Vec3 CENTER = new Vec3(4.5, 64, -77.5);
    /** The shock front's steady speed once it has left the fireball, in blocks a tick. */
    public static final float SPEED = 1.15F;
    /** How far from a visitor the front is when time stops for them. */
    public static final double MARGIN = 20;
    /** Everything held still, then everything running backwards to the moment before the light. */
    public static final int FREEZE_TICKS = 50, REWIND_TICKS = 46;
    /**
     * Once it has all run back, time stays stopped a few seconds more, everyone back where they stood before
     * the sirens, so the visitor can see what they have undone before they go home.
     */
    public static final int HOLD_TICKS = 60;
    /** From time stopping to going home. */
    public static final int STOPPED_TICKS = FREEZE_TICKS + REWIND_TICKS + HOLD_TICKS;
    /** The fireball's full radius. */
    public static final float FIREBALL = 42;

    private IntroBlast() {}

    /** The shock front's radius, in blocks, {@code t} ticks after the flash. */
    public static float front(float t) {
        if (t <= 0) return 0;
        return 24 * (1 - (float) Math.exp(-t / 4)) + SPEED * t;
    }

    /** Ticks after the flash until the front is within {@link #MARGIN} of a point {@code distance} away. */
    public static int arrival(double distance) {
        double target = Math.max(0, distance - MARGIN);
        int t = 0;
        while (front(t) < target && t < 2000) t++;
        return t;
    }

    /** Horizontal distance from ground zero. */
    public static double distance(double x, double z) {
        return Math.hypot(x - CENTER.x, z - CENTER.z);
    }
}
