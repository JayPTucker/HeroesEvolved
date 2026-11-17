package com.jayptucker.heroesevolved.combat;

import net.minecraft.world.phys.Vec3;

/**
 * Beats and shapes the server's finishers and the client's drawing both read, for the finishers whose bodies are drawn
 * differently ({@code BodyCopies}): Monument's statue, Off Frequency's copies, Hall of Mirrors' panes and Unmade's
 * vanishing body. One place, so the picture never runs ahead of the move.
 */
public final class FinisherBeats {
    /** Monument: the plates close over the foe, it is stone until it crumbles. */
    public static final int MONUMENT_SHUT = 8, MONUMENT_LAND = 22, MONUMENT_CRUMBLE = 25, MONUMENT_TICKS = 32;
    /** Off Frequency: the foe splits, the copies blink out one by one from GONE. */
    public static final int FREQUENCY_SPLIT = 6, FREQUENCY_LAND = 20, FREQUENCY_GONE = 20, FREQUENCY_TICKS = 26;
    /** Hall of Mirrors: the panes rise from RISE (one a tick), you step out of the one behind the foe at STEP. */
    public static final int MIRRORS_RISE = 4, MIRRORS_STEP = 18, MIRRORS_LAND = 21, MIRRORS_TICKS = 30, MIRRORS_PANES = 5;
    public static final double MIRRORS_RADIUS = 1.9;
    /** Unmade: the plates close by GONE and the body under them is gone; the blow lands at LAND. */
    public static final int UNMADE_GONE = 15, UNMADE_LAND = 21, UNMADE_TICKS = 30;

    /** Unwound: violet copies of the foe step back out of it from SPLIT and are drawn back in; the body is gone at GONE. */
    public static final int UNWOUND_SPLIT = 3, UNWOUND_GONE = 18, UNWOUND_LAND = 22, UNWOUND_TICKS = 30;
    /** Half-Life: the body greens, greys to ash from ASH, and is gone at GONE (the blow lands at LAND). */
    public static final int HALF_LIFE_ASH = 16, HALF_LIFE_LAND = 22, HALF_LIFE_GONE = 26, HALF_LIFE_TICKS = 34;
    /** Breach Drop: the breach opens under the foe by OPEN, it falls from FALL and is through at LAND. */
    public static final int BREACH_OPEN = 8, BREACH_FALL = 12, BREACH_LAND = 18, BREACH_TICKS = 28;
    /** Heavy Hand: the blow at LAND drives the body down into the crater over SINK ticks. */
    public static final int HEAVY_LAND = 16, HEAVY_SINK = 8, HEAVY_TICKS = 30;
    /** Echo: a gold outline of the foe rises in front of it from RISE and hits it with its own attack at LAND. */
    public static final int ECHO_RISE = 4, ECHO_LAND = 16, ECHO_TICKS = 26;
    /**
     * Its Own Face: you wear the foe's shape from WEAR (your body hidden) and shed it at BACK, the moment you strike (the
     * copy is the foe's own model, so it would fall with the foe's death if it stayed).
     */
    public static final int FACE_WEAR = 6, FACE_LAND = 20, FACE_BACK = 20, FACE_TICKS = 30;
    /** Apex Bite: you are hidden under a predator's shape from SHIFT to BACK; it lunges and bites at LAND. */
    public static final int APEX_SHIFT = 5, APEX_LAND = 15, APEX_BACK = 22, APEX_TICKS = 30;
    /** A piece with this scale or less marks "hide the body it is bound to" (the second half of a two-body plan). */
    public static final float HIDE_MARK = .3F;

    private FinisherBeats() {}

    /**
     * Where pane {@code i} of {@code count} stands round the foe at {@code foe}, counted from the side facing
     * {@code from} (the finisher's starting place): pane 0 is between you and the foe, the middle one is behind it.
     */
    public static Vec3 pane(Vec3 foe, Vec3 from, int i, int count) {
        Vec3 toward = from.subtract(foe).multiply(1, 0, 1);
        double start = toward.lengthSqr() < 1e-4 ? 0 : Math.atan2(toward.z, toward.x);
        double angle = start + i * Math.PI * 2 / count;
        return foe.add(Math.cos(angle) * MIRRORS_RADIUS, 0, Math.sin(angle) * MIRRORS_RADIUS);
    }

    /** The pane farthest from where you started: the one you step out of. */
    public static int behind(int count) { return count / 2; }
}
