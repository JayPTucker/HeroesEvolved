package com.jayptucker.heroesevolved.time;

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
 * The numbers Space-Time's skills and {@code TimeArt} share: reaches, radii, timings, the flags packed into a piece's
 * scale, and the body anchors effects start from.
 */
public final class TimeGeometry {
    /** Stasis Seal: how far it can be set, its radius (five with Held Volley), how long it lasts, how long it holds a creature. */
    public static final double SEAL_REACH = 24, SEAL_RADIUS = 3, SEAL_WIDE = 5;
    public static final int SEAL_TICKS = 160, SEAL_HOLD = 40, SEAL_SHOTS = 24;
    /** Moment's Edge: reach and targets (Long Edge: 20 and five), ticks between cuts, how long Thousand Cuts holds them after. */
    public static final double EDGE_RANGE = 12, EDGE_LONG_RANGE = 20;
    public static final int EDGE_TARGETS = 3, EDGE_LONG_TARGETS = 5, EDGE_STEP = 2, EDGE_LINGER = 40;
    /** Snap Shut's reach round the hole Fold Space leaves. */
    public static final double SNAP_RADIUS = 3;
    /** Fast Forward: reach, how far it winds a clock (five seconds), Wider Era's spread and its growth radii. */
    public static final double FORWARD_RANGE = 20, ERA_SPREAD = 4;
    public static final int FORWARD_TICKS = 100, GROWTH_RADIUS = 3, GROWTH_WIDE = 5;
    /** Flags added to a piece's scale: a friend's Fast Forward, the growth version, a replayed blow (Era's End). */
    public static final float FRIEND = 32, GROWTH = 16, REPLAY = 10;
    /** Behind You: how far behind its target you land, and how long the strike waits. */
    public static final double BEHIND = 1.3;
    public static final int BEHIND_TICKS = 40;
    /** Foreknowledge: how long you remember them. Second Chance and Shared Hour reach back five seconds. */
    public static final int MARK_TICKS = 200, SECOND_TICKS = 100;
    public static final double SHARED_RADIUS = 6, ANCHOR_CARRY = 3;

    private TimeGeometry() {}

    /** The casting hand along {@code direction}, from the shoulder the body (not the head) turns with. */
    public static Vec3 hand(LivingEntity entity, Vec3 direction, float partial) {
        double yaw = Math.toRadians(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot));
        double sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 shoulder = entity.getPosition(partial).add(-Math.cos(yaw) * .3125 * sign,
                entity.isCrouching() ? 1.17 : 1.375, -Math.sin(yaw) * .3125 * sign);
        return shoulder.add(direction.scale(.72));
    }

    /** A point {@link #BEHIND} blocks behind where a creature faces, at its feet. */
    public static Vec3 behind(LivingEntity target) {
        double yaw = Math.toRadians(target.getYRot());
        return target.position().add(Math.sin(yaw) * BEHIND, 0, -Math.cos(yaw) * BEHIND);
    }

    /** The yaw and pitch that look from {@code from} (feet) at a creature's chest. */
    public static float[] facing(Vec3 from, LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(from.add(0, 1.62, 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new float[] {yaw, pitch};
    }

    public static void show(ServerLevel level, Piece piece, Vec3 at, Vec3 to, float scale, Entity bound) {
        SetPiecePayload.send(level, piece, at, to, scale, bound);
    }

    public static void show(ServerLevel level, Piece piece, Vec3 at, Vec3 to, float scale, Entity bound, int delay) {
        SetPiecePayload.send(level, piece, at, to, scale, bound, delay);
    }

    /** A piece only one player sees (Foreknowledge's marks). */
    public static void showTo(ServerPlayer viewer, Piece piece, Vec3 at, Vec3 to, float scale, Entity bound) {
        PacketDistributor.sendToPlayer(viewer, new SetPiecePayload(piece.ordinal(), at, to, scale,
                bound == null ? -1 : bound.getId(), viewer.getRandom().nextInt(), 0));
    }
}
