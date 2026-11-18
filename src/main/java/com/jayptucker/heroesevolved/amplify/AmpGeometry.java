package com.jayptucker.heroesevolved.amplify;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The numbers and body anchors Power Amplification's server and drawing share. */
public final class AmpGeometry {
    /** How far in front of the shoulder the hand is when red lightning leaves it. */
    public static final double HAND_REACH = .45;
    /** How big the ball between cupped hands is at full size, and how long it takes to get there. */
    public static final float BALL_RADIUS = .32F;
    public static final int BALL_TICKS = 40;

    private AmpGeometry() {}

    /** The main hand, where every zap, bolt and beam starts (the same anchor Electrokinesis uses). */
    public static Vec3 hand(Player player) { return hand(player, 1); }

    public static Vec3 hand(Player player, float partial) {
        return com.jayptucker.heroesevolved.electro.ElectroGeometry.hand(player, partial, HAND_REACH);
    }

    /** Between the hands, in front of the chest: where the ball grows. */
    public static Vec3 ball(Player player, float partial) {
        Vec3 look = player.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        return player.getPosition(partial).add(0, player.isCrouching() ? 1.0 : 1.2, 0).add(flat.scale(.42)).add(0, look.y * .25, 0);
    }
}
