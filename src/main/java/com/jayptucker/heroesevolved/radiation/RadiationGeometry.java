package com.jayptucker.heroesevolved.radiation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where Induced Radioactivity comes from on the body, read by both the server (where a cone, beam or lance starts) and
 * {@code RadiationArt} (where it is drawn), so the heat always leaves the hands the player sees: the main palm along the
 * look, the cupped hands in front of the chest where a core is held, and the core itself, glowing in the chest.
 */
public final class RadiationGeometry {
    private RadiationGeometry() {}

    /** How far ahead of the chest the cupped hands hold a core. */
    public static final double CUPPED = .5;
    /** How far along the look the palm reaches. */
    public static final double PALM = .64;

    private static double yaw(LivingEntity e, float partial) { return Math.toRadians(Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)); }

    private static double shoulder(LivingEntity e) { return e.isCrouching() ? 1.17 : 1.375; }

    /** The main palm, reaching along the look (the server passes a partial of 1). */
    public static Vec3 hand(LivingEntity e, float partial) {
        double side = e.getMainArm() == HumanoidArm.LEFT ? 1 : -1, yaw = yaw(e, partial);
        return e.getPosition(partial).add(Math.cos(yaw) * side * .3125, shoulder(e), Math.sin(yaw) * side * .3125)
                .add(e.getViewVector(partial).scale(PALM));
    }

    /** The off palm, the same on the other side. */
    public static Vec3 offHand(LivingEntity e, float partial) {
        double side = e.getMainArm() == HumanoidArm.LEFT ? -1 : 1, yaw = yaw(e, partial);
        return e.getPosition(partial).add(Math.cos(yaw) * side * .3125, shoulder(e), Math.sin(yaw) * side * .3125)
                .add(e.getViewVector(partial).scale(PALM));
    }

    /** Between the cupped hands, in front of the chest. */
    public static Vec3 cupped(LivingEntity e, float partial) {
        double yaw = yaw(e, partial);
        return e.getPosition(partial).add(-Math.sin(yaw) * CUPPED, e.getBbHeight() * .61, Math.cos(yaw) * CUPPED);
    }

    /** The core: the heart of the reactor, in the chest. */
    public static Vec3 core(LivingEntity e, float partial) {
        double yaw = yaw(e, partial);
        return e.getPosition(partial).add(-Math.sin(yaw) * .12, e.getBbHeight() * .66, Math.cos(yaw) * .12);
    }
}
