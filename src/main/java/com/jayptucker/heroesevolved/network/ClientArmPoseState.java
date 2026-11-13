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
        if (durationTicks < 0) {
            POSES.computeIfPresent(entityId, (id, entry) -> entry.pose == pose ? null : entry);
            return;
        }
        if (durationTicks == 0) {
            POSES.remove(entityId);
            return;
        }
        long now = Util.getMillis();
        Entry previous = POSES.get(entityId);
        boolean continuous = pose == ArmPose.KINETIC_GATHER || pose == ArmPose.KINETIC_HOLD
                || pose == ArmPose.FIRE_FORGE || pose == ArmPose.FIRE_BREATH || pose == ArmPose.FIRE_SUN
                || pose == ArmPose.CRYO_WEAVE || pose == ArmPose.CRYO_WHITEOUT || pose == ArmPose.CRYO_SURF
                || pose == ArmPose.CRYO_LANCE || pose == ArmPose.CRYO_LOOM || pose == ArmPose.CRYO_SHELTER
                || pose == ArmPose.SONIC_SCREAM || pose == ArmPose.SONIC_GATHER || pose == ArmPose.SONIC_DIVE
                || pose == ArmPose.SONIC_HUM || pose == ArmPose.SONIC_HUSH
                || pose == ArmPose.QUAKE_BEAM || pose == ArmPose.QUAKE_FLY || pose == ArmPose.QUAKE_RUMBLE
                || pose == ArmPose.QUAKE_TUNE || pose == ArmPose.QUAKE_BRACE || pose == ArmPose.QUAKE_SHELL
                || pose == ArmPose.TELE_READ || pose == ArmPose.TELE_WEAVE
                || pose == ArmPose.VIBE_GUARD || pose == ArmPose.REGEN_STREAM || pose == ArmPose.REGEN_EMBRACE || pose == ArmPose.REGEN_BRACE
                || pose == ArmPose.DENSITY_BRACE;
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
