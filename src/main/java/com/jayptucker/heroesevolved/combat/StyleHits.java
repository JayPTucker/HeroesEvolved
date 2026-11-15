package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.flight.FlightSkillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What every fighting style needs to land a blow: who counts as a foe, who is in reach, a hit that ignores the usual
 * hit cooldown (combos land), a shove that respects knockback resistance, and the body's anchors (hands, hips, the
 * level aim). One place, so a rule about who can be hit changes once for every style.
 */
public final class StyleHits {
    private static int depth;

    private StyleHits() {}

    /** True while a style's own blow is landing, so punch passives (Static Touch, Burning Touch) don't stack on it. */
    public static boolean dealing() { return depth > 0; }

    public static boolean hostile(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !(target instanceof AbstractVillager) && FlightSkillService.hostile(player, target);
    }

    public static List<LivingEntity> foes(ServerPlayer player, Vec3 center, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                t -> hostile(player, t) && t.position().distanceToSqr(center) <= radius * radius).stream().limit(32).toList();
    }

    /** Only solid blocks give cover. */
    public static boolean exposed(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    /** Where the aim meets a block within {@code reach}, or the end of the reach. */
    public static Vec3 reach(ServerPlayer player, Vec3 eye, Vec3 aim, double reach) {
        return player.serverLevel().clip(new ClipContext(eye, eye.add(aim.scale(reach)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
    }

    /** The nearest exposed foe whose body is within {@code width} of the aim line, out to {@code length}. */
    public static LivingEntity firstInTube(ServerPlayer player, Vec3 eye, Vec3 aim, double length, double width, boolean throughWalls) {
        LivingEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity candidate : foes(player, eye, length + 1)) {
            Vec3 center = candidate.getBoundingBox().getCenter(), delta = center.subtract(eye);
            double along = delta.dot(aim), reach = width + candidate.getBbWidth() * .5;
            if (along < 0 || along > length + .3 || along >= nearest || delta.subtract(aim.scale(along)).lengthSqr() > reach * reach
                    || !throughWalls && !exposed(player, eye, center)) continue;
            best = candidate;
            nearest = along;
        }
        return best;
    }

    /** The first foe along the look, out to {@code length} (blocks stop it). */
    public static LivingEntity aimed(ServerPlayer player, double length, double width) {
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        return firstInTube(player, eye, aim, eye.distanceTo(reach(player, eye, aim, length)), width, false);
    }

    public static void hurt(ServerPlayer player, LivingEntity target, float damage) {
        hurt(target, player.damageSources().playerAttack(player), damage);
    }

    public static void hurt(LivingEntity target, DamageSource source, float damage) {
        if (!target.isAlive()) return;
        depth++;
        try {
            target.invulnerableTime = 0;
            target.hurt(source, damage);
        } finally {
            depth--;
        }
    }

    /** Shoves a body, weaker against knockback resistance. */
    public static void shove(LivingEntity target, Vec3 velocity) {
        double resist = Math.max(.3, 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * .6);
        StyleMotion.push(target, velocity.scale(resist));
    }

    /** Pushes a body straight away from {@code from}, level, with some lift. */
    public static void away(LivingEntity target, Vec3 from, Vec3 fallback, double speed, double lift) {
        Vec3 out = target.position().subtract(from).multiply(1, 0, 1);
        out = out.lengthSqr() < 1e-4 ? fallback : out.normalize();
        shove(target, out.scale(speed).add(0, lift, 0));
    }

    /** The horizontal aim, falling back to the body's facing when looking straight up or down. */
    public static Vec3 level(Player player) {
        Vec3 look = player.getLookAngle(), flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() > 1e-4) return flat.normalize();
        double radians = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
    }

    public static Vec3 right(Vec3 forward) { return new Vec3(-forward.z, 0, forward.x); }

    /** A hand reaching along the look: {@code main} for the main (rear) arm, otherwise the lead. */
    public static Vec3 hand(Player player, float partial, boolean main) {
        double side = (player.getMainArm() == HumanoidArm.LEFT) == main ? 1 : -1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125, player.isCrouching() ? 1.17 : 1.375,
                Math.sin(yaw) * side * .3125).add(player.getViewVector(partial).scale(.64));
    }

    public static Vec3 chest(Player player) { return player.position().add(0, player.isCrouching() ? .95 : 1.2, 0); }

    public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerPlayer player, Vec3 at, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Moves a body to {@code at} at once (and, for a test's player that never ticks, its stored position too). */
    public static void blink(ServerPlayer player, Vec3 at) {
        player.teleportTo(player.serverLevel(), at.x, at.y, at.z, player.getYRot(), player.getXRot());
        player.absMoveTo(at.x, at.y, at.z);
        player.resetFallDistance();
    }
}
