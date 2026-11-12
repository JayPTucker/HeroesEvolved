package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fission Leap: a blast under your feet throws you up and forward, and the shock where you left throws and
 * doses enemies. Press again in the air to slam down along your aim: you land in a second, bigger blast that
 * leaves the ground contaminated. You take no fall damage from either.
 */
public final class RadiationLeap {
    /** Launch speed (blocks a tick) up and forward: about ten blocks high and twelve along. */
    static final double LAUNCH_UP = 1.2, LAUNCH_FORWARD = 0.95;
    /** How fast the slam drives you down. */
    static final double SLAM_SPEED = 2.6;
    public static final double LAUNCH_RADIUS = 3.5;
    /** Ticks after a launch before a slam can start, and before landing ends the leap. */
    static final int SLAM_AFTER = 3, MAX_TICKS = 120, SLAM_MAX = 40, CUSHION = 10;
    public static final int CORE = 8, ZONE_TICKS = 160;

    private static final class Leap {
        final ServerPlayer player;
        final int tier;
        final long start;
        boolean slamming, relaunched;
        long slamStart, landed = -1;
        Vec3 dive = Vec3.ZERO;
        Leap(ServerPlayer player, int tier, long start) { this.player = player; this.tier = tier; this.start = start; }
    }
    private static final Map<UUID, Leap> LEAPS = new HashMap<>();

    private RadiationLeap() {}

    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    public static boolean leaping(ServerPlayer p) {
        Leap l = LEAPS.get(p.getUUID());
        return l != null && l.landed < 0;
    }

    public static boolean slamming(ServerPlayer p) {
        Leap l = LEAPS.get(p.getUUID());
        return l != null && l.slamming && l.landed < 0;
    }

    /** In the air after a launch and not yet slamming: the next press slams. */
    public static boolean canSlam(ServerPlayer p) {
        Leap l = LEAPS.get(p.getUUID());
        return l != null && l.landed < 0 && !l.slamming && now(p) - l.start >= SLAM_AFTER && !p.onGround();
    }

    /** Falls during a leap, and just after it, do no harm. */
    public static boolean cushioned(ServerPlayer p) {
        Leap l = LEAPS.get(p.getUUID());
        return l != null && (l.landed < 0 || now(p) - l.landed <= CUSHION);
    }

