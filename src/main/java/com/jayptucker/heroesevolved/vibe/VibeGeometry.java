package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/** The planes, timings and hand anchors shared by gameplay and the breach renderer. */
public final class VibeGeometry {
    public static final double PORTAL_HEIGHT = 1.27, PORTAL_WIDTH = .96;
    public static final float GUARD_WIDTH = 1.28F, HORIZON_WIDTH = 2.05F;
    public static final int BREACH_OPEN_TICKS = 8, LOCK_WARNING_TICKS = 16;
    public static final Vec3 UP = new Vec3(0, 1, 0);

    private VibeGeometry() {}

    /**
     * How long a breach from the Breach key takes to tear open while the viber channels it: 30 ticks at tier 1, three
     * fewer each tier (18 at tier 5). The server opens it and the client grows it on the same clock.
     */
    public static int channelTicks(int tier) { return 33 - 3 * Mth.clamp(tier, 1, 5); }

    public static Vec3 side(Vec3 direction) {
        Vec3 right = direction.cross(UP);
        return right.lengthSqr() < 1e-8 ? new Vec3(-1, 0, 0) : right.normalize();
    }

    /** The moving inner edge shared by the iris and its cutout. */
    public static double lipScale(float angle, float time) {
        return 1 + .043 * Math.sin(angle * 7 - time * .18) + .023 * Math.sin(angle * 11 + time * .29);
    }

    public static Vec3 hand(LivingEntity entity, Vec3 direction, float partial, boolean offHand) {
        double yaw = Math.toRadians(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot));
        double sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (offHand) sign = -sign;
        Vec3 shoulder = entity.getPosition(partial).add(-Math.cos(yaw) * .3125 * sign,
                entity.isCrouching() ? 1.17 : 1.375, -Math.sin(yaw) * .3125 * sign);
        return shoulder.add(direction.scale(.69));
    }

    public static double waveTravel(double distance) { return Mth.clamp(distance / 3.2, 2.5, 24); }

    /** A direction's yaw, in Minecraft's convention (0 is +Z, 90 is −X). */
    public static float yaw(Vec3 direction) { return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z)); }

    public static float pitch(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.y, direction.horizontalDistance()));
    }

    /** Rotation from one mouth's local frame to the other, including their pitch. */
    public static Quaternionf rotation(Vec3 from, float fromYaw, Vec3 to, float toYaw) {
        // In Minecraft, yaw increases clockwise when seen from above and pitch increases downward.
        return new Quaternionf().rotationY(-toYaw * Mth.DEG_TO_RAD)
                .rotateX((pitch(to) - pitch(from)) * Mth.DEG_TO_RAD)
                .rotateY(fromYaw * Mth.DEG_TO_RAD);
    }

    public static Vec3 rotate(Vec3 vector, Vec3 from, float fromYaw, Vec3 to, float toYaw) {
        Vector3d turned = rotation(from, fromYaw, to, toYaw).transform(new Vector3d(vector.x, vector.y, vector.z));
        return new Vec3(turned.x, turned.y, turned.z);
    }

    public static Vec3 clip(ServerPlayer player, Vec3 from, Vec3 to) {
        var hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** Segment crossing against the same ellipse the viewer sees. Coordinates refer to the entity's centre. */
    public static Vec3 crossing(Vec3 from, Vec3 to, Vec3 center, Vec3 normal, double width, double height) {
        double start = from.subtract(center).dot(normal), end = to.subtract(center).dot(normal);
        if (start == end || start * end > 0) return null;
        Vec3 point = from.lerp(to, start / (start - end));
        return inside(point.subtract(center), normal, width, height) ? point : null;
    }

    /** Whether an offset falls inside the opening when projected onto its plane. */
    static boolean inside(Vec3 offset, Vec3 normal, double width, double height) {
        Vec3 across = side(normal), up = across.cross(normal).normalize();
        double x = offset.dot(across) / width, y = offset.dot(up) / height;
        return x * x + y * y <= 1;
    }

    static void privatePiece(ServerPlayer player, Piece piece, Vec3 at, Vec3 to, float scale, int entity, int delay) {
        PacketDistributor.sendToPlayer(player, new SetPiecePayload(piece.ordinal(), at, to, scale,
                entity, player.getRandom().nextInt(), delay));
    }
}
