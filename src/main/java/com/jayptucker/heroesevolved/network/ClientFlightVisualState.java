package com.jayptucker.heroesevolved.network;

import java.util.HashSet;
import java.util.Set;

public final class ClientFlightVisualState {
    private static final Set<Integer> ACTIVE_FLIGHT_ENTITIES = new HashSet<>();

    private ClientFlightVisualState() {
    }

    public static boolean isFlightActive(int entityId) {
        return ACTIVE_FLIGHT_ENTITIES.contains(entityId);
    }

    public static void update(int entityId, boolean flightActive) {
        if (flightActive) {
            ACTIVE_FLIGHT_ENTITIES.add(entityId);
        } else {
            ACTIVE_FLIGHT_ENTITIES.remove(entityId);
        }
    }
}
