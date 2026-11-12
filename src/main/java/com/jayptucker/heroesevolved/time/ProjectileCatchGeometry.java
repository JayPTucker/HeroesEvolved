package com.jayptucker.heroesevolved.time;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The same crosshair, reach and cover checks run on both sides of a catch. */
public final class ProjectileCatchGeometry {
    public static final double REACH = 7;

    private ProjectileCatchGeometry() {}

    public static double hitDistanceSquared(Player player, Projectile shot) {
        if (player.level() != shot.level() || shot.isRemoved() || shot.getOwner() == player) return Double.POSITIVE_INFINITY;
        Vec3 eye = player.getEyePosition(), center = shot.getBoundingBox().getCenter(), look = player.getLookAngle();
        Vec3 offset = center.subtract(eye);
        if (offset.lengthSqr() > REACH * REACH || offset.dot(look) < 0) return Double.POSITIVE_INFINITY;
        var box = shot.getBoundingBox().inflate(.55);
        Vec3 hit = box.contains(eye) ? eye : box.clip(eye, eye.add(look.scale(REACH))).orElse(null);
        if (hit == null || player.level().clip(new ClipContext(eye, center,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS)
            return Double.POSITIVE_INFINITY;
        return hit.distanceToSqr(eye);
    }
}
