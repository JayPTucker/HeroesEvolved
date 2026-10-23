package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.ability.ArmPose;
import net.minecraft.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Arm poses per entity on the client, timed in real time so nothing can
 * outlive a world change.
 */
public final class ClientArmPoseState {
    private static final Map<Integer, Entry> POSES = new HashMap<>();

    private ClientArmPoseState() {
    }

    public static void set(int entityId, ArmPose pose, int durationTicks) {
        if (durationTicks <= 0) {
            POSES.remove(entityId);
            return;
        }
        POSES.put(entityId, new Entry(pose, Util.getMillis() + durationTicks * 50L));
    }

    /** The entity's current pose, or null if it has none. */
    public static ArmPose get(int entityId) {
        Entry entry = POSES.get(entityId);
        if (entry == null) {
            return null;
        }
        if (Util.getMillis() >= entry.endMillis) {
            POSES.remove(entityId);
            return null;
        }
        return entry.pose;
    }

    public static void clear() {
        POSES.clear();
    }

    private record Entry(ArmPose pose, long endMillis) {
    }
}
