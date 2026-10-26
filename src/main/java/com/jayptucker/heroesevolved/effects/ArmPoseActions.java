package com.jayptucker.heroesevolved.effects;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * How the arms move for each action, applied after it succeeds. Quake,
 * Dive Bomb and held abilities pose themselves, because their poses follow
 * what the power is doing (a plunge, a held beam) rather than the press.
 */
public final class ArmPoseActions {
    private record Pose(ArmPose pose, int ticks) {
    }

    private static final Map<String, Pose> POSES = Map.ofEntries(
            Map.entry("sonic_cry", new Pose(ArmPose.ARMS_WIDE, 14)),
            Map.entry("sonic_burst", new Pose(ArmPose.ARMS_WIDE, 16)),
            Map.entry("sonic_echo_sense", new Pose(ArmPose.TEMPLE, 16)),
            Map.entry("sonic_vault", new Pose(ArmPose.TWO_HANDS, 12)),
            Map.entry("sonic_scream", new Pose(ArmPose.ARMS_WIDE, 100)),
            Map.entry("flight_launch", new Pose(ArmPose.ARMS_RAISED, 25)),
            Map.entry("flight_cyclone", new Pose(ArmPose.ARMS_WIDE, 100)),

            Map.entry("time_blink", new Pose(ArmPose.ONE_HAND, 6)),
            Map.entry("time_slow", new Pose(ArmPose.TEMPLE, 20)),
            Map.entry("time_stop", new Pose(ArmPose.TEMPLE, 30)),
            Map.entry("time_rewind", new Pose(ArmPose.TEMPLE, 20)),
            Map.entry("temporal_snapshot", new Pose(ArmPose.TEMPLE, 25)),

            Map.entry("regeneration_blood_gift", new Pose(ArmPose.ONE_HAND, 15)),
            Map.entry("regeneration_healing_aura", new Pose(ArmPose.ARMS_WIDE, 25)),
            Map.entry("regeneration_indestructible", new Pose(ArmPose.ARMS_WIDE, 10)),

            Map.entry("electro_chain_lightning", new Pose(ArmPose.ONE_HAND, 8)),
            Map.entry("electro_ball_lightning", new Pose(ArmPose.TWO_HANDS, 10)),
            Map.entry("electro_thunderstrike", new Pose(ArmPose.ARMS_RAISED, 15)),
            Map.entry("electro_storm_surge", new Pose(ArmPose.ARMS_WIDE, 20)),

            Map.entry("radiation_radiant_touch", new Pose(ArmPose.ONE_HAND, 8)),
            Map.entry("radiation_pulse", new Pose(ArmPose.ARMS_WIDE, 15)),
            Map.entry("radiation_emp", new Pose(ArmPose.ARMS_WIDE, 16)),
            Map.entry("radiation_meltdown_beam", new Pose(ArmPose.TWO_HANDS_STRAIN, 15)),

            Map.entry("cryo_frost_bolt", new Pose(ArmPose.ONE_HAND, 8)),
            Map.entry("cryo_flash_freeze", new Pose(ArmPose.ONE_HAND, 12)),
            Map.entry("cryo_ice_wall", new Pose(ArmPose.ARMS_RAISED, 12)),
            Map.entry("cryo_glacial_spikes", new Pose(ArmPose.GROUND, 14)),
            Map.entry("cryo_blizzard", new Pose(ArmPose.ARMS_RAISED, 30)),

            Map.entry("telepathy_read_mind", new Pose(ArmPose.TEMPLE, 20)),
            Map.entry("telepathy_suggestion", new Pose(ArmPose.TEMPLE, 20)),
            Map.entry("telepathy_mind_link", new Pose(ArmPose.TEMPLE, 30)),
            Map.entry("telepathy_puppet", new Pose(ArmPose.ONE_HAND, 20)),
            Map.entry("telepathy_nightmare", new Pose(ArmPose.ONE_HAND, 25)),

            Map.entry("telekinesis_push", new Pose(ArmPose.ONE_HAND, 8)),
            Map.entry("telekinesis_grip", new Pose(ArmPose.ONE_HAND, 15)),
            Map.entry("telekinesis_bullet_stop", new Pose(ArmPose.TWO_HANDS, 20)),
            Map.entry("telekinesis_disarm", new Pose(ArmPose.ONE_HAND, 10)),
            Map.entry("telekinesis_storm", new Pose(ArmPose.ARMS_RAISED, 60))
    );

    private ArmPoseActions() {
    }

    public static void onAction(ServerPlayer player, String actionPath) {
        Pose pose = POSES.get(actionPath);
        if (pose != null) {
            ArmPosePayload.show(player, pose.pose(), pose.ticks());
        }
    }
}
