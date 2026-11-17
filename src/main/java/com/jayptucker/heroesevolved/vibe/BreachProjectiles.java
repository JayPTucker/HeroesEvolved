package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.mixin.ArrowAccess;
import com.jayptucker.heroesevolved.mixin.ProjectileAccess;
import com.jayptucker.heroesevolved.time.ProjectileTimeService;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Splits an incoming shot at the first reachable breach, then hands it back to its own projectile logic. */
public final class BreachProjectiles {
    private BreachProjectiles() {}

    public static boolean beforeTick(ServerLevel level, Projectile shot) {
        if (!VibeBreaches.hasGates() && !VibeService.hasGuard()) return false;
        Vec3 from = shot.position(), motion = shot.getDeltaMovement(), end = from.add(motion);
        if (motion.lengthSqr() < 1e-8 || shot instanceof AbstractArrow && ((ArrowAccess) shot).heroes$inGround()) return false;
        var crossing = VibeBreaches.shotCrossing(shot, from, end);
        boolean bullet = crossing != null && PowerWeapons.isBullet(shot);
        if (crossing != null && blockedBefore(level, shot, from, crossing.hit(), bullet)) crossing = null;

        // A guard on this side can intercept the incoming segment. A projectile emerging on the other side is
        // checked by that side's normal defenses and hit logic on its next tick.
        if (VibeService.hasGuard() && VibeService.intercept(level, shot, from, crossing == null ? end : crossing.hit())) return true;
        if (crossing == null) return false;

        var exit = crossing.to();
        Vec3 velocity = crossing.rotate(motion);
        double side = Math.copySign(.025, velocity.dot(exit.direction()));
        Vec3 out = crossing.map(crossing.hit()).add(exit.direction().scale(side));
        BlockPos cell = BlockPos.containing(out);
        if (!exit.level().hasChunkAt(cell) || !exit.level().getWorldBorder().isWithinBounds(cell)) return false;
        // Projectile rotations use atan2(+x, z), the opposite of player yaw.
        Vec3 heading = crossing.rotate(Vec3.directionFromRotation(-shot.getXRot(), -shot.getYRot()));
        float yaw = -VibeGeometry.yaw(heading), pitch = -VibeGeometry.pitch(heading);
        var transition = new DimensionTransition(exit.level(), out, velocity, yaw, pitch, DimensionTransition.DO_NOTHING);
        Entity owner = shot.getOwner();
        Projectile moved;
        if (bullet) {
            moved = PowerWeapons.breachBullet(shot, transition, crossing::map);
        } else if (level == exit.level()) {
            shot.absMoveTo(out.x, out.y, out.z, yaw, pitch);
            shot.setDeltaMovement(velocity);
            moved = shot;
        } else if (shot.canChangeDimensions(level, exit.level()) && shot.changeDimension(transition) instanceof Projectile next) {
            next.setOwner(owner);
            next.tickCount = shot.tickCount;
            moved = next;
        } else {
            return false;
        }
        if (moved == null) return false;
        ProjectileTimeService.throughBreach(shot, moved, crossing::rotate);
        // The incoming step ends at the seam. Continuing on the next tick preserves the full speed used by
        // vanilla arrow damage and lets TaCZ trace every hit from the exit, including piercing and explosive ammo.
        return true;
    }

    private static boolean blockedBefore(ServerLevel level, Projectile shot, Vec3 from, Vec3 hit, boolean bullet) {
        if (bullet) return PowerWeapons.bulletObstructed(shot, from, hit);
        if (level.clip(new ClipContext(from, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot)).getType()
                != HitResult.Type.MISS) return true;
        return ProjectileUtil.getEntityHitResult(level, shot, from, hit,
                shot.getBoundingBox().expandTowards(hit.subtract(from)).inflate(1), ((ProjectileAccess) shot)::heroes$canHitEntity) != null;
    }
}
