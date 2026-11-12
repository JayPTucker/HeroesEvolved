package com.jayptucker.heroesevolved.story.elle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Elle's moves on the test floor. Each has a windup that is exactly its telegraph's length, so what you see is when it
 * lands. Every move's numbers live here; what each one does is in {@link ElleEntity}.
 *
 * @param phase  the first phase that uses it
 * @param pose   how she holds herself while winding it up (see {@link ElleEntity.Pose})
 */
public record ElleMoves(String id, int windup, int cooldown, double minRange, double maxRange, int phase, int weight, ElleEntity.Pose pose) {
    /** A crackling filament from her hand to where you stood, then the bolt down it. Step off the line. */
    public static final ElleMoves ARC_BOLT = new ElleMoves("arc_bolt", 16, 36, 2, 26, 1, 3, ElleEntity.Pose.POINT);
    /** Rings run out across the floor from her feet; then the floor discharges. Jump, stand on rubber, or leave. */
    public static final ElleMoves GROUND_CURRENT = new ElleMoves("ground_current", 24, 90, 0, 9, 1, 2, ElleEntity.Pose.PALMS_DOWN);
    /** An arc from a live bank to her chest: she drinks it. Pull that bank's breaker before it ends. */
    public static final ElleMoves SIPHON = new ElleMoves("siphon", 60, 200, 0, 40, 1, 2, ElleEntity.Pose.DRAW);
    /** She forces a cut bank back on. Stand at its breaker (your body grounds the current) or hit her hard. */
    public static final ElleMoves REBOOT = new ElleMoves("reboot", 60, 220, 0, 40, 2, 3, ElleEntity.Pose.POINT);
    /** A ball of lightning forms in her hands, then rolls down a lit lane. Get out of the lane. */
    public static final ElleMoves BALL = new ElleMoves("ball_lightning", 12, 110, 3, 22, 2, 2, ElleEntity.Pose.BOTH_HANDS);
    /** Third phase: rings on the floor under everyone, and lightning from the ceiling into each. Move. */
    public static final ElleMoves STORM = new ElleMoves("storm", 30, 130, 0, 40, 3, 3, ElleEntity.Pose.RAISED);

    public static final List<ElleMoves> ALL = List.of(ARC_BOLT, GROUND_CURRENT, SIPHON, REBOOT, BALL, STORM);

    /** The ground current's reach, from her feet. */
    public static final float RING = 6.5F;
    /** How fast her ball lightning rolls (blocks a tick), how far it goes, and what its burst reaches. */
    public static final double BALL_SPEED = 0.55, BALL_REACH = 20, BALL_BURST = 2.6;
    /** The storm's strike rings. */
    public static final float STRIKE = 1.9F;
    /** How much of her health a siphon gives back, and how much a backfire takes (through her shield). */
    public static final float SIPHON_HEAL = 0.06F, BACKFIRE = 0.16F;
    /** Each live bank takes this much off every hit on her. */
    public static final float SHIELD_PER_BANK = 0.10F;
    /** How long a backfire stuns her. */
    public static final int STAGGER_TICKS = 60;
    /** Within this of a bank's breaker, a player grounds her reboot. */
    public static final double GROUNDED = 2.6;
    /** When the conduits surge (second phase on) and how long each warns first. */
    public static final int SURGE_EVERY_TWO = 100, SURGE_EVERY_THREE = 70, SURGE_WARNING = 24;

    public static ElleMoves byId(String id) {
        for (ElleMoves move : ALL) if (move.id.equals(id)) return move;
        return null;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Her casting hand: right of her body, at chest height, reaching toward her look. */
    static Vec3 hand(LivingEntity elle) {
        float yaw = elle.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return elle.position().add(0, 1.375, 0).add(right.scale(0.3125)).add(elle.getLookAngle().scale(0.72)).add(forward.scale(0.05));
    }

    /** Who her current can hurt: anything alive that isn't her, a spectator, or a creative player. */
    static boolean foe(LivingEntity elle, Entity e) {
        if (!(e instanceof LivingEntity living) || e == elle || !living.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player p && (p.isCreative())) return false;
        return e instanceof Player || e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == elle;
    }

    static List<LivingEntity> foes(LivingEntity elle, Vec3 at, double radius) {
        return elle.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 4, radius),
                e -> foe(elle, e) && Math.hypot(e.getX() - at.x, e.getZ() - at.z) <= radius);
    }

    static void hurt(LivingEntity elle, LivingEntity target, float damage) {
        DamageSource source = elle.damageSources().mobAttack(elle);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
    }

    static void push(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player)
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
    }

    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** Players who can still fight her here (alive, in the room, not spectating). */
    static boolean fighter(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && PrimatechArena.inArena(player);
    }
}