    /** The blast under your feet. Refused while a leap is still in the air. */
    public static boolean launch(ServerPlayer p, int tier) {
        if (p.isPassenger() || p.isSpectator()) return false;
        if (leaping(p)) return relaunch(p);
        ServerLevel level = p.serverLevel();
        Vec3 feet = p.position();
        Vec3 look = p.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1e-4 ? Vec3.ZERO : forward.normalize();
        push(p, forward.scale(LAUNCH_FORWARD).add(0, LAUNCH_UP, 0));
        p.resetFallDistance();
        blast(p, feet, LAUNCH_RADIUS, (2.0F + tier) * RadiationService.multiplier(p), 1, 0.9);
        SetPiecePayload.send(level, Piece.RAD_LEAP, feet, feet.add(forward.scale(2)).add(0, 4, 0), (float) LAUNCH_RADIUS, null);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1F, 1.45F);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
        CameraShakePayload.sendAround(level, feet, 16, 0.6F, 8);
        ArmPosePayload.show(p, ArmPose.GROUND, 8);
        LEAPS.put(p.getUUID(), new Leap(p, tier, now(p)));
        // Fallout Boots: the ground you left stays contaminated for a while.
        if (RadiationSkills.has(p, RadiationSkills.FALLOUT_BOOTS)) RadiationService.contaminate(p, feet, 3, RadiationSkills.zoneTicks(p, 100));
        RadiationService.addMass(p, CORE);
        MasteryService.markPowerUse(p, ModAbilities.RADIATION_ID);
        return true;
    }

    /** Second Leap: one more launch from mid-air, before any slam. */
    private static boolean relaunch(ServerPlayer p) {
        Leap l = LEAPS.get(p.getUUID());
        if (l == null || l.relaunched || l.slamming || !RadiationSkills.has(p, RadiationSkills.SECOND_LEAP)) return false;
        l.relaunched = true;
        Vec3 look = p.getLookAngle(), forward = new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1e-4 ? Vec3.ZERO : forward.normalize();
        push(p, forward.scale(LAUNCH_FORWARD).add(0, LAUNCH_UP * 0.85, 0));
        SetPiecePayload.send(p.serverLevel(), Piece.RAD_LEAP, p.position(), p.position().add(0, 4, 0), 2.0F, null);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.7F);
        RadiationService.addMass(p, CORE / 2);
        return true;
    }

    /** The second press: drive down along your aim (always at least a steep dive). */
    public static boolean slam(ServerPlayer p) {
        if (!canSlam(p)) return false;
        Leap l = LEAPS.get(p.getUUID());
        Vec3 look = p.getLookAngle();
        l.dive = new Vec3(look.x, Math.min(look.y, -0.55), look.z).normalize();
        l.slamming = true;
        l.slamStart = now(p);
        push(p, l.dive.scale(SLAM_SPEED));
        ArmPosePayload.show(p, ArmPose.GROUND, SLAM_MAX);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
        return true;
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        for (Leap l : List.copyOf(LEAPS.values())) {
            ServerPlayer p = l.player;
            if (p.isRemoved() || !p.isAlive()) { LEAPS.remove(p.getUUID()); continue; }
            long now = now(p), age = now - l.start;
            if (l.landed >= 0) {
                if (now - l.landed > CUSHION) LEAPS.remove(p.getUUID());
                continue;
            }
            p.resetFallDistance();
            if (age > MAX_TICKS || !PowerRules.canAct(p)) { l.landed = now; continue; }
            if (l.slamming) {
                boolean down = p.onGround() || p.verticalCollision || p.isInWater() || p.isInLava()
                        || now - l.slamStart > SLAM_MAX;
                if (down) { impact(p, l); continue; }
                push(p, l.dive.scale(SLAM_SPEED));
            } else if (age > SLAM_AFTER && p.onGround()) {
                l.landed = now;
            }
        }
    }

    /** Landing a slam: the bigger blast, and ground that stays contaminated. */
    private static void impact(ServerPlayer p, Leap l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        double radius = 4.0 + l.tier * 0.5;
        boolean critical = RadiationService.mass(p) >= RadiationService.CRITICAL;
        boolean catastrophe = RadiationSkills.has(p, RadiationSkills.CATASTROPHE) && RadiationService.inOwnZone(p, at);
        if (catastrophe) radius *= 1.6;
        blast(p, at, radius, (4.0F + l.tier * 1.5F) * RadiationService.multiplier(p) * (catastrophe ? 1.5F : 1.0F), critical || catastrophe ? 2 : 1, 1.3);
        RadiationService.contaminate(p, at, radius * (catastrophe ? 1.2 : 0.8), RadiationSkills.zoneTicks(p, ZONE_TICKS));
        SetPiecePayload.send(level, Piece.RAD_SLAM, at, at.add(0, 1, 0), (float) radius, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.75F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
        CameraShakePayload.sendAround(level, at, 28, 1.2F, 12);
        push(p, Vec3.ZERO);
        ArmPosePayload.clear(p);
        l.landed = now(p);
        RadiationService.addMass(p, CORE);
    }

    /** Hurts, doses and throws every enemy within {@code radius} of {@code at}, harder near the middle. */
    static void blast(ServerPlayer p, Vec3 at, double radius, float damage, int stages, double throwSpeed) {
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().move(at.subtract(p.position())).inflate(radius),
                e -> RadiationService.hostile(p, e))) {
            Vec3 rel = e.position().subtract(at);
            double d = rel.length();
            if (d > radius + e.getBbWidth() / 2) continue;
            double k = 1 - 0.5 * Math.min(1, d / radius);
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().indirectMagic(p, p), (float) (damage * k));
            RadiationService.irradiate(e, p, stages, 140);
            Vec3 out = new Vec3(rel.x, 0, rel.z);
            out = out.lengthSqr() < 1e-4 ? p.getLookAngle().multiply(1, 0, 1) : out.normalize();
            double resist = Math.max(0.2, 1 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            e.setDeltaMovement(out.scale(throwSpeed * k * resist).add(0, 0.45 * resist, 0));
            e.hurtMarked = true;
            if (e instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
    }

    private static void push(ServerPlayer p, Vec3 velocity) {
        p.setDeltaMovement(velocity);
        p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    public static void forget(ServerPlayer p) { LEAPS.remove(p.getUUID()); }

    public static void clear() { LEAPS.clear(); }
}
