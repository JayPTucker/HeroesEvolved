package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The numbers Density's earthbending tree and {@code DensityArt} share: reaches, timings, the flags packed into a piece's scale,
 * and the hand effects start from.
 */
public final class DensityGeometry {
    /** Pile Driver: how long a punch in Horse Stance roots an enemy, and how often the same enemy can be driven down. */
    public static final int PILE_TICKS = 30, PILE_COOLDOWN = 160;
    /** Stone Skin: how long it lasts, the weight it can bank, and Rebound's reach. */
    public static final int HARDEN_TICKS = 120;
    public static final float HARDEN_CAP = 40;
    public static final double REBOUND_RADIUS = 3;
    /** Pile Driver: how long a shockwave roots the enemies it hits. */
    public static final int CRATER_TICKS = 40;
    /** Quicksand: reach, radius, the warning before the ground goes soft, and how long it stays soft. */
    public static final double SINKHOLE_RANGE = 16, SINKHOLE_RADIUS = 2.5;
    public static final int SINKHOLE_WARN = 10, SINKHOLE_TICKS = 60, SINKHOLE_HOLD = 40;
    /** Set in Stone: how much longer each blow in stance holds an embedded enemy, up to a limit. */
    public static final int SET_STEP = 20, SET_MAX = 80;
    /** Entombed: how long the ground closes over them. */
    public static final int ENTOMB_TICKS = 60;
    /** Uproot: how long a body you surface under can't heal. */
    public static final int UPROOT_SEAL = 40;
    /** Grasp from Below: reach round you while tunnelling, and how long the hold lasts. */
    public static final double GRASP_REACH = 6;
    public static final int GRASP_TICKS = 40;

    private DensityGeometry() {}

    /** The main hand along {@code direction}, from the shoulder the body (not the head) turns with. */
    public static Vec3 hand(LivingEntity entity, Vec3 direction, float partial) {
        double yaw = Math.toRadians(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot));
        double sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 shoulder = entity.getPosition(partial).add(-Math.cos(yaw) * .3125 * sign,
                entity.isCrouching() ? 1.17 : 1.375, -Math.sin(yaw) * .3125 * sign);
        return shoulder.add(direction.scale(.72));
    }

    /** The yaw that looks from {@code from} toward {@code at}. */
    public static float yaw(Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    public static void show(ServerLevel level, Piece piece, Vec3 at, Vec3 to, float scale, Entity bound) {
        SetPiecePayload.send(level, piece, at, to, scale, bound);
    }

    /** A piece only one player sees (Wait and Listen). */
    public static void showTo(ServerPlayer viewer, Piece piece, Vec3 at, Vec3 to, float scale, Entity bound) {
        PacketDistributor.sendToPlayer(viewer, new SetPiecePayload(piece.ordinal(), at, to, scale,
                bound == null ? -1 : bound.getId(), viewer.getRandom().nextInt(), 0));
    }
}
