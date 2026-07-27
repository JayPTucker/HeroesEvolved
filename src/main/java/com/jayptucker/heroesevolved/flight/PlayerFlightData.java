package com.jayptucker.heroesevolved.flight;

public record PlayerFlightData(
        boolean sessionActive,
        boolean flightEnabled,
        boolean grantedMayfly,
        int launchTicksRemaining,
        boolean hasBeenAirborne,
        boolean visualPoseActive
) {
    public static PlayerFlightData empty() {
        return new PlayerFlightData(false, false, false, 0, false, false);
    }

    public PlayerFlightData startLaunch(
            boolean grantedMayfly,
            int launchDelayTicks
    ) {
        return new PlayerFlightData(
                true,
                true,
                grantedMayfly,
                launchDelayTicks,
                false,
                false
        );
    }

    public PlayerFlightData enableFlight(boolean grantedMayfly) {
        if (!sessionActive) {
            throw new IllegalStateException(
                    "Cannot enable flight without an active session."
            );
        }

        return new PlayerFlightData(
                true,
                true,
                grantedMayfly,
                launchTicksRemaining,
                hasBeenAirborne,
                visualPoseActive
        );
    }

    public PlayerFlightData withLaunchTicksRemaining(
            int remainingTicks
    ) {
        return new PlayerFlightData(
                sessionActive,
                flightEnabled,
                grantedMayfly,
                Math.max(0, remainingTicks),
                hasBeenAirborne,
                visualPoseActive
        );
    }

    public PlayerFlightData markAirborne() {
        if (hasBeenAirborne) {
            return this;
        }

        return new PlayerFlightData(
                sessionActive,
                flightEnabled,
                grantedMayfly,
                launchTicksRemaining,
                true,
                visualPoseActive
        );
    }

    public PlayerFlightData withVisualPoseActive(boolean active) {
        if (visualPoseActive == active) {
            return this;
        }

        return new PlayerFlightData(
                sessionActive,
                flightEnabled,
                grantedMayfly,
                launchTicksRemaining,
                hasBeenAirborne,
                active
        );
    }

    public PlayerFlightData disableFlight() {
        return new PlayerFlightData(
                true,
                false,
                false,
                0,
                hasBeenAirborne,
                false
        );
    }
}
