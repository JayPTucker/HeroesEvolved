package com.jayptucker.heroesevolved.cryo;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * A voice from inside a shell of ice: the bright part of speech stopped by the ice, the rest boxed in by the shell's
 * walls a few centimetres from the mouth, a little quieter, and shivering. The ice closes over a voice in a tenth of a
 * second when the speaker freezes and lets it go over a third of a second when they thaw, so a sentence that spans
 * either moment is muffled or freed mid-word instead of clicking.
 *
 * <p>Two halves, neither touching the voice chat. The server publishes who is frozen once a tick
 * ({@link CryoService#tick}, from {@link CryoService#isFrozen}); the voice chat's thread only reads that immutable set
 * ({@link #muffled}). The effect itself is pure arithmetic on 48 kHz mono samples, one per speaker because its filters
 * and echoes carry from one 20 ms frame to the next; {@code telepathy/MindVoice} decodes, calls {@link #process} and
 * encodes again, and a game test can feed it a tone.
 */
public final class IcedVoice {
    private static final int RATE = 48_000, SIZE = 512, MASK = SIZE - 1;
    /** Seconds for the ice to close over a voice, and to let it go. */
    private static final double CLOSE = .12, OPEN = .3;
    /** A silence this long (nanoseconds) starts the next sentence afresh: no old echo, and fully muffled if frozen. */
    public static final long GAP = 300_000_000L;
    /** The inside of the shell: two reflections, 2.3 and 3.7 ms back (the walls a few centimetres off). */
    private static final int WALL_A = 110, WALL_B = 177;

    private static volatile Set<UUID> inIce = Set.of();

    private final Biquad rumble = Biquad.highPass(90, .707), iceA = Biquad.lowPass(900, .707),
            iceB = Biquad.lowPass(900, .707), body = Biquad.peak(310, 1.3, 5);
    private final float[] shell = new float[SIZE];
    private int write;
    private double mix, shiverA, shiverB;
    private long last = Long.MIN_VALUE / 2;

    /** The players frozen solid this tick. Called on the server thread only. */
    public static void publish(Collection<UUID> players) { inIce = Set.copyOf(players); }

    /** Whether this speaker's voice comes from inside the ice. Safe on any thread. */
    public static boolean muffled(UUID speaker) { return speaker != null && inIce.contains(speaker); }

    public static void clear() { inIce = Set.of(); }

    /**
     * Muffles one frame in place. {@code frozen} is whether the speaker is in the ice now, {@code nanos} when the frame
     * arrived. Once the ice has let go the frames come out exactly as they went in.
     *
     * @return false when the voice is clear and the speaker has paused, so the stream can go back to the speaker's
     *         own encoding at a silence rather than mid-word
     */
    public boolean process(short[] pcm, boolean frozen, long nanos) {
        boolean gap = nanos - last > GAP;
        last = nanos;
        if (gap) {
            reset();
            mix = frozen ? 1 : 0;
        }
        if (!frozen && mix <= 0) return !gap;
        double step = 1.0 / (RATE * (frozen ? CLOSE : OPEN));
        for (int i = 0; i < pcm.length; i++) {
            mix = frozen ? Math.min(1, mix + step) : Math.max(0, mix - step);
            double dry = pcm[i] / 32768.0;
            // The ice stops everything bright (two low-passes near 900 Hz); the shell boxes in what's left.
            double x = body.run(iceB.run(iceA.run(rumble.run(dry))));
            double walls = .26 * shell[(write - WALL_A) & MASK] + .17 * shell[(write - WALL_B) & MASK];
            shell[write] = (float) (x + walls * .45);
            write = (write + 1) & MASK;
            // A shiver: two slow wobbles, so it never sounds like a steady tremolo.
            shiverA += 2 * Math.PI * 6.3 / RATE;
            shiverB += 2 * Math.PI * 9.1 / RATE;
            double wet = Math.tanh((x + walls) * .55 * (1 + .06 * Math.sin(shiverA) + .04 * Math.sin(shiverB)));
            pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round((dry * (1 - mix) + wet * mix) * 32768)));
        }
        if (shiverA > 2 * Math.PI) shiverA -= 2 * Math.PI;
        if (shiverB > 2 * Math.PI) shiverB -= 2 * Math.PI;
        return true;
    }

    public void reset() {
        java.util.Arrays.fill(shell, 0);
        rumble.reset(); iceA.reset(); iceB.reset(); body.reset();
    }

    /** One second-order filter (the audio EQ cookbook's), run sample by sample. */
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = a1 / a0; this.a2 = a2 / a0;
        }

        static Biquad lowPass(double hz, double q) {
            double w = 2 * Math.PI * hz / RATE, cos = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return new Biquad((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad highPass(double hz, double q) {
            double w = 2 * Math.PI * hz / RATE, cos = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad peak(double hz, double q, double decibels) {
            double w = 2 * Math.PI * hz / RATE, cos = Math.cos(w), alpha = Math.sin(w) / (2 * q),
                    gain = Math.pow(10, decibels / 40);
            return new Biquad(1 + alpha * gain, -2 * cos, 1 - alpha * gain, 1 + alpha / gain, -2 * cos, 1 - alpha / gain);
        }

        double run(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }

        void reset() { x1 = x2 = y1 = y2 = 0; }
    }
}
