package com.jayptucker.heroesevolved.styles;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The small verbs the class abilities are made of: who counts as a foe or a friend, hurting, pushing,
 * healing, aiming at the ground, and dealing with incoming arrows. Every search is capped; nothing here
 * breaks or places blocks.
 */
public final class StyleOps {
    private StyleOps() {}

    public static boolean isFoe(ServerPlayer source, LivingEntity target) {
        return target != source && target.isAlive() && !target.isSpectator() && !source.isAlliedTo(target)
                && !(target instanceof OwnableEntity pet && pet.getOwner() == source)
                && !(target instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && (!(target instanceof ServerPlayer other) || source.getServer().isPvpAllowed() && source.canHarmPlayer(other));
    }

    public static boolean isFriend(ServerPlayer source, LivingEntity target) {
        if (target == source) return true;
        if (target instanceof OwnableEntity pet && pet.getOwner() == source) return true;
        return target instanceof ServerPlayer other && (source.isAlliedTo(other) || !source.getServer().isPvpAllowed() && !(source.getTeam() != null && other.getTeam() != null));
    }

    /** Foes within {@code radius} of a point, nearest first, at most 32. */
    public static List<LivingEntity> foes(ServerPlayer p, Vec3 center, double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                        e -> isFoe(p, e) && e.position().distanceToSqr(center) <= radius * radius).stream()
                .sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(center))).limit(32).toList();
    }

    /** The caster and their friends within {@code radius} (allied or, without PvP, any player; and their own pets), at most 16. */
    public static List<LivingEntity> friends(ServerPlayer p, Vec3 center, double radius) {
        List<LivingEntity> list = new ArrayList<>(p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e != p && e.isAlive() && !e.isSpectator() && isFriend(p, e) && e.position().distanceToSqr(center) <= radius * radius).stream().limit(15).toList());
        list.addFirst(p);
        return list;
    }

    public static void hurt(ServerPlayer p, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(p.damageSources().playerAttack(p), damage);
    }

    public static void magic(ServerPlayer p, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(p.damageSources().indirectMagic(p, p), damage);
    }

    public static void effect(LivingEntity target, Holder<MobEffect> effect, int ticks, int amplifier) {
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    public static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Away from {@code from}, level, at {@code speed}, with a little lift. */
    public static void shove(Entity target, Vec3 from, double speed, double lift) {
        Vec3 away = target.position().subtract(from).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
        push(target, away.scale(speed).add(0, lift, 0));
    }

    public static void heal(LivingEntity target, float amount) {
        if (!target.isInvertedHealAndHarm()) target.heal(amount);
    }

    /** Takes away harmful effects and fire. */
    public static void cleanse(LivingEntity target) {
        for (var e : List.copyOf(target.getActiveEffects())) if (!e.getEffect().value().isBeneficial()) target.removeEffect(e.getEffect());
        target.clearFire();
        target.setTicksFrozen(0);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void piece(ServerPlayer p, Piece piece, Vec3 origin, Vec3 target, float scale, Entity bound, int delay) {
        SetPiecePayload.send(p.serverLevel(), piece, origin, target, scale, bound, delay);
    }

    public static void piece(ServerPlayer p, Piece piece, Vec3 origin, Vec3 target, float scale, Entity bound) {
        piece(p, piece, origin, target, scale, bound, 0);
    }

    /** Where the player is looking, on the ground, at most {@code range} away. */
    public static Vec3 aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 point = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(p.getLookAngle().scale(0.2));
        return ground(p.serverLevel(), point);
    }

    /** The top of the ground under a point, looking down at most 24 blocks. */
    public static Vec3 ground(ServerLevel level, Vec3 at) {
        var hit = level.clip(new ClipContext(at.add(0, 0.5, 0), at.subtract(0, 24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }

    /** Level facing, from the player's look. */
    public static Vec3 facing(ServerPlayer p) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        return look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
    }

    /** Projectiles not fired by the caster or a friend, within {@code radius} of a point, at most 32. */
    public static List<Projectile> incoming(ServerPlayer p, Vec3 center, double radius) {
        return p.serverLevel().getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(radius),
                shot -> !(shot.getOwner() instanceof LivingEntity owner && isFriend(p, owner)) && shot.position().distanceToSqr(center) <= radius * radius)
                .stream().limit(32).toList();
    }

    /** Burns, shatters or swallows every hostile projectile near a point. */
    public static int destroy(ServerPlayer p, Vec3 center, double radius) {
        int n = 0;
        for (Projectile shot : incoming(p, center, radius)) { shot.discard(); n++; }
        return n;
    }

    /** Throws hostile projectiles near a point back out the way they came, now the caster's. */
    public static void reflect(ServerPlayer p, Vec3 center, double radius) {
        for (Projectile shot : incoming(p, center, radius)) {
            Vec3 away = shot.position().subtract(center).normalize();
            if (shot.getDeltaMovement().dot(away) >= 0) continue;
            shot.setOwner(p);
            push(shot, away.scale(Math.max(0.8, shot.getDeltaMovement().length())));
        }
    }
}
