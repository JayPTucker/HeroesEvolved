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

    // ------------------------------------------------------------------
    // Lightning style: strikes from the fists, a clap, a slam, and dodges as a bolt
    // ------------------------------------------------------------------

    /** A spark's reach, and how far off the aim line it still finds a body (lightning seeks the nearest conductor). */
    public static final double SPARK_REACH = 12, SPARK_SEEK = 1.3;
    /** Thunderclap's cone (reach, and half its angle in degrees) and Grounding Strike's ring. */
    public static final double CLAP_REACH = 6, CLAP_HALF_ANGLE = 40, GROUND_RADIUS = 4.5;
    /** A spark's leader reaches its end in SPARK_FLIGHT ticks; the clap's fan opens over CLAP_OPEN. */
    public static final float SPARK_FLIGHT = 2, CLAP_OPEN = 4;
    /** An Arc Step and a Flashback each push this long; the static a Flashback leaves bursts FLASHBACK_FUSE ticks later. */
    public static final int STEP_TICKS = 3, FLASHBACK_FUSE = 12;
    /** How far the static a Flashback leaves behind reaches when it bursts. */
    public static final double FLASHBACK_RADIUS = 2.5;
    /** Scale added to a spark that Ground Out loaded, so it draws as a chain. */
    public static final float CHAINED = 4;

    /** A fist reaching along the look: {@code main} for the main arm (the rear, cross hand), otherwise the lead. */
    public static Vec3 fist(Player player, float partial, boolean main) {
        double side = (player.getMainArm() == HumanoidArm.RIGHT) == main ? -1 : 1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                        player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125)
                .add(player.getViewVector(partial).scale(.66));
    }

    /** Where the palms meet for a Thunderclap: a forearm in front of the chest. */
    public static Vec3 clap(Player player, float partial, Vec3 forward) {
        return player.getPosition(partial).add(0, player.isCrouching() ? 1.0 : 1.22, 0).add(forward.scale(.55));
    }

    /** The horizontal aim, falling back to the body's facing when looking straight up or down. */
    public static Vec3 level(Vec3 look, float yaw) {
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() > 1e-4) return flat.normalize();
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
    }

    /** True if {@code point} lies in the clap's horizontal cone of {@code reach} and {@code halfAngle} round {@code forward}. */
    public static boolean inCone(Vec3 origin, Vec3 forward, double reach, double halfAngle, Vec3 point) {
        Vec3 delta = point.subtract(origin).multiply(1, 0, 1);
        double distance = delta.length();
        if (distance > reach) return false;
        return distance < .7 || delta.scale(1 / distance).dot(forward) >= Math.cos(Math.toRadians(halfAngle));
    }

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound) {
        show(level, piece, from, to, size, bound, 0);
    }

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound, int delay) {
        SetPiecePayload.send(level, piece, from, to, (float) size, bound, delay);
    }
}
