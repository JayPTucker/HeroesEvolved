package com.jayptucker.heroesevolved.sonic;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Numbers and body anchors shared by the server (where a sound starts and how fast it travels) and
 * {@code SonicArt} (where it is drawn), so the voice leaves the mouth the player sees and a pushed wall is
 * drawn exactly where it hits.
 */
public final class SonicGeometry {
    /** A pushed Wall of Sound rolls this far each tick, for this many ticks (twelve blocks). */
    public static final int WALL_PUSH_TICKS = 16;
    public static final double WALL_PUSH_SPEED = 0.75, WALL_HALF = 2.6;
    /** How long Ringing lasts on a target, and what shattering it adds. */
    public static final int RING_TICKS = 120, RING_DAMAGE = 6, RING_RESONANCE = 10;
    /** Sonic Dive: how fast you fall, and how wide and hard the landing is next to an ordinary one. */
    public static final double DIVE_SPEED = 2.4, DIVE_RADIUS = 6;
    public static final int DIVE_MAX = 40;
    /** A harmonic burst or a Note fed by a scream sends this much on top of the scale. */
    public static final float HARMONIC_FLAG = 32;
    /** Echo Map's ring sweeps out at this many blocks a tick. */
    public static final float ECHO_SPEED = 0.8F;

    private SonicGeometry() {}

    /** Just in front of the mouth: where every cry starts. */
    public static Vec3 mouth(Player player, float partial) {
        Vec3 eye = player.getEyePosition(partial);
        return eye.add(0, -0.12, 0).add(player.getViewVector(partial).scale(0.3));
    }

    /** The main hand held out along the look: where a Note or a Shatter Note leaves. */
    public static Vec3 hand(Player player, float partial, double reach) {
        double side = player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                        player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125)
                .add(player.getViewVector(partial).scale(reach));
    }

    /** Where a Wall of Sound stands for a player facing {@code normal}: 2.2 blocks ahead at chest height. */
    public static Vec3 wallCenter(Vec3 feet, Vec3 normal) { return feet.add(0, 1.2, 0).add(normal.scale(2.2)); }

    /** Where a pushed wall is after {@code age} ticks. */
    public static Vec3 pushed(Vec3 start, Vec3 normal, float age) {
        return start.add(normal.scale(WALL_PUSH_SPEED * Mth.clamp(age, 0, WALL_PUSH_TICKS)));
    }

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound) {
        SetPiecePayload.send(level, piece, from, to, (float) size, bound);
    }
}
