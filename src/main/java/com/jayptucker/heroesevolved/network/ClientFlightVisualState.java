package com.jayptucker.heroesevolved.network;

import java.util.HashSet;
import java.util.Set;

public final class ClientFlightVisualState {
    private static final Set<Integer> ACTIVE_FLIGHT_ENTITIES = new HashSet<>();
    private static final Set<Integer> ACTIVE_CYCLONE_ENTITIES = new HashSet<>();

    private static final Set<Integer> ENABLED = new HashSet<>();
    private static final java.util.Map<Integer, Integer> SPEEDS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Long> ROLL_TIMES = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Long> PHASE_TIMES = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Integer> TELEMETRY = new java.util.HashMap<>();
    private static final int ANIMATED_PHASES = 128 | 256 | FlightVisualStatePayload.DIVE_RELEASED
            | FlightVisualStatePayload.VACUUM;
    public static boolean isFlightEnabled(int id) { return ENABLED.contains(id); }
    public static int speedStep(int id) { return SPEEDS.getOrDefault(id, 0); }
    public static int momentum(int id) { return Math.min(100, TELEMETRY.getOrDefault(id, 0) & 127); }
    public static boolean isCharging(int id) { return (TELEMETRY.getOrDefault(id, 0) & 128) != 0; }
    public static boolean isDiving(int id) { return (TELEMETRY.getOrDefault(id, 0) & 256) != 0; }
    public static boolean isTakingOff(int id) { return hasFlag(id, FlightVisualStatePayload.TAKEOFF); }
    public static boolean isBursting(int id) { return hasFlag(id, FlightVisualStatePayload.BURST); }
    public static boolean isPlunging(int id) { return hasFlag(id, FlightVisualStatePayload.DIVE_RELEASED); }
    public static boolean isGatheringVacuum(int id) { return hasFlag(id, FlightVisualStatePayload.VACUUM); }
    public static boolean hasWideVacuum(int id) { return hasFlag(id, FlightVisualStatePayload.WIDE_VACUUM); }

    private static boolean hasFlag(int id, int flag) { return (TELEMETRY.getOrDefault(id, 0) & flag) != 0; }

    public static float phaseAge(int id, long now, float partial) {
        return Math.max(0, now - PHASE_TIMES.getOrDefault(id, now) + partial);
    }

    public static boolean isPyroFlight(int id) { return (TELEMETRY.getOrDefault(id, 0) & 32768) != 0; }
    public static boolean isPyroBlue(int id) { return (TELEMETRY.getOrDefault(id, 0) & 65536) != 0; }
    public static boolean isPyroBoosting(int id) { return (TELEMETRY.getOrDefault(id, 0) & 131072) != 0; }

    public static boolean isRolling(int id) { return (TELEMETRY.getOrDefault(id, 0) & 512) != 0; }
    public static int rollDirection(int id) { return (TELEMETRY.getOrDefault(id, 0) & 16384) != 0 ? -1 : 1; }
    public static float rollProgress(int id) {
        if (!isRolling(id)) return 0;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var level = minecraft == null ? null : minecraft.level;
        long elapsed = level == null ? 0 : Math.max(0, level.getGameTime() - ROLL_TIMES.getOrDefault(id, level.getGameTime()));
        return Math.clamp((((TELEMETRY.getOrDefault(id, 0) >> 10) & 15) + elapsed) / 12.0F, 0, 1);
    }

    private ClientFlightVisualState() {
    }

    public static void clear() {
        ENABLED.clear(); SPEEDS.clear(); TELEMETRY.clear(); ROLL_TIMES.clear(); PHASE_TIMES.clear();
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
            int speedStep,
            int telemetry
    ) {
        if ((telemetry & 512) != 0) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            var level = minecraft == null ? null : minecraft.level;
            ROLL_TIMES.put(entityId, level == null ? 0L : level.getGameTime());
        } else ROLL_TIMES.remove(entityId);
        int phase = telemetry & ANIMATED_PHASES;
        if (phase == 0) PHASE_TIMES.remove(entityId);
        else if (phase != (TELEMETRY.getOrDefault(entityId, 0) & ANIMATED_PHASES)) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            PHASE_TIMES.put(entityId, level == null ? 0L : level.getGameTime());
        }
        if (telemetry == 0) TELEMETRY.remove(entityId); else TELEMETRY.put(entityId, telemetry);
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
