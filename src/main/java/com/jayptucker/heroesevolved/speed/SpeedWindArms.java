package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wind Arms (Shift + the Cyclone key): spin both arms so fast they become two funnels of wind, for as long as
 * the key is held (up to three seconds).
 * <ul>
 *   <li>Look ahead and the funnels blow everything in front of you away: creatures, dropped items and shots,
 *   out to {@link #REACH} blocks, harder the closer they are. It does no harm, and puts out what is burning.</li>
 *   <li>Look down and the funnels drive into the ground and lift you: you rise up to {@link #LIFT} blocks above
 *   the ground and hover there, drifting the way you face. Let go and you float down.</li>
 * </ul>
 */
public final class SpeedWindArms {
    public static final int MAX_TICKS = 60, ENERGY = 12, COOLDOWN = 80;
    public static final double REACH = 14, LIFT = 7;
    /** Looking further down than this (the look's y) points the funnels at the ground. */
    static final double DOWN = -.7;

    private static final class Arms {
        final ServerPlayer player;
        final long start;
        boolean lifted;
        Arms(ServerPlayer p, long start) { player = p; this.start = start; }
    }
    private static final Map<UUID, Arms> ARMS = new HashMap<>();

    private SpeedWindArms() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }

    public static boolean active(ServerPlayer p) { return ARMS.containsKey(p.getUUID()); }

    public static boolean start(ServerPlayer p) {
        if (active(p) || p.isPassenger() || p.isSpectator()) return false;
        ARMS.put(p.getUUID(), new Arms(p, now(p)));
        ArmPosePayload.show(p, ArmPose.WIND_ARMS, MAX_TICKS + 5);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, .8F, 1.4F);
        blow(p, ARMS.get(p.getUUID()), 0);
        return true;
    }

    /** Lets the arms stop. A lift ends in a gentle float down. */
    public static boolean stop(ServerPlayer p) {
        Arms a = ARMS.remove(p.getUUID());
        if (a == null) return false;
        ArmPosePayload.clear(p);
        if (a.lifted && !p.onGround()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, true));
        return true;
    }

    static void tick(long now) {
        for (Arms a : List.copyOf(ARMS.values())) {
            ServerPlayer p = a.player;
            long age = now - a.start;
            if (p.isRemoved() || !p.isAlive() || p.isSpectator() || p.isPassenger() || age > MAX_TICKS || !PowerRules.canAct(p)) { stop(p); continue; }
            blow(p, a, age);
        }
    }

    private static void blow(ServerPlayer p, Arms a, long age) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        if (look.y < DOWN) lift(p, a);
        else push(p, look);
        if (age % 10 == 0)
            SetPiecePayload.send(level, Piece.SPEED_ARMS, p.position(), p.position().add(look), 1, p);
        if (age % 8 == 0)
            level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1F, 1.6F + level.random.nextFloat() * .2F);
    }

    /** Funnels into the ground: up to {@link #LIFT} blocks above it, then a hover, drifting where you face. */
    private static void lift(ServerPlayer p, Arms a) {
        a.lifted = true;
        double height = heightAboveGround(p);
        double up = height < LIFT - 1 ? .5 : height < LIFT ? .12 : -.02;
        Vec3 drift = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (drift.lengthSqr() > 1e-6) drift = drift.normalize().scale(.22);
        p.setDeltaMovement(drift.x, up, drift.z);
        p.resetFallDistance();
        p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    public static double heightAboveGround(Entity e) {
        Vec3 from = e.position();
        var hit = e.level().clip(new ClipContext(from, from.add(0, -(LIFT + 2), 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, e));
        return hit.getType() == HitResult.Type.MISS ? LIFT + 2 : from.y - hit.getLocation().y;
    }

    /** Everything in the cone in front of you, out to {@link #REACH} blocks, is blown away along your look. */
    private static void push(ServerPlayer p, Vec3 look) {
        Vec3 eye = p.getEyePosition();
        for (Entity e : p.serverLevel().getEntities(p, p.getBoundingBox().inflate(REACH),
                e -> e instanceof ItemEntity || e instanceof Projectile pr && pr.getOwner() != p
                        || e instanceof LivingEntity l && SpeedService.canHit(p, l))) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(eye);
            double along = rel.dot(look);
            if (along < .3 || along > REACH) continue;
            if (rel.subtract(look.scale(along)).length() > 1.3 + along * .35) continue;
            double k = 1 - along / REACH;
            if (e instanceof Projectile) {
                // A shot turned round and blown back the way it came.
                e.setDeltaMovement(look.scale(Math.max(.8, e.getDeltaMovement().length())));
                e.hurtMarked = true;
                continue;
            }
            double resist = e instanceof LivingEntity l ? Math.max(.2, 1 - l.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) : 1;
            Vec3 shove = look.scale((.3 + .55 * k) * resist).add(0, (.06 + .08 * k) * resist, 0);
            e.setDeltaMovement(e.getDeltaMovement().scale(.4).add(shove));
            e.hurtMarked = true;
            if (e.isOnFire()) e.clearFire();
            if (e instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        if (p.isOnFire()) p.clearFire();
    }

    static void forget(ServerPlayer p) { ARMS.remove(p.getUUID()); }

    static void clear() { ARMS.clear(); }
}
