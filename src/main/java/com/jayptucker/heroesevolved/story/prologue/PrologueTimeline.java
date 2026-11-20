package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import net.minecraft.util.Mth;

/**
 * The numbers the server and every client share for the Last Day, so the light Ted gives off, his shock waves, the flashes
 * on screen and the moment you're knocked flat always agree. Ticks are counted from the moment the light appears; {@code run}
 * is how long the survivors have to reach the safe house (the config's run time, never shorter than {@link #minRun}).
 * <ol>
 *   <li>The light, and small shock waves rolling off Ted ({@link #PULSES}); Peter and Sylar's fight ({@link LastDayFight}).</li>
 *   <li>{@link #WAVE}: the big one. Each player hears it coming and is made to look at it just before it reaches them
 *   ({@code client/effects/intro/WaveApproach}). It races down the avenue ({@link #front}), half the glass it passes
 *   bursts, and it knocks everyone flat for {@link #DAZE} ticks, ears ringing: out cold for {@link #BLACKOUT} of them.</li>
 *   <li>{@link #SIRENS}: the city's sirens, the vault open, the agents calling people in. The light grows and surges until
 *   the run is up, but nobody sees it go.</li>
 * </ol>
 */
public final class PrologueTimeline {
    /** The big wave leaves ground zero, a little after Peter comes down beside Ted. */
    public static final int WAVE = LastDayFight.LAND + 64;
    /**
     * How long someone the wave knocks flat stays down: out cold for {@link #BLACKOUT}, hearing the screaming slowed and
     * smeared as they try to come to, then the radiation awakening's own blinks, and up.
     */
    public static final int DAZE = 160;
    /** How much of {@link #DAZE} is blacked out before the eyes first open: about three seconds. */
    public static final int BLACKOUT = 60;
    /** When the sirens start: while you're still on the ground, so they're the first thing you hear through the ringing. */
    public static final int SIRENS = WAVE + 70;
    /** From here on the street is the way to the stair: the vault is open and the shelter counts. */
    public static final int SHELTER = SIRENS;
    /** Ted's small waves before the big one: when each leaves him, and how far it rolls. */
    public static final int[] PULSES = {30, 120, 215, 300, 380, 455, 525, 590, 650, 705, 755, 800, 840, 875, 905};
    /** How fast the big wave travels once it's out of the fireball, in blocks a tick. */
    public static final float WAVE_SPEED = 2.2F;
    /** The surges after the wave: when each peaks, as a fraction of the time from {@link #SURGES_FROM} to the end. */
    private static final float[] SURGE_AT = {0.08F, 0.28F, 0.45F, 0.6F, 0.73F, 0.84F, 0.93F, 1.0F};
    private static final float[] SURGE_POWER = {0.25F, 0.32F, 0.4F, 0.5F, 0.6F, 0.72F, 0.85F, 1.0F};
    /** Surges start once the wave's dazed have come to. */
    public static final int SURGES_FROM = WAVE + DAZE + 120;
    /** How long a surge takes to rise and fall, in ticks. */
    public static final int SURGE_TICKS = 30;

    private PrologueTimeline() {}

    /** The shortest run the day allows: the fight, the wave, coming to, and a minute and a half to get down. */
    public static int minRun() { return SURGES_FROM + 20 * 70; }

    /** The run's real length for a configured one. */
    public static int run(int configured) { return Math.max(configured, minRun()); }

    // ------------------------------------------------------------------
    // Ted's waves
    // ------------------------------------------------------------------

    /** How far the big wave has come, in blocks from ground zero, {@code dt} ticks after it left him. */
    public static float front(float dt) {
        if (dt <= 0) return 0;
        return 18 * (1 - (float) Math.exp(-dt / 4)) + WAVE_SPEED * dt;
    }

    /** Ticks after {@link #WAVE} until the front reaches a point this far from ground zero. */
    public static int arrival(double distance) {
        int t = 0;
        while (front(t) < distance && t < 400) t++;
        return t;
    }

    /** The tick (after the light) the big wave reaches someone at {@code x, z}. */
    public static int hits(double x, double z) { return WAVE + arrival(IntroBlast.distance(x, z)); }

    /** How far small wave {@code i} rolls before it dies away: further each time. */
    public static float pulseReach(int i) { return 26 + 70 * i / (float) (PULSES.length - 1); }

    /** How hard small wave {@code i} is, from 0 to 1. */
    public static float pulsePower(int i) { return 0.25F + 0.55F * i / (float) (PULSES.length - 1); }

    /** How long a small wave takes to roll out to its reach. */
    public static final int PULSE_TICKS = 34;

    // ------------------------------------------------------------------
    // The surges after it
    // ------------------------------------------------------------------

    /** How many surges there are. */
    public static int surges() { return SURGE_AT.length; }

    /** The tick (after the light) at which surge {@code i} peaks. */
    public static int surgeTick(int i, int run) {
        int span = Math.max(1, run - SURGES_FROM);
        return SURGES_FROM + Math.round(SURGE_AT[i] * span);
    }

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
        // The big wave itself: a white-out at ground zero as it leaves him.
        float w = t - WAVE;
        if (w > -6 && w < 40) total = Math.max(total, w < 0 ? 1 + w / 6 : 1 - w / 40F);
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
