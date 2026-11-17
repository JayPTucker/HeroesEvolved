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

    /** The living entity under the crosshair, stopping at walls. Accepts the base power range. */
    public static LivingEntity livingEntityInSight(
            ServerPlayer player,
            double baseRange,
            Predicate<LivingEntity> filter
    ) {
        return livingEntityInRange(player, AbilityRanges.ranged(baseRange), ClipContext.Fluid.NONE, filter);
    }

    /** Uses actual world blocks, sharing the same obstruction/fluid range as the attack's terrain trace. */
    public static LivingEntity livingEntityInRange(ServerPlayer player, double range, ClipContext.Fluid fluid,
                                                   Predicate<LivingEntity> filter) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        HitResult blockHit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER,
                fluid, player
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

    /** First body touched by a swept shot, including a shot that starts inside a body. Clip end to cover first. */
    public static EntityHitResult firstLivingHit(ServerPlayer player, Vec3 from, Vec3 end, double padding,
                                                  Predicate<LivingEntity> filter) {
        EntityHitResult first = null;
        double nearest = from.distanceToSqr(end) + 1e-7;
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(from, end).inflate(padding + 1),
                target -> target != player && target.isAlive() && !target.isSpectator() && filter.test(target))) {
            AABB body = target.getBoundingBox().inflate(padding);
            Vec3 at = body.contains(from) ? from : body.clip(from, end).orElse(null);
            if (at != null && from.distanceToSqr(at) < nearest) {
                nearest = from.distanceToSqr(at);
                first = new EntityHitResult(target, at);
            }
        }
        return first;
    }
}
