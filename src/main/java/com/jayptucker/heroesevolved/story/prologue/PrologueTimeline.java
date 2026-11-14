package com.jayptucker.heroesevolved.story.prologue;

import net.minecraft.util.Mth;

/**
 * The numbers the server and every client share for the Last Day, so the light Ted gives off, its surges and the flashes
 * on screen always agree. Ticks are counted from the moment the light appears; {@code run} is how long the survivors have
 * to reach the safe house (the config's run time).
 */
public final class PrologueTimeline {
    /** When the sirens start, after the light: fifteen seconds of people just staring at it before anyone runs. */
    public static final int SIRENS = 300;
    /** The surges: when each peaks, as a fraction of the run, and how hard. The last lands as the agents fire. */
    private static final float[] SURGE_AT = {0.22F, 0.40F, 0.55F, 0.68F, 0.79F, 0.88F, 0.95F, 1.0F};
    private static final float[] SURGE_POWER = {0.25F, 0.32F, 0.4F, 0.5F, 0.6F, 0.72F, 0.85F, 1.0F};
    /** How long a surge takes to rise and fall, in ticks. */
    public static final int SURGE_TICKS = 30;

    private PrologueTimeline() {}

    /** How many surges there are. */
    public static int surges() { return SURGE_AT.length; }

    /** The tick (after the light) at which surge {@code i} peaks. */
    public static int surgeTick(int i, int run) { return Math.round(SURGE_AT[i] * run); }

    /** How hard surge {@code i} is, from 0 to 1. */
    public static float surgePower(int i) { return SURGE_POWER[i]; }

    /** The surges' combined swell at {@code t} ticks after the light: a quick rise and a slower fall around each peak. */
    public static float surge(float t, int run) {
        float total = 0;
        for (int i = 0; i < SURGE_AT.length; i++) {
            float d = t - surgeTick(i, run);
            if (d < -SURGE_TICKS / 3F || d > SURGE_TICKS) continue;
            float shape = d < 0 ? 1 + d / (SURGE_TICKS / 3F) : 1 - d / SURGE_TICKS;
            total = Math.max(total, SURGE_POWER[i] * shape * shape);
        }
        return total;
    }

    /**
     * How bright Ted is at {@code t} ticks after the light: a glow that grows steadily through the run, with the surges
     * on top. It never reaches the flash: on the Last Day nobody sees him go.
     */
    public static float glow(float t, int run) {
        if (t < 0) return 0;
        float base = 0.12F + 0.6F * Mth.clamp(t / Math.max(1, run), 0, 1);
        return Mth.clamp(base + 0.35F * surge(t, run), 0, 1);
    }
}
