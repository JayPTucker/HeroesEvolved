package com.jayptucker.heroesevolved.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared by the rendered flight pose and its server-side particle anchors. */
public final class FlightVisualGeometry {
    public static final double PIVOT_HEIGHT = 0.9D;
    private static final double FOOT_HEIGHT = 0.05D;

    private FlightVisualGeometry() {
    }

    public static float yaw(Player player, boolean cycloneActive, float partialTick) {
        Vec3 velocity = player.getDeltaMovement();
        if (cycloneActive && velocity.horizontalDistanceSqr() >= 0.01D) {
            return (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        }
        return Mth.rotLerp(partialTick, player.yRotO, player.getYRot());
    }

    public static float pitch(Player player, float partialTick) {
        return Mth.lerp(partialTick, player.xRotO, player.getXRot());
    }

    public static Vec3 direction(Player player, boolean cycloneActive, float partialTick) {
        return Vec3.directionFromRotation(pitch(player, partialTick),
                yaw(player, cycloneActive, partialTick));
    }

    public static Vec3 bodyCenter(Vec3 position) {
        return position.add(0.0D, PIVOT_HEIGHT, 0.0D);
    }

    public static Vec3 feet(Vec3 position, Vec3 bodyDirection, boolean posed) {
        if (!posed) {
            return position.add(0.0D, FOOT_HEIGHT, 0.0D);
        }
        // The pose rotates the upright centerline into the full 3D look axis.
        // At level flight the feet are behind the torso at its height; on a
        // climb/dive they move below/above it with the body, not the hitbox.
        return bodyCenter(position).add(bodyDirection.normalize()
                .scale(FOOT_HEIGHT - PIVOT_HEIGHT));
    }
}
