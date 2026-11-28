package com.jayptucker.heroesevolved.mimicry;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where an empath's gestures are, shared by the server (where a skill's piece starts) and {@code MimicArt} (where the
 * thread leaves the hand the player sees). The numbers are the player model's: shoulders 1.375 up (1.17 crouching) and
 * 0.3125 to each side of the body's centre, arms 0.72 long, hung from the body's yaw and aimed with the look.
 */
public final class MimicGeometry {
    /** How far in front of the chest the heart is drawn, and how high on the body. */
    public static final double HEART_FORWARD = .18, HEART_HEIGHT = .72;

    private MimicGeometry() {}

    /**
     * The hand at the end of an arm: the lead (main) arm or the rear one, {@code reach} 0 hanging and 1 straight along the
     * look.
     */
    public static Vec3 hand(LivingEntity e, float partial, boolean lead, float reach) {
        float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        boolean rightArm = (e.getMainArm() == HumanoidArm.RIGHT) == lead;
        Vec3 shoulder = e.getPosition(partial).add(0, e.isCrouching() ? 1.17 : 1.375, 0).add(right.scale(rightArm ? .3125 : -.3125));
        Vec3 down = new Vec3(0, -1, 0), look = e.getViewVector(partial);
        Vec3 dir = down.lerp(look, Mth.clamp(reach, 0, 1));
        return shoulder.add(dir.lengthSqr() < 1.0E-4 ? look.scale(.72) : dir.normalize().scale(.72));
    }

    /** The empath's heart: on the chest a little in front, where taken things bloom and threads end. */
    public static Vec3 heart(LivingEntity e, float partial) {
        float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        return e.getPosition(partial).add(0, e.getBbHeight() * HEART_HEIGHT, 0).add(forward.scale(HEART_FORWARD));
    }

    /** Anyone's chest (a friend, a foe), where a thread to them lands. */
    public static Vec3 chest(LivingEntity e, float partial) {
        return e.getPosition(partial).add(0, e.getBbHeight() * .62, 0);
    }
}
