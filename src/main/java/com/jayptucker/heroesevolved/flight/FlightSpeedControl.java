package com.jayptucker.heroesevolved.flight;

public final class FlightSpeedControl {
    private FlightSpeedControl() {}
    public static int maximumStep(int level) { return Math.clamp(level - 1, 0, 4); }
    public static int adjust(int current, int direction, int level) {
        return Math.clamp(Math.clamp(current, 0, maximumStep(level)) + Integer.signum(direction), 0, maximumStep(level));
    }
    /**
     * Forward speed at a wheel step: every step half again as fast as it used to be, so the top step is one and a
     * half times the configured cruise speed (3 blocks a tick at the default 2).
     */
    public static double speed(int step, double configuredMaximum) {
        return step <= 0 ? 0 : configuredMaximum * (.3D + .3D * Math.clamp(step, 1, 4));
    }
}
