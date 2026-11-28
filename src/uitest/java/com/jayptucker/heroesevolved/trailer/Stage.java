package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The place every shot is filmed: the scout's hilltop, the ground under the fighter's feet, the actors in the scene and
 * the jobs a shot's server schedule leaves for the client.
 *
 * <p>Shots are written in a <b>shot frame</b>: the fighter at the origin facing +Z, as the other previews stage scenes
 * (the signature preview's numbers carry over unchanged). {@link #at} turns that frame so the fighter faces west, where
 * the sun goes down: forward (+Z) becomes west (-X) and +X becomes south (+Z).</p>
 */
final class Stage {
    /** The ground under the fighter's feet (the block above the top solid one). */
    static volatile BlockPos ground;
    /** How far the highest ground round the stage stands over the fighter's feet. */
    static volatile int lift;
    /** The actors of the shot being filmed (server thread only). */
    static final List<Mob> ACTORS = new ArrayList<>();
    /** What the server's schedule asks of the client (markers, shakes, widenings), done on the client's next tick. */
    static final Queue<Runnable> ON_CLIENT = new ConcurrentLinkedQueue<>();

    private Stage() {}

    static Vec3 feet() { return Vec3.atBottomCenterOf(ground); }

    /** A point of the shot frame in the world: {@code dz} forward (west), {@code dx} to the fighter's left side (south). */
    static Vec3 at(double dx, double dy, double dz) { return feet().add(-dz, dy, dx); }

    /** The fighter's yaw when facing forward in the shot frame (west). */
    static final float FORWARD = 90;

    /** The fighter at {@code (dx, dz)} of the shot frame, standing on the ground, facing forward. */
    static void place(ServerPlayer p, double dx, double dz) {
        Vec3 spot = at(dx, 0, dz);
        ServerLevel world = p.server.overworld();
        p.teleportTo(world, spot.x, groundAt(world, spot), spot.z, FORWARD, 0);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** Turns the fighter to face a point (world coordinates). */
    static void aim(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }

    static double groundAt(ServerLevel world, Vec3 at) {
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
    }

    /**
     * A foe that obeys physics (so powers can throw it) but stands its ground: AI on, every goal removed, never made
     * gifted, 400 health so a shot never kills it early. It faces the fighter.
     */
    static Mob puppet(ServerPlayer p, EntityType<? extends Mob> type, double dx, double dz) {
        ServerLevel world = p.server.overworld();
        Vec3 spot = at(dx, 0, dz);
        Mob mob = type.create(world);
        Vec3 toFighter = feet().subtract(spot);
        float yaw = (float) Math.toDegrees(Math.atan2(-toFighter.x, toFighter.z));
        mob.moveTo(spot.x, groundAt(world, spot), spot.z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        mob.setHealth(400);
        world.addFreshEntity(mob);
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        ACTORS.add(mob);
        return mob;
    }

    /** Clears the last shot away: its actors, and any mob, projectile or dropped item left near the stage. */
    static void clear(ServerPlayer p) {
        ACTORS.forEach(Mob::discard);
        ACTORS.clear();
        ServerLevel world = p.server.overworld();
        for (Entity e : world.getEntities((Entity) null, new AABB(ground).inflate(32),
                e -> e instanceof Mob || e instanceof Projectile || e instanceof ItemEntity)) e.discard();
    }

    /** Notes the video frame being filmed now (for the edit and the score), from the server's schedule. */
    static void mark(String name) { ON_CLIENT.add(() -> FilmRecorder.mark(name)); }
}
