package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared fire shapes and anchors for gameplay and rendering. */
public final class PyroGeometry {
    public static final double BREATH_SLOPE = .51;
    public static final int STORM_WARNING = 12, COLLAPSE_WARNING = 8, SUN_FLIGHT = 9, MINE_WARNING = 8;
    public static final float BLUE_SCALE = 32;

    private PyroGeometry() {}

    public static Vec3 castingHand(Player player, float partial) {
        double side = player.getMainArm() == HumanoidArm.LEFT ? 1 : -1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        Vec3 look = player.getViewVector(partial);
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125).add(look.scale(.64));
    }

    // ------------------------------------------------------------------
    // Firebending: strikes from the fists and feet, and jet dodges
    // ------------------------------------------------------------------

    /** Reach of a jab or cross, the roundhouse's crescent, the flip kick's arc and the sweep kick's ring. */
    public static final double JAB_REACH = 16, KICK_REACH = 7, FLIP_REACH = 5, SWEEP_RADIUS = 4;
    /** Half the roundhouse's crescent and the flip kick's arc, in degrees either side of the aim. */
    public static final double KICK_HALF_ANGLE = 55, FLIP_HALF_ANGLE = 45;
    /** A jab fills this many ticks reaching its end; the kick's crescent opens to full reach over KICK_OPEN. */
    public static final float JAB_FLIGHT = 3, KICK_OPEN = 6;
    /** A sweep kick's ring reaches its edge in SWEEP_OPEN ticks; the body turns once over SWEEP_SPIN. */
    public static final float SWEEP_OPEN = 7, SWEEP_SPIN = 8;
    /** A jet step pushes this long; the backflip turns once over FLIP_SPIN ticks. */
    public static final int STEP_TICKS = 4, FLIP_SPIN = 11;
    /** Scale added to a jab for a Counterflame-loaded fist (as BLUE_SCALE is for blue fire). */
    public static final float BLAZING = 2;

    /** A hand reaching along the look: {@code main} for the main arm, otherwise the other one. */
    public static Vec3 hand(Player player, float partial, boolean main) {
        double side = (player.getMainArm() == HumanoidArm.LEFT) == main ? 1 : -1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        Vec3 look = player.getViewVector(partial);
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125).add(look.scale(.64));
    }

    /** Hip height, where a kicked crescent leaves the body. */
    public static Vec3 hip(Player player, float partial) {
        return player.getPosition(partial).add(0, player.isCrouching() ? .55 : .85, 0);
    }

    /** The horizontal aim, falling back to the body's facing when looking straight up or down. */
    public static Vec3 level(Vec3 look, float yaw) {
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() > 1e-4) return flat.normalize();
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
    }

    /** True if {@code point} lies in a horizontal crescent of {@code reach} and {@code halfAngle} round {@code forward}. */
    public static boolean inCrescent(Vec3 origin, Vec3 forward, double reach, double halfAngle, Vec3 point) {
        Vec3 delta = point.subtract(origin).multiply(1, 0, 1);
        double distance = delta.length();
        if (distance > reach) return false;
        return distance < .8 || delta.scale(1 / distance).dot(forward) >= Math.cos(Math.toRadians(halfAngle));
    }

    public static Vec3 breathOrigin(Player player, float partial) {
        return player.getEyePosition(partial).add(0, -.17, 0).add(player.getViewVector(partial).scale(.34));
    }

    public static void show(ServerLevel level, Piece piece, Vec3 start, Vec3 end, double size, boolean blue, Entity bound) {
        SetPiecePayload.send(level, piece, start, end, (float) size + (blue ? BLUE_SCALE : 0), bound);
    }

    public static void showPrivate(ServerPlayer player, Piece piece, Vec3 at, double size, boolean blue) {
        PacketDistributor.sendToPlayer(player, new SetPiecePayload(piece.ordinal(), at, at,
                (float) size + (blue ? BLUE_SCALE : 0), -1, player.getRandom().nextInt(), 0));
    }
}
