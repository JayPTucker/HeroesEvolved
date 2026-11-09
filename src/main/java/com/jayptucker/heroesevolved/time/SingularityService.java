package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Singularity (Time Manipulation, Wayfinder signature). Space folds to a point.
 *
 * <p>Aim and a black point opens where you look. For five seconds it pulls every enemy within ten blocks
 * into a spiralling orbit, faster the closer they fall; anything at the core is torn at. Hostile arrows
 * and fireballs bend into it and vanish, and loose items drift in. Then it collapses: the fold snaps
 * back as a shockwave that throws everything outward, and whatever it swallowed is set at your feet.
 *
 * <p>No blocks are touched. Bosses feel a fraction of the pull, allies none, and frozen or preserved
 * creatures stay where time put them.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SingularityService {
    public static final int LIFETIME = 100, FORMING = 10, MAX_ACTIVE = 16;
    public static final double PULL_RADIUS = 10, CORE_RADIUS = 1.6, COLLAPSE_RADIUS = 8;
    /** The accretion disk leans a little, so it reads as a disk from the side and from above. */
    public static final Vec3 DISK_NORMAL = new Vec3(0.28, 1, 0.16).normalize();

    private static final class Hole {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 center;
        final long born;
        final int powerLevel;
        int swallowed;
        Hole(ServerPlayer owner, Vec3 center, int powerLevel) {
            this.owner = owner; this.level = owner.serverLevel(); this.center = center;
            this.born = level.getGameTime(); this.powerLevel = powerLevel;
        }
        int age() { return (int) (level.getGameTime() - born); }
    }
    private static final Map<UUID, Hole> HOLES = new HashMap<>();

    private SingularityService() {}

    public static boolean active(ServerPlayer player) { return HOLES.containsKey(player.getUUID()); }
    public static int count() { return HOLES.size(); }
    public static Vec3 center(ServerPlayer player) { Hole hole = HOLES.get(player.getUUID()); return hole == null ? null : hole.center; }

    public static boolean activate(ServerPlayer player, int powerLevel) {
        if (HOLES.containsKey(player.getUUID()) || HOLES.size() >= MAX_ACTIVE || !PowerRules.canAct(player)) return false;
        return open(player, aim(player), powerLevel);
    }

    /** Opens one at a given point (tests and the aim above). */
    public static boolean open(ServerPlayer player, Vec3 center, int powerLevel) {
        if (HOLES.containsKey(player.getUUID())) return false;
        Hole hole = new Hole(player, center, powerLevel);
        HOLES.put(player.getUUID(), hole);
        ServerLevel level = hole.level;
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.9F, 0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.2F, 0.5F);
        ArmPosePayload.show(player, ArmPose.ONE_HAND, 14);
        return true;
    }

    /** Where you look, held clear of the surface, at least four blocks out. */
    private static Vec3 aim(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(look.scale(24)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 point = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(2.5)).add(0, 0.5, 0)
                : eye.add(look.scale(16));
        return point.distanceToSqr(eye) < 16 ? eye.add(look.scale(4)) : point;
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (HOLES.isEmpty()) return;
        for (Hole hole : List.copyOf(HOLES.values())) {
            if (hole.level.getServer() != server) continue;
            ServerPlayer owner = hole.owner;
            if (owner.isRemoved() || !owner.isAlive() || owner.serverLevel() != hole.level || !PowerRules.canAct(owner)) {
                HOLES.remove(owner.getUUID());
                release(hole);
                AbilityEffects.send(hole.level, Kind.TIME_SINGULARITY_COLLAPSE, hole.center, hole.center.add(DISK_NORMAL), 0.4);
                continue;
            }
            int age = hole.age();
            if (age >= LIFETIME) { HOLES.remove(owner.getUUID()); collapse(hole); continue; }
            float strength = Math.min(1, age / (float) FORMING);
            pull(hole, strength);
            if (age % 2 == 0) {
                float size = strength * (1 - Math.max(0, age - (LIFETIME - 8)) / 12F);
                AbilityEffects.send(hole.level, Kind.TIME_SINGULARITY, hole.center, hole.center.add(DISK_NORMAL), Math.max(0.15, size));
            }
            if (age % 20 == 10) hole.level.playSound(null, hole.center.x, hole.center.y, hole.center.z, SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 1.5F, 0.5F);
        }
    }

    private static boolean pinned(Entity entity) {
        return SpaceTimeService.isSuspended(entity) || ExpansionSkills.preserved(entity);
    }

    private static void pull(Hole hole, float strength) {
        ServerLevel level = hole.level;
        Vec3 center = hole.center;
        AABB reach = new AABB(center, center).inflate(PULL_RADIUS);
        boolean tear = hole.age() % 10 == 0;
        for (Entity entity : level.getEntities((Entity) null, reach, entity -> entity.isAlive() && !entity.isSpectator() && !pinned(entity))) {
            Vec3 at = entity.getBoundingBox().getCenter(), toward = center.subtract(at);
            double distance = toward.length();
            if (distance > PULL_RADIUS || distance < 1.0E-4) continue;
            Vec3 in = toward.scale(1 / distance);
            if (entity instanceof LivingEntity living) {
                if (!ExpansionSkills.hostile(hole.owner, living)) continue;
                double scale = strength * (living.getType().is(Tags.EntityTypes.BOSSES) ? 0.15 : 1.0)
                        * (1 - 0.7 * living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
                // Inward harder near the core, plus a sideways swirl so they orbit as they fall.
                Vec3 swirl = DISK_NORMAL.cross(in).normalize();
                double inward = 0.05 + 0.13 * (1 - distance / PULL_RADIUS);
                Vec3 velocity = distance < 0.8 ? living.getDeltaMovement().scale(0.3)
                        : living.getDeltaMovement().scale(0.82).add(in.scale(inward * scale)).add(swirl.scale(0.07 * scale));
                move(living, velocity);
                living.resetFallDistance();
                if (tear && distance <= CORE_RADIUS) {
                    living.hurt(hole.owner.damageSources().playerAttack(hole.owner), 3.0F + hole.powerLevel * 0.5F);
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
                }
            } else if (entity instanceof Projectile projectile) {
                // A trident is somebody's weapon, not ammunition: it is never swallowed.
                if (projectile instanceof net.minecraft.world.entity.projectile.ThrownTrident) continue;
                Entity shooter = projectile.getOwner();
                if (shooter == hole.owner || shooter instanceof Player other && hole.owner.isAlliedTo(other)) continue;
                if (distance < 1.2) { projectile.discard(); hole.swallowed++; continue; }
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.9).add(in.scale(0.18 * strength)));
                projectile.hurtMarked = true;
            } else if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                Vec3 velocity = distance < 0.7 ? Vec3.ZERO : entity.getDeltaMovement().scale(0.8).add(in.scale(0.08 * strength));
                entity.setDeltaMovement(velocity);
                entity.setNoGravity(distance < 2.5);
                entity.hurtMarked = true;
            }
        }
    }

    /** The fold snaps back. Public for tests. */
    public static void collapse(ServerPlayer player) {
        Hole hole = HOLES.remove(player.getUUID());
        if (hole != null) collapse(hole);
    }

    private static void collapse(Hole hole) {
        ServerLevel level = hole.level;
        Vec3 center = hole.center;
        ServerPlayer owner = hole.owner;
        AbilityEffects.send(level, Kind.TIME_SINGULARITY_COLLAPSE, center, center.add(DISK_NORMAL), COLLAPSE_RADIUS);
        float damage = 8.0F + hole.powerLevel;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(COLLAPSE_RADIUS),
                target -> ExpansionSkills.hostile(owner, target) && !pinned(target)
                        && target.getBoundingBox().getCenter().distanceToSqr(center) <= COLLAPSE_RADIUS * COLLAPSE_RADIUS)) {
            Vec3 out = target.getBoundingBox().getCenter().subtract(center);
            double falloff = 1 - out.length() / COLLAPSE_RADIUS;
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
            target.hurt(owner.damageSources().playerAttack(owner), (float) (damage * (0.5 + 0.5 * falloff)));
            move(target, out.scale(0.6 + 1.1 * falloff).add(0, 0.45, 0));
        }
        // What it swallowed comes out at your feet.
        for (Entity loose : level.getEntities((Entity) null, new AABB(center, center).inflate(3), entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb)) {
            loose.setNoGravity(false);
            if (owner.isAlive() && owner.serverLevel() == level && owner.distanceToSqr(center) < 64 * 64) {
                loose.teleportTo(owner.getX(), owner.getY() + 0.3, owner.getZ());
                loose.setDeltaMovement(Vec3.ZERO);
                if (loose instanceof ItemEntity item) item.setNoPickUpDelay();
            }
        }
        release(hole);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
        CameraShakePayload.sendAround(level, center, COLLAPSE_RADIUS * 3, 1.8F, 14);
        ScreenTintPayload.sendAround(level, center, COLLAPSE_RADIUS * 2, 0x60B98CFF, 8, ScreenTintPayload.Style.FLASH);
        if (hole.swallowed > 0 && owner.isAlive())
            owner.displayClientMessage(net.minecraft.network.chat.Component.literal("The fold swallowed " + hole.swallowed
                    + (hole.swallowed == 1 ? " shot." : " shots.")).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void move(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Loose items it was holding up fall normally again (they are never left floating in a save). */
    private static void release(Hole hole) {
        for (Entity loose : hole.level.getEntities((Entity) null, new AABB(hole.center, hole.center).inflate(PULL_RADIUS + 2),
                entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb)) loose.setNoGravity(false);
    }

    public static void forget(ServerPlayer player) {
        Hole hole = HOLES.remove(player.getUUID());
        if (hole != null) release(hole);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        HOLES.values().forEach(SingularityService::release);
        HOLES.clear();
    }
}
