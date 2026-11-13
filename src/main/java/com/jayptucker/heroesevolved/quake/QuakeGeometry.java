package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Numbers and body anchors Quake's server code and {@code QuakeArt} share, so a blast leaves the palm the player sees. */
public final class QuakeGeometry {
    /** Shockwave's follow-up: the other palm may answer within this many ticks, once. */
    public static final int DOUBLE_TAP_WINDOW = 14;
    /** The Vibration Shell: its radius and how long it stands. */
    public static final double SHELL_RADIUS = 3.2;
    public static final int SHELL_TICKS = 80;
    /** Seismic Lance's reach, and the beat between the punch and the column bursting up. */
    public static final double LANCE_RANGE = 24;
    public static final int LANCE_DELAY = 5;
    /** Pressure Step's launch speed along the aim (about eight blocks). */
    public static final double DASH_SPEED = 1.9;
    /** Read the Pulse: how long a felt heartbeat stays marked, and how much harder the next quake on it lands. */
    public static final int PULSE_TICKS = 100;
    public static final float PULSE_BONUS = 1.25F;
    /** A bullet this close to a blast's line, or about to cross it this tick, is caught. */
    public static final double BULLET_CATCH = 1.8;

    private QuakeGeometry() {}

    /** A palm held out along the look: the main hand, or the other one for Double Tap. */
    public static Vec3 palm(Player player, float partial, boolean offHand, double reach) {
        double side = (player.getMainArm() == HumanoidArm.RIGHT) != offHand ? -1 : 1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                        player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125)
                .add(player.getViewVector(partial).scale(reach));
    }

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound) {
        SetPiecePayload.send(level, piece, from, to, (float) size, bound);
    }
}
