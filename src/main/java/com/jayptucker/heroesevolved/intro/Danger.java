package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Who an awakening has to save and from what. The endangered one is the <em>ward</em>: the owner
 * themself, or an ally nearby (a friendly player or the owner's own pet) who is about to lose a fight.
 */
public final class Danger {
    /** How far an ally can be for each power to answer: runners and flyers come from further away. */
    public static double reach(String power) {
        return switch (power) {
            case "superspeed" -> 48;
            case "flight" -> 64;
            case "time_manipulation" -> 16;
            default -> 24;
        };
    }

    private Danger() {}

    /** Allies an awakening answers: friendly players (teammates, or anyone while PvP is off) and the owner's pets. */
    public static boolean ally(ServerPlayer owner, LivingEntity other) {
        return other != owner && other.isAlive() && !other.isSpectator() && other.level() == owner.level()
                && !(other instanceof TemporalEchoEntity) && StyleOps.isFriend(owner, other);
    }

    /**
     * True when nothing solid stands between this point and the sky (leaves don't count). Flight can only
     * awaken out in the open, and only carry off someone who is out there too.
     */
    public static boolean openSky(Entity entity) {
        Level level = entity.level();
        BlockPos head = BlockPos.containing(entity.getEyePosition());
        if (!level.hasChunkAt(head)) return false;
        return head.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, head.getX(), head.getZ());
    }

    /**
     * Whoever the ward needs saving from: monsters near them, anything targeting the ward or the owner,
     * and whoever last hurt either of them (another player included). Never the owner, the ward or a friend
     * who isn't actually attacking.
     */
    public static List<LivingEntity> threats(ServerPlayer owner, LivingEntity ward, double radius) {
        LivingEntity hurtWard = ward.getLastHurtByMob(), hurtOwner = owner.getLastHurtByMob();
        Vec3 center = ward.position();
        return ward.level().getEntitiesOfClass(LivingEntity.class, ward.getBoundingBox().inflate(radius),
                entity -> entity != owner && entity != ward && entity.isAlive() && !entity.isSpectator()
                        && entity.position().distanceToSqr(center) <= radius * radius
                        && !(entity instanceof TemporalEchoEntity) && !EntityCarryService.isBeingCarried(entity)
                        && (entity == hurtWard || entity == hurtOwner
                        || !StyleOps.isFriend(owner, entity) && (entity instanceof Enemy
                        || entity instanceof Mob mob && (mob.getTarget() == ward || mob.getTarget() == owner))));
    }

    /** Level ground direction away from the threats (or {@code fallback} when there are none). */
    public static Vec3 away(Vec3 from, List<LivingEntity> threats, Vec3 fallback) {
        Vec3 push = Vec3.ZERO;
        for (LivingEntity threat : threats) {
            Vec3 offset = from.subtract(threat.position()).multiply(1, 0, 1);
            double distance = Math.max(1, offset.length());
            push = push.add(offset.normalize().scale(1 / distance));
        }
        if (push.lengthSqr() < 1.0E-4) push = fallback.multiply(1, 0, 1);
        if (push.lengthSqr() < 1.0E-4) push = new Vec3(1, 0, 0);
        return push.normalize();
    }
}
