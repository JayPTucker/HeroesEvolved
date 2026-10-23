package com.jayptucker.heroesevolved.network;

import java.util.HashSet;
import java.util.Set;

public final class ClientFlightVisualState {
    private static final Set<Integer> ACTIVE_FLIGHT_ENTITIES = new HashSet<>();
    private static final Set<Integer> ACTIVE_CYCLONE_ENTITIES = new HashSet<>();

    private static final Set<Integer> ENABLED = new HashSet<>();
    private static final java.util.Map<Integer, Integer> SPEEDS = new java.util.HashMap<>();
    public static boolean isFlightEnabled(int id) { return ENABLED.contains(id); }
    public static int speedStep(int id) { return SPEEDS.getOrDefault(id, 0); }

    private ClientFlightVisualState() {
    }

    public static void clear() {
        ENABLED.clear(); SPEEDS.clear();
        ACTIVE_FLIGHT_ENTITIES.clear();
        ACTIVE_CYCLONE_ENTITIES.clear();
    }

    public static boolean isFlightActive(int entityId) {
        return ACTIVE_FLIGHT_ENTITIES.contains(entityId);
    }

    public static boolean isCycloneActive(int entityId) {
        return ACTIVE_CYCLONE_ENTITIES.contains(entityId);
    }

    public static void update(
            int entityId,
            boolean flightActive,
            boolean cycloneActive,
            boolean flightEnabled,
            int speedStep
    ) {
        if (flightEnabled) { ENABLED.add(entityId); SPEEDS.put(entityId, Math.clamp(speedStep, 0, 4)); }
        else { ENABLED.remove(entityId); SPEEDS.remove(entityId); }
        if (flightActive) {
            ACTIVE_FLIGHT_ENTITIES.add(entityId);
        } else {
            ACTIVE_FLIGHT_ENTITIES.remove(entityId);
        }

        if (cycloneActive) {
            ACTIVE_CYCLONE_ENTITIES.add(entityId);
        } else {
            ACTIVE_CYCLONE_ENTITIES.remove(entityId);
        }
    }
}
