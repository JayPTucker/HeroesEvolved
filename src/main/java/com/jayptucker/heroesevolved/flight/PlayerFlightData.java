package com.jayptucker.heroesevolved.flight;

public record PlayerFlightData(boolean sessionActive, boolean flightEnabled, boolean grantedMayfly,
        int launchTicksRemaining, boolean hasBeenAirborne, boolean visualPoseActive,
        boolean movingForward, int speedStep) {
    public static PlayerFlightData empty() {
        return new PlayerFlightData(false, false, false, 0, false, false, false, 0);
    }
    public PlayerFlightData startLaunch(boolean grantedMayfly, int launchDelayTicks) {
        return new PlayerFlightData(true, true, grantedMayfly, launchDelayTicks, false, false, false, 0);
    }
    public PlayerFlightData enableFlight(boolean grantedMayfly) {
        if (!sessionActive) throw new IllegalStateException("Cannot enable flight without an active session.");
        return new PlayerFlightData(true, true, grantedMayfly, launchTicksRemaining, hasBeenAirborne,
                visualPoseActive, movingForward, speedStep);
    }
    public PlayerFlightData withLaunchTicksRemaining(int remainingTicks) {
        return new PlayerFlightData(sessionActive, flightEnabled, grantedMayfly, Math.max(0, remainingTicks),
                hasBeenAirborne, visualPoseActive, movingForward, speedStep);
    }
    public PlayerFlightData markAirborne() {
        if (hasBeenAirborne) return this;
        return new PlayerFlightData(sessionActive, flightEnabled, grantedMayfly, launchTicksRemaining,
                true, visualPoseActive, movingForward, speedStep);
    }
    public PlayerFlightData withVisualPoseActive(boolean active) {
        if (visualPoseActive == active) return this;
        return new PlayerFlightData(sessionActive, flightEnabled, grantedMayfly, launchTicksRemaining,
                hasBeenAirborne, active, movingForward, speedStep);
    }
    public PlayerFlightData withForwardInput(boolean forward) {
        if (movingForward == forward) return this;
        return new PlayerFlightData(sessionActive, flightEnabled, grantedMayfly, launchTicksRemaining,
                hasBeenAirborne, visualPoseActive, forward, speedStep);
    }
    public PlayerFlightData withSpeedStep(int step) {
        step = Math.clamp(step, 0, 4);
        if (speedStep == step) return this;
        return new PlayerFlightData(sessionActive, flightEnabled, grantedMayfly, launchTicksRemaining,
                hasBeenAirborne, visualPoseActive, movingForward, step);
    }
    public PlayerFlightData disableFlight() {
        return new PlayerFlightData(true, false, false, 0, hasBeenAirborne, false, false, 0);
    }
}
