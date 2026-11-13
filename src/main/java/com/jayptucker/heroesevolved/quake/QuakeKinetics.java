package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What Quake's vibrations do to things in their way, rather than to people. A bullet coming at her is shaken to dust; an
 * explosive (a TaCZ explosive round, primed TNT, a TNT minecart, a ghast's fireball, a wither skull, an end crystal) goes
 * off where the wave meets it; a creeper's fuse is lit; anything else thrown at her is turned back and becomes hers.
 * Every blast, the beam and the Vibration Shell ask here first.
 */
public final class QuakeKinetics {
    /** What happened to the thing the wave met. */
    public enum Result { NONE, DUST, DETONATED, TURNED }

    /** Kinetic Sink: every shot she breaks or turns bleeds a little Strain. */
    static final String KINETIC_SINK = "quake_1_2";

    private QuakeKinetics() {}

    /** Whether {@code entity} is an explosive the wave would set off (a living creeper is handled as a body). */
    public static boolean explosive(Entity entity) {
        return entity instanceof PrimedTnt || entity instanceof MinecartTNT || entity instanceof LargeFireball
                || entity instanceof WitherSkull || entity instanceof EndCrystal || PowerWeapons.bulletExplosion(entity) != null;
    }

    /** Whether {@code shot} is heading at {@code owner}: bullets only shatter when they are. */
    static boolean incoming(ServerPlayer owner, Entity shot) {
        Vec3 to = owner.getBoundingBox().getCenter().subtract(shot.position());
        return shot.getDeltaMovement().dot(to) > 0;
    }

    /**
     * The wave from {@code owner}, travelling along {@code push}, meets {@code entity}. Returns what it did, or NONE when
     * the entity isn't one of the things this handles (a living body, the owner's own shot, a bullet flying away).
     */
    public static Result disrupt(ServerPlayer owner, Entity entity, Vec3 push) {
        if (!entity.isAlive() || entity == owner) return Result.NONE;
        ServerLevel level = owner.serverLevel();
        if (entity instanceof Creeper creeper) {
            // The wave sets its fuse; it is still hit and thrown like any other body.
            creeper.ignite();
            return Result.NONE;
        }
        if (entity instanceof Projectile shot && shot.getOwner() == owner) return Result.NONE;
        if (explosive(entity)) {
            detonate(owner, entity);
            return settle(owner, Result.DETONATED);
        }
        if (PowerWeapons.isBullet(entity)) {
            if (!incoming(owner, entity)) return Result.NONE;
            Vec3 at = entity.position(), from = entity.getDeltaMovement().lengthSqr() < 1.0E-6 ? push.reverse() : entity.getDeltaMovement();
            entity.discard();
            QuakeGeometry.show(level, Piece.QUAKE_DUST, at, at.add(from.normalize()), 1, null);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.5F, 2.0F);
            return settle(owner, Result.DUST);
        }
        if (entity instanceof Projectile projectile) {
            // Caught by the wave and flung back the way it pushes.
            double speed = Math.max(1.6, projectile.getDeltaMovement().length());
            projectile.setOwner(owner);
            projectile.setDeltaMovement(push.normalize().scale(speed));
            projectile.hurtMarked = true;
            level.getChunkSource().broadcastAndSend(projectile, new ClientboundSetEntityMotionPacket(projectile));
            return settle(owner, Result.TURNED);
        }
        return Result.NONE;
    }

    /** Sets an explosive off where it is: the wave is the impact it was waiting for. */
    static void detonate(ServerPlayer owner, Entity entity) {
        Level level = entity.level();
        Vec3 at = entity.position();
        if (entity instanceof EndCrystal crystal) {
            crystal.hurt(owner.damageSources().playerAttack(owner), 1);
            return;
        }
        float radius;
        boolean fire = false, breaks;
        float[] round = PowerWeapons.bulletExplosion(entity);
        if (round != null) { radius = round[0]; breaks = round[1] > 0; }
        else if (entity instanceof PrimedTnt || entity instanceof MinecartTNT) { radius = 4; breaks = true; }
        else if (entity instanceof LargeFireball) { radius = 1; breaks = true; fire = true; }
        else { radius = 1; breaks = true; }
        Entity source = entity instanceof Projectile shot && shot.getOwner() != null ? shot.getOwner() : entity;
        entity.discard();
        level.explode(entity, source.damageSources().explosion(entity, source), null, at.x, at.y + entity.getBbHeight() / 2, at.z,
                radius, fire, breaks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    private static Result settle(ServerPlayer owner, Result result) {
        if (SkillTreeService.has(owner, KINETIC_SINK)) QuakeKit.relieve(owner, 3);
        return result;
    }

    /** Whether a projectile is on, or about to cross this tick, a line from {@code origin} along {@code direction}. */
    static boolean crossing(Entity shot, Vec3 origin, Vec3 direction, double length, double reach) {
        for (Vec3 p : new Vec3[] {shot.position(), shot.position().add(shot.getDeltaMovement())}) {
            Vec3 to = p.subtract(origin);
            double along = to.dot(direction);
            if (along >= -0.5 && along <= length + 0.5 && to.subtract(direction.scale(along)).length() <= reach) return true;
        }
        return false;
    }
}
