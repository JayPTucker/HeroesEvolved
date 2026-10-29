package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.FlightVisualSyncService;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Meteor Drop: hang, fold into the dive, then turn stored airspeed into a pressure wave. */
public final class DiveBombService {
    private static final int WINDUP_TICKS = 10;
    private static final int MAXIMUM_DIVE_TICKS = 70;
    private static final Map<UUID, Dive> DIVES = new HashMap<>();
    private static final Map<UUID, Cushion> CUSHIONED = new HashMap<>();

    private DiveBombService() {}

    public static boolean isDiving(ServerPlayer player) {
        return DIVES.containsKey(player.getUUID());
    }

    public static boolean canStart(ServerPlayer player) {
        return PowerRules.canAct(player) && !player.onGround() && !player.isPassenger()
                && !player.isInWaterOrBubble() && !player.isFallFlying()
                && !isDiving(player) && !FlightService.isCycloneActive(player)
                && !FlightDynamicsService.isCharging(player)
                && !FlightSkillService.isRolling(player) && !FlightSkillService.isChanneling(player)
                && FlightService.isFlightAuthorized(player);
    }

    public static void start(ServerPlayer player, int powerLevel) {
        if (!canStart(player)) return;
        double momentum = FlightDynamicsService.consumeMomentum(player);
        DIVES.put(player.getUUID(), new Dive(player.serverLevel(), diveDirection(player),
                Math.clamp(powerLevel, 1, 5), momentum));
        cushion(player, MAXIMUM_DIVE_TICKS + 40);
        push(player, Vec3.ZERO);
        player.resetFallDistance();
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, MAXIMUM_DIVE_TICKS);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, .8F, .65F);
        FlightVisualSyncService.syncToTrackingPlayers(player);
        MasteryService.markPowerUse(player, ModAbilities.FLIGHT_ID);
    }

    public static void tick(ServerPlayer player) {
        Cushion cushion = CUSHIONED.get(player.getUUID());
        if (cushion != null && (cushion.level != player.serverLevel()
                || player.serverLevel().getGameTime() > cushion.until)) CUSHIONED.remove(player.getUUID());
        Dive dive = DIVES.get(player.getUUID());
        if (dive == null) return;
        if (dive.level != player.serverLevel() || !PowerRules.canAct(player)
                || player.isPassenger() || player.isInWaterOrBubble() || player.isFallFlying()
                || !FlightService.isFlightAuthorized(player)) {
            forget(player);
            push(player, Vec3.ZERO);
            player.resetFallDistance();
            return;
        }

        dive.ageTicks++;
        player.resetFallDistance();
        if (dive.ageTicks <= WINDUP_TICKS) {
            push(player, Vec3.ZERO);
            if (dive.ageTicks % 2 == 0) {
                Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
                AbilityEffects.send(dive.level, Kind.FLIGHT_DIVE_CHARGE, center,
                        center.add(dive.direction), dive.ageTicks / (double) WINDUP_TICKS);
            }
            return;
        }
        if (dive.ageTicks == WINDUP_TICKS + 1) {
            Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
            AbilityEffects.send(dive.level, Kind.FLIGHT_SKYBURST, center, center.add(dive.direction), 1.5);
            dive.level.playSound(null, player.blockPosition(), ModSounds.FLIGHT_BOOST.get(),
                    SoundSource.PLAYERS, .8F, 1.2F);
        }
        if (dive.ageTicks >= MAXIMUM_DIVE_TICKS) {
            cancel(player);
            return;
        }
        // Steering is deliberate and limited: the flier can curve the descent, never climb in it.
        dive.direction = dive.direction.lerp(diveDirection(player), .08).normalize();
        double speed = Math.min(3.2 + dive.momentum * .006,
                1.15 + (dive.ageTicks - WINDUP_TICKS) * .12 + dive.momentum * .004);
        Vec3 velocity = dive.direction.scale(speed);
        BlockHitResult contact = sweptContact(player, velocity);
        if (contact != null || player.onGround() || player.horizontalCollision || player.verticalCollision) {
            DIVES.remove(player.getUUID());
            // Let vanilla's collision solver put the whole hitbox on the near side of the surface.
            if (contact != null) {
                player.move(MoverType.SELF, velocity);
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            }
            impact(player, dive);
            FlightVisualSyncService.syncToTrackingPlayers(player);
            return;
        }
        push(player, velocity);
        if (dive.ageTicks % 2 == 0) {
            Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
            AbilityEffects.send(dive.level, Kind.DIVE_TRAIL, center,
                    center.add(velocity), 1 + dive.momentum * .01);
        }
    }

    /** Sample the leading face as well as the center so a fast dive cannot tunnel through a ledge. */
    private static BlockHitResult sweptContact(ServerPlayer player, Vec3 velocity) {
        BlockHitResult closest = null;
        double distance = Double.MAX_VALUE;
        double half = player.getBbWidth() * .45;
        for (Vec3 offset : new Vec3[]{new Vec3(0, .15, 0), new Vec3(half, .15, half),
                new Vec3(-half, .15, half), new Vec3(half, .15, -half), new Vec3(-half, .15, -half),
                new Vec3(0, player.getBbHeight() * .5, 0)}) {
            Vec3 from = player.position().add(offset);
            BlockHitResult hit = player.serverLevel().clip(new ClipContext(from, from.add(velocity),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            double candidate = hit.getLocation().distanceToSqr(from);
            if (hit.getType() == HitResult.Type.BLOCK && candidate < distance) {
                closest = hit;
                distance = candidate;
            }
        }
        return closest;
    }

    private static Vec3 diveDirection(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        return new Vec3(look.x, Math.min(look.y, -.55), look.z).normalize();
    }

    private static void impact(ServerPlayer player, Dive dive) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double radius = 3.5 + dive.powerLevel * .3 + dive.momentum * .018;
        float damage = (float) (5 + dive.powerLevel + dive.momentum * .06);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), entity -> entity != player && entity.isAlive()
                        && !entity.isSpectator() && !player.isAlliedTo(entity)
                        && !com.jayptucker.heroesevolved.entity.EntityCarryService.isBeingCarried(entity)
                        && entity.distanceToSqr(player) <= radius * radius && player.hasLineOfSight(entity))) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 away = target.position().subtract(center).multiply(1, 0, 1);
            if (away.lengthSqr() < .01) away = new Vec3(1, 0, 0);
            push(target, away.normalize().scale(.9 + dive.momentum * .007).add(0, .55, 0));
        }
        push(player, Vec3.ZERO);
        player.resetFallDistance();
        cushion(player, 40);
        ArmPosePayload.show(player, ArmPose.GROUND, 10);
        AbilityEffects.send(level, Kind.DIVE_IMPACT, center, center.add(0, 1, 0), radius);
        level.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(),
                SoundSource.PLAYERS, 1.3F, .65F);
        CameraShakePayload.sendAround(level, center, radius * 4, 1.2F, 12);
        FlightSkillService.onMeteorImpact(player, center, radius, damage);
    }

    /** Shift + Meteor Drop aborts safely; the wind-up has already paid its stamina cost. */
    public static boolean cancel(ServerPlayer player) {
        if (DIVES.remove(player.getUUID()) == null) return false;
        push(player, Vec3.ZERO);
        player.resetFallDistance();
        cushion(player, 40);
        ArmPosePayload.clear(player);
        FlightVisualSyncService.syncToTrackingPlayers(player);
        return true;
    }

    public static boolean consumeCushion(ServerPlayer player) {
        Cushion cushion = CUSHIONED.remove(player.getUUID());
        return cushion != null && cushion.level == player.serverLevel()
                && player.serverLevel().getGameTime() <= cushion.until;
    }

    public static void forget(ServerPlayer player) {
        boolean active = DIVES.remove(player.getUUID()) != null;
        CUSHIONED.remove(player.getUUID());
        if (active) {
            ArmPosePayload.clear(player);
            FlightVisualSyncService.syncToTrackingPlayers(player);
        }
    }

    private static void cushion(ServerPlayer player, int ticks) {
        CUSHIONED.put(player.getUUID(), new Cushion(player.serverLevel(), player.serverLevel().getGameTime() + ticks));
    }

    private static void push(Entity entity, Vec3 velocity) {
        boolean changed = entity.getDeltaMovement().distanceToSqr(velocity) > .0001;
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player && (changed || velocity.lengthSqr() > .0001 || player.tickCount % 4 == 0))
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private record Cushion(ServerLevel level, long until) {}

    private static final class Dive {
        final ServerLevel level;
        final int powerLevel;
        final double momentum;
        Vec3 direction;
        int ageTicks;
        Dive(ServerLevel level, Vec3 direction, int powerLevel, double momentum) {
            this.level = level;
            this.direction = direction;
            this.powerLevel = powerLevel;
            this.momentum = momentum;
        }
    }
}
