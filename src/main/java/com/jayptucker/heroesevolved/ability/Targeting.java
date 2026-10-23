package com.jayptucker.heroesevolved.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Finds what a player is aiming at for targeted powers. */
public final class Targeting {
    private Targeting() {
    }

    /** The living entity under the crosshair, stopping at walls. */
    public static LivingEntity livingEntityInSight(
            ServerPlayer player,
            double range,
            Predicate<LivingEntity> filter
    ) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        HitResult blockHit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player
        ));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        AABB searchBox = player.getBoundingBox()
                .expandTowards(end.subtract(eye))
                .inflate(1.0D);
        Predicate<Entity> entityFilter = entity -> entity != player
                && entity instanceof LivingEntity living
                && living.isAlive()
                && !living.isSpectator()
                && filter.test(living);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox, entityFilter,
                eye.distanceToSqr(end)
        );
        return entityHit == null ? null : (LivingEntity) entityHit.getEntity();
    }
}
