package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.dampener.Dampening;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.TemporalProjectilePayload;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;

/** A brief, readable telekinetic catch, followed by the actual round returning to its shooter. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SylarProjectiles {
    public static final int HOLD_TICKS = 12, MAX_HELD = 32;
    private record Caught(SylarEntity boss, Entity shooter, Vec3 velocity, boolean gravity, long until) {}
    private static final Map<Projectile, Caught> HELD = new HashMap<>();
    private SylarProjectiles() {}
    private static boolean ready(SylarEntity boss) {
        return boss.isAlive() && !boss.isRemoved() && (boss.fighting() || boss.actor()) && !Dampening.dampened(boss)
                && boss.stagger <= 0 && boss.downed < 0 && boss.dying < 0 && boss.transition <= 0;
    }
    public static boolean intercept(ServerLevel level, Entity entity, Vec3 from, Vec3 to) {
        if (!(entity instanceof Projectile shot) || shot.isRemoved() || !PowerWeapons.isBullet(shot)) return false;
        if (HELD.containsKey(shot)) return true;
        if (HELD.size() >= MAX_HELD || shot.getDeltaMovement().lengthSqr() < .001) return false;
        for (SylarEntity boss : level.getEntitiesOfClass(SylarEntity.class, new AABB(from, to).inflate(3), SylarProjectiles::ready)) {
            if (shot.getOwner() == boss || shot.getOwner() != null && shot.getOwner().isAlliedTo(boss)) continue;
            var hit = boss.getBoundingBox().inflate(1.8).clip(from, to);
            if (hit.isEmpty() || PowerWeapons.bulletObstructed(shot, from, hit.get())) continue;
            HELD.put(shot, new Caught(boss, shot.getOwner(), shot.getDeltaMovement(), shot.isNoGravity(), level.getGameTime() + HOLD_TICKS));
            boss.catching(true);
            shot.setPos(hit.get()); shot.setNoGravity(true); shot.setDeltaMovement(Vec3.ZERO);
            sync(level, shot, 0, hit.get().subtract(from).normalize());
            AbilityEffects.send(level, Kind.TK_GRIP, boss.getEyePosition(), shot.position(), .7);
            return true;
        }
        return false;
    }
    private static void sync(ServerLevel level, Projectile shot, int interval, Vec3 direction) {
        PacketDistributor.sendToPlayersTrackingEntity(shot, new TemporalProjectilePayload(shot.getId(), interval,
                shot.position(), shot.getDeltaMovement(), direction, interval == 0));
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Projectile shot) || !(shot.level() instanceof ServerLevel level)) return;
        Caught caught = HELD.get(shot);
        if (caught != null) {
            if (ready(caught.boss) && level.getGameTime() < caught.until) { event.setCanceled(true); return; }
            HELD.remove(shot);
            caught.boss.catching(HELD.values().stream().anyMatch(other -> other.boss == caught.boss));
            Vec3 velocity = caught.velocity;
            if (ready(caught.boss)) {
                Vec3 aim = caught.shooter != null && caught.shooter.isAlive() && caught.shooter.level() == level
                        ? caught.shooter.getEyePosition().subtract(shot.position()).normalize() : velocity.normalize().scale(-1);
                velocity = aim.scale(Math.max(.5, velocity.length()));
                shot.setOwner(caught.boss);
                AbilityEffects.send(level, Kind.TK_LAUNCH, shot.position(), shot.position().add(aim.scale(4)), .8);
            }
            shot.setNoGravity(caught.gravity); shot.setDeltaMovement(velocity); shot.hurtMarked = true; shot.hasImpulse = true;
            sync(level, shot, 1, velocity.normalize());
            return;
        }
        if (intercept(level, shot, shot.position(), shot.position().add(shot.getDeltaMovement()))) event.setCanceled(true);
    }
    @SubscribeEvent public static void impact(ProjectileImpactEvent event) {
        if (event.getProjectile().level() instanceof ServerLevel level && intercept(level, event.getProjectile(),
                event.getProjectile().position(), event.getRayTraceResult().getLocation())) event.setCanceled(true);
    }
    @SubscribeEvent public static void cleanup(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (var entry : java.util.List.copyOf(HELD.entrySet())) if (entry.getKey().isRemoved()) {
            Caught caught = HELD.remove(entry.getKey());
            caught.boss.catching(HELD.values().stream().anyMatch(other -> other.boss == caught.boss));
        }
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        HELD.values().forEach(caught -> caught.boss.catching(false)); HELD.clear();
    }
}
