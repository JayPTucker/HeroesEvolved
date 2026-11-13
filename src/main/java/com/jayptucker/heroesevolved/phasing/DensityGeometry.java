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
 * The numbers Density's skills and {@code DensityArt} share: reaches, timings, the flags packed into a piece's scale,
 * and the hand effects start from.
 */
public final class DensityGeometry {
    /** Pass Through: reach, how far behind the body you come out, how long it can't heal. */
    public static final double PASS_RANGE = 10, PASS_BEHIND = 1.2;
    public static final int PASS_SEAL = 60;
    /** Through the Wall: how many blocks of wall a hand finds an enemy behind, and the bonus for the surprise. */
    public static final int WALL_BLOCKS = 2;
    public static final float WALL_BONUS = 3;
    /** Ambush: how long a hand stays primed after you step out of a wall, and the bonus it lands. */
    public static final int PRIMED_TICKS = 60;
    public static final float AMBUSH_BONUS = 4;
    /** Ghost Network: how long a wall you passed stays open for friends; Phase Carry: how long friends stay loose. */
    public static final int DOOR_TICKS = 120, CARRY_GHOST = 60;
    public static final double DOOR_REACH = 1.1;
    /** Pile Driver: how long a Full Dense punch roots an enemy, and how often the same enemy can be driven down. */
    public static final int PILE_TICKS = 30, PILE_COOLDOWN = 160;
    /** Harden: how long it lasts, the weight it can bank, and Rebound's reach. */
    public static final int HARDEN_TICKS = 120;
    public static final float HARDEN_CAP = 40;
    public static final double REBOUND_RADIUS = 3;
    /** Crater Landing: how long a shockwave roots the enemies it hits. */
    public static final int CRATER_TICKS = 40;
    /** Sinkhole: reach, radius, the warning before the ground goes soft, and how long it stays soft. */
    public static final double SINKHOLE_RANGE = 16, SINKHOLE_RADIUS = 2.5;
    public static final int SINKHOLE_WARN = 10, SINKHOLE_TICKS = 60, SINKHOLE_HOLD = 40;
    /** Set in Stone: how much longer each blow at Dense holds an embedded enemy, up to a limit. */
    public static final int SET_STEP = 20, SET_MAX = 80;
    /** Entombed: how long the ground closes over them. */
    public static final int ENTOMB_TICKS = 60;
    /** Uproot: how long a body you surface under can't heal. */
    public static final int UPROOT_SEAL = 40;
    /** Fade Through: how long it lasts, and how long a body you drift through can't heal. */
    public static final int FADE_TICKS = 60, FADE_SEAL = 40;
    /** Grasp from Below: reach round you while sunk, and how long the hold lasts. */
    public static final double GRASP_REACH = 6;
    public static final int GRASP_TICKS = 40;
    /** Untouchable: the health it saves you at, how long the slip lasts, and how often it can. */
    public static final float SLIP_AT = 1 / 3F;
    public static final int SLIP_TICKS = 40, SLIP_COOLDOWN = 600;
    /** Flag added to a piece's scale: a door opened by Phase Carry (brighter, once), an entomb that has broken open. */
    public static final float CARRIED = 10;

    private DensityGeometry() {}

    /** The main hand along {@code direction}, from the shoulder the body (not the head) turns with. */
    public static Vec3 hand(LivingEntity entity, Vec3 direction, float partial) {
        double yaw = Math.toRadians(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot));
        double sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 shoulder = entity.getPosition(partial).add(-Math.cos(yaw) * .3125 * sign,
                entity.isCrouching() ? 1.17 : 1.375, -Math.sin(yaw) * .3125 * sign);
        return shoulder.add(direction.scale(.72));
    }

    /** Where you come out after passing through a body: just behind it, on the line from you through it. */
    public static Vec3 behind(Entity from, LivingEntity body) {
        Vec3 d = body.position().subtract(from.position()).multiply(1, 0, 1);
        d = d.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, from.getYRot()).multiply(1, 0, 1).normalize() : d.normalize();
        return body.position().add(d.scale(body.getBbWidth() * .5 + PASS_BEHIND));
    }

    /** The yaw that looks from {@code from} toward {@code at}. */
    public static float yaw(Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    public static void show(ServerLevel level, Piece piece, Vec3 at, Vec3 to, float scale, Entity bound) {
        SetPiecePayload.send(level, piece, at, to, scale, bound);
    }

    /** A piece only one player sees (a primed hand). */
    public static void showTo(ServerPlayer viewer, Piece piece, Vec3 at, Vec3 to, float scale, Entity bound) {
        PacketDistributor.sendToPlayer(viewer, new SetPiecePayload(piece.ordinal(), at, to, scale,
                bound == null ? -1 : bound.getId(), viewer.getRandom().nextInt(), 0));
    }
}
