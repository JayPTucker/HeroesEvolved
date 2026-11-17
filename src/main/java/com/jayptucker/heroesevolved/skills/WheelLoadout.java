package com.jayptucker.heroesevolved.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Saved wheel positions, clockwise from the top. An explicitly cleared position stays empty. */
public final class WheelLoadout {
    public static final int SLOTS = 8;
    public static final int TAP = 0;
    private static final String QUICK_CAST = "tap";
    private static final String INITIALIZED = "initialized";

    private WheelLoadout() {}

    private static String position(int slot) {
        return "position_" + slot;
    }

    public static List<String> positions(Map<String, String> slots) {
        List<String> positions = new ArrayList<>(SLOTS);
        for (int slot = 1; slot <= SLOTS; slot++) positions.add(slots.getOrDefault(position(slot), ""));
        return List.copyOf(positions);
    }

    public static String quickCast(Map<String, String> slots) {
        return slots.getOrDefault(QUICK_CAST, "");
    }

    public static boolean initialized(Map<String, String> slots) {
        return slots.containsKey(INITIALIZED);
    }

    /** Initializes saves from before the wheel had favorites. */
    public static Map<String, String> seed(Map<String, String> slots, List<String> candidates) {
        if (initialized(slots)) return slots;
        var updated = new HashMap<>(addDefaults(slots, candidates));
        updated.put(INITIALIZED, "");
        return updated;
    }

    /** One newly learned action can fill a position the player has never used or cleared. */
    public static Map<String, String> addLearned(Map<String, String> slots, String actionKey) {
        return addDefaults(slots, List.of(actionKey));
    }

    private static Map<String, String> addDefaults(Map<String, String> slots, List<String> candidates) {
        var updated = new HashMap<>(slots);
        var remaining = new ArrayList<>(candidates);
        remaining.removeAll(positions(slots));
        for (int slot = 1; slot <= SLOTS && !remaining.isEmpty(); slot++) {
            String position = position(slot);
            if (!updated.containsKey(position)) updated.put(position, remaining.removeFirst());
        }
        return updated;
    }

    public static Map<String, String> place(Map<String, String> slots, int slot, String actionKey) {
        var updated = new HashMap<>(slots);
        if (slot == TAP) {
            updated.put(QUICK_CAST, actionKey);
            return updated;
        }
        String replaced = updated.put(position(slot), actionKey);
        if (!actionKey.isEmpty()) {
            // Moving a favorite onto an occupied position swaps the two.
            for (int other = 1; other <= SLOTS; other++) {
                String otherPosition = position(other);
                if (other != slot && actionKey.equals(updated.get(otherPosition))) {
                    updated.put(otherPosition, replaced == null ? "" : replaced);
                    break;
                }
            }
        }
        if (!positions(updated).contains(quickCast(updated))) updated.remove(QUICK_CAST);
        return updated;
    }
}
