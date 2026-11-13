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

/** The planes, timings and hand anchors shared by gameplay and the breach renderer. */
public final class VibeGeometry {
    public static final double PORTAL_HEIGHT = 1.27, PORTAL_WIDTH = .96;
    public static final float GUARD_WIDTH = 1.28F, HORIZON_WIDTH = 2.05F;
    public static final int BREACH_OPEN_TICKS = 8, LOCK_WARNING_TICKS = 16;
    public static final Vec3 UP = new Vec3(0, 1, 0);

    private VibeGeometry() {}

    public static Vec3 side(Vec3 direction) {
        Vec3 right = direction.cross(UP);
        return right.lengthSqr() < 1e-5 ? new Vec3(-1, 0, 0) : right.normalize();
    }

    public static Vec3 hand(LivingEntity entity, Vec3 direction, float partial, boolean offHand) {
        double yaw = Math.toRadians(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot));
        double sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (offHand) sign = -sign;
        Vec3 shoulder = entity.getPosition(partial).add(-Math.cos(yaw) * .3125 * sign,
                entity.isCrouching() ? 1.17 : 1.375, -Math.sin(yaw) * .3125 * sign);
        return shoulder.add(direction.scale(.69));
    }

    public static double waveTravel(double distance) { return Mth.clamp(distance / 3.2, 2.5, 8); }

    public static Vec3 clip(ServerPlayer player, Vec3 from, Vec3 to) {
        var hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** Segment crossing against the same ellipse the viewer sees. Coordinates refer to the entity's centre. */
    public static Vec3 crossing(Vec3 from, Vec3 to, Vec3 center, Vec3 normal, double width, double height) {
        double start = from.subtract(center).dot(normal), end = to.subtract(center).dot(normal);
        if (start == end || start * end > 0) return null;
        Vec3 point = from.lerp(to, start / (start - end));
        Vec3 offset = point.subtract(center);
        double x = offset.dot(side(normal)) / width, y = offset.y / height;
        return x * x + y * y <= 1 ? point : null;
    }

    static void privatePiece(ServerPlayer player, Piece piece, Vec3 at, Vec3 to, float scale, int entity, int delay) {
        PacketDistributor.sendToPlayer(player, new SetPiecePayload(piece.ordinal(), at, to, scale,
                entity, player.getRandom().nextInt(), delay));
    }
}
