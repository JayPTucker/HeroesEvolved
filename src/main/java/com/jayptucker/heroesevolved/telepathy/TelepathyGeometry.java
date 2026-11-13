package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Numbers and body anchors shared by the Telepathy skills on the server and {@code TelepathyArt} on the client, so a
 * thread leaves the hand the player sees, a talk-down closes exactly as it lands and a memory unspools at the speed the
 * server recorded it.
 */
public final class TelepathyGeometry {
    /** Persuasion's talk-down, in ticks: two seconds, one with Silver Tongue. */
    public static final int PERSUADE_TICKS = 40, PERSUADE_QUICK = 20;
    public static final double PERSUADE_RANGE = 16, PERSUADE_LONG = 24;
    /** How long a persuaded mob is on your side, and a mob let go with Strings Attached. */
    public static final int PERSUADED_TICKS = 600, STRINGS_TICKS = 300;
    /** Drop It: how long a mob can't fight, and how long a player's hands won't obey. */
    public static final int DISARM_TICKS = 80, HANDS_TICKS = 40;
    /** Far Hearing: how long it lasts and how far it reaches (Eavesdropper doubles it); whispers carry 48 blocks. */
    public static final int HEARING_TICKS = 600;
    public static final double HEARING_RANGE = 160, HEARING_LONG = 320, WHISPER_RANGE = 48;
    /** A Far Hearing voice mark floats this far from the listener's eyes, toward the voice. */
    public static final double MARK_DISTANCE = 2.6;
    /** Memory Walk: how far back it reaches (Deep Read doubles it), how often a creature's place is remembered. */
    public static final int MEMORY_TICKS = 600, MEMORY_DEEP = 1200, MEMORY_STEP = 10;
    /** A walked memory is drawn as at most this many trail pieces, each unspooling this many ticks after the last. */
    public static final int TRAIL_PIECES = 20, TRAIL_DELAY = 2;
    /** Memory Walk on a place: how near a creature must have passed, and how many trails it shows. */
    public static final double PLACE_RADIUS = 6;
    public static final int PLACE_TRAILS = 4;
    /** Lost in Thought: how long a mob walks its own memory, and how long a player is lost. */
    public static final int LOST_MOB_TICKS = 120, LOST_PLAYER_TICKS = 80;
    /** Hive Link: how long the link holds (ten minutes), how far it reaches when made, and how many friends it takes. */
    public static final int HIVE_TICKS = 12000, HIVE_SIZE = 6;
    public static final double HIVE_RANGE = 24;
    /** Waking Fright: how long a frightened mind is Shaken, and what the next hit on it adds. */
    public static final int SHAKEN_TICKS = 100;
    public static final float SHAKEN_DAMAGE = 6;
    /** A mind Mind Network can speak into needs at least this much Focus. */
    public static final int PROJECT_FOCUS = 30;

    /**
     * Flags carried in a piece's scale (the client clamps a scale to 64): a memory trail segment where its mind was hurt,
     * a Hive halo whose friend is speaking or in danger.
     */
    public static final float FLAG_HURT = 32, FLAG_SPEAKING = 16, FLAG_DANGER = 32;

    private TelepathyGeometry() {}

    /** The main hand held out along the look: where a command or a persuasion thread leaves. */
    public static Vec3 hand(Player player, float partial, double reach) {
        double side = player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                        player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125)
                .add(player.getViewVector(partial).scale(reach));
    }

    /** Just beside the eye on the main-hand side: two fingers at the temple. */
    public static Vec3 temple(Player player, float partial) {
        double side = player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot));
        return player.getEyePosition(partial).add(Math.cos(yaw) * side * .27, .06, Math.sin(yaw) * side * .27);
    }

    /** Where a voice mark floats for a listener at {@code eye} hearing a voice at {@code voice}. */
    public static Vec3 mark(Vec3 eye, Vec3 voice) {
        Vec3 toward = voice.subtract(eye);
        return toward.lengthSqr() < 1e-6 ? eye : eye.add(toward.normalize().scale(MARK_DISTANCE));
    }

    /** Shows a piece to everyone close enough. */
    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound) {
        SetPiecePayload.send(level, piece, from, to, (float) size, bound);
    }

    /** Shows a piece to one player only: what a telepath sees inside a mind, or a voice only they hear. */
    public static void showTo(ServerPlayer viewer, Piece piece, Vec3 from, Vec3 to, double size, Entity bound, int delay) {
        PacketDistributor.sendToPlayer(viewer, new SetPiecePayload(piece.ordinal(), from, to, (float) size,
                bound == null ? -1 : bound.getId(), viewer.getRandom().nextInt(), Math.max(0, delay)));
    }
}
