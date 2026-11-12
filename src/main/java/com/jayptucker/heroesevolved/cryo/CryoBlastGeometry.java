package com.jayptucker.heroesevolved.cryo;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Aim and palm anchors shared by the ice blast and its renderer. */
public final class CryoBlastGeometry {
    public static final double REACH = 12;
    public static final double ARM_REACH = .68;
    static final double AIM_PADDING = .24;

    private CryoBlastGeometry() {}

    public static Vec3 palm(Player player, float partial, int side) {
        return shoulder(player, partial, side).add(player.getViewVector(partial).scale(ARM_REACH));
    }

    public static Vec3 shoulder(Player player, float partial, int side) {
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        return player.getPosition(partial)
                .add(-Math.cos(yaw) * side * .3125, player.isCrouching() ? 1.17 : 1.375,
                        -Math.sin(yaw) * side * .3125);
    }
}
