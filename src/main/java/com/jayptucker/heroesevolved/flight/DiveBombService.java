package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.particles.ModParticles;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dive Bomb: the flier hurls themself down along their gaze at supersonic
 * speed. Hitting anything solid releases a sonic shockwave, and the flier
 * walks away unharmed.
 */
public final class DiveBombService {
    private static final int MAXIMUM_DIVE_TICKS = 60;
    private static final Map<UUID, Dive> DIVES = new HashMap<>();
    // Landings that must not cause fall damage: set on dive, cleared on fall.
    private static final Set<UUID> CUSHIONED = new HashSet<>();

    private DiveBombService() {
    }

    public static boolean isDiving(ServerPlayer player) {
        return DIVES.containsKey(player.getUUID());
    }

    public static void start(ServerPlayer player, int powerLevel) {
        Vec3 look = player.getLookAngle();
        // A dive always heads downward, even when started looking level.
        Vec3 direction = new Vec3(look.x, Math.min(look.y, -0.35D), look.z).normalize();
        double speed = 2.4D + powerLevel * 0.12D;

        DIVES.put(player.getUUID(), new Dive(direction.scale(speed), powerLevel));
        CUSHIONED.add(player.getUUID());
        push(player, direction.scale(speed));
        Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_BURST,
                center, center.add(direction), 1.6D);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, MAXIMUM_DIVE_TICKS);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FLIGHT_BOOST.get(), SoundSource.PLAYERS, 0.8F, 1.3F);
        MasteryService.markPowerUse(player, ModAbilities.FLIGHT_ID);
    }

    public static void tick(ServerPlayer player) {
        Dive dive = DIVES.get(player.getUUID());
        if (dive == null) {
            return;
        }

        dive.ageTicks++;
        boolean struck = player.onGround() || player.horizontalCollision
                || player.verticalCollision || player.isInWater();
        if (struck && dive.ageTicks > 1) {
            DIVES.remove(player.getUUID());
            impact(player, dive);
            return;
        }
        if (dive.ageTicks >= MAXIMUM_DIVE_TICKS || !player.isAlive()) {
            DIVES.remove(player.getUUID());
            ArmPosePayload.clear(player);
            return;
        }

        // Hold the dive speed: creative-style flight drag would otherwise
        // bleed it away within a few ticks.
        push(player, dive.velocity);
        ServerLevel level = player.serverLevel();
        boolean posed = player.getData(ModDataAttachments.PLAYER_FLIGHT.get())
                .visualPoseActive() && !player.onGround();
        Vec3 feet = FlightVisualGeometry.feet(player.position(),
                FlightVisualGeometry.direction(player, FlightService.isCycloneActive(player), 1.0F), posed);
        level.sendParticles(ModParticles.WHITE_CONTRAIL_SMOKE.get(),
                feet.x, feet.y, feet.z, 1, 0.08D, 0.08D, 0.08D, 0.0D);
        if (dive.ageTicks % 2 == 0) {
            AbilityEffects.send(level, Kind.DIVE_TRAIL, feet, feet.add(dive.velocity), 1.0D);
        }
    }

    /** A sonic shockwave where the flier hits, flinging everything nearby. */
    private static void impact(ServerPlayer player, Dive dive) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double radius = 3.5D + dive.powerLevel * 0.3D;
        float damage = 6.0F + dive.powerLevel;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity != player && entity.isAlive()
                        && entity.distanceToSqr(player) <= radius * radius)) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 away = target.position().subtract(center).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            push(target, away.normalize().scale(1.1D).add(0.0D, 0.55D, 0.0D));
        }

        push(player, Vec3.ZERO);
        player.resetFallDistance();
        ArmPosePayload.show(player, ArmPose.GROUND, 10);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.3D,
                center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.2D, center.z,
                40, radius / 2, 0.1D, radius / 2, 0.15D);
        AbilityEffects.send(level, Kind.DIVE_IMPACT, center,
                center.add(0.0D, 1.0D, 0.0D), radius);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, center.x, center.y, center.z,
                ModSounds.FLIGHT_BOOST.get(), SoundSource.PLAYERS, 1.0F, 0.8F);
        CameraShakePayload.sendAround(level, center, radius * 5.0D, 2.0F, 14);
    }

    /** @return true if this landing should take no fall damage */
    public static boolean consumeCushion(ServerPlayer player) {
        return CUSHIONED.remove(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        DIVES.remove(player.getUUID());
        CUSHIONED.remove(player.getUUID());
    }

    private static void push(net.minecraft.world.entity.Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        } else {
            entity.hurtMarked = true;
        }
    }

    private static final class Dive {
        private final Vec3 velocity;
        private final int powerLevel;
        private int ageTicks;

        private Dive(Vec3 velocity, int powerLevel) {
            this.velocity = velocity;
            this.powerLevel = powerLevel;
        }
    }
}
