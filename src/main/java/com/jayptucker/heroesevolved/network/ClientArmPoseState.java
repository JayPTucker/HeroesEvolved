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
        long now = Util.getMillis();
        Entry previous = POSES.get(entityId);
        boolean continuous = pose == ArmPose.KINETIC_GATHER || pose == ArmPose.KINETIC_HOLD;
        long began = continuous && previous != null && previous.pose == pose && previous.endMillis > now
                ? previous.startMillis : now;
        POSES.put(entityId, new Entry(pose, began, now + durationTicks * 50L));
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

    /** Gesture phase survives hold refreshes, so hands do not snap back every server heartbeat. */
    public static float ageTicks(int entityId) {
        Entry entry = POSES.get(entityId);
        return entry == null ? 0 : Math.max(0, (Util.getMillis() - entry.startMillis) / 50F);
    }

    private record Entry(ArmPose pose, long startMillis, long endMillis) {
    }
}
