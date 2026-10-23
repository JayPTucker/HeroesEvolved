package com.jayptucker.heroesevolved.flight;

public final class FlightSpeedControl {
    private FlightSpeedControl() {}
    public static int maximumStep(int level) { return Math.clamp(level - 1, 0, 4); }
    public static int adjust(int current, int direction, int level) {
        return Math.clamp(Math.clamp(current, 0, maximumStep(level)) + Integer.signum(direction), 0, maximumStep(level));
    }
    public static double speed(int step, double configuredMaximum) {
        return step <= 0 ? 0 : configuredMaximum * (.2D + .2D * Math.clamp(step, 1, 4));
    }
}
