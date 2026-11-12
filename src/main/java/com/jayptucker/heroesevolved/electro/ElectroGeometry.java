package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared sizes and body anchors for electrokinesis. */
public final class ElectroGeometry {
    public static final int STRIKE_DELAY = 16, SURGE_TICKS = 30, SCAR_TICKS = 60, ORB_PRIME = 6;
    public static final double STRIKE_RADIUS = 3.5, RIDE_RADIUS = 1.15, SCAR_RADIUS = .9;

    private ElectroGeometry() {}

    public static Vec3 hand(Player player, float partial, double reach) {
        double side = player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                        player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125)
                .add(player.getViewVector(partial).scale(reach));
    }

    public static Vec3 chest(Vec3 feet) { return feet.add(0, 1.17, 0); }

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound) {
        show(level, piece, from, to, size, bound, 0);
    }

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound, int delay) {
        SetPiecePayload.send(level, piece, from, to, (float) size, bound, delay);
    }
}
