package com.jayptucker.heroesevolved.telepathy;

/**
 * What a voice sounds like inside your head: the same words, but doubled and shimmering, with a short echo off the
 * inside of the skull, a faint tremble, and the boom and the hiss filtered away. One per speaker, because the echo and
 * the shimmer carry from one 20 ms frame to the next. Pure arithmetic on 48 kHz mono samples, so it can be tested
 * without the voice chat ({@link MindVoice} decodes, calls {@link #process} and encodes again).
 */
public final class PsychicVoice {
    private static final int RATE = 48_000, SIZE = 8192, MASK = SIZE - 1;
    /** Echoes 70 and 113 ms back, and how much of them feeds round again. */
    private static final int ECHO_A = 3360, ECHO_B = 5424;
    private static final float FEEDBACK = .6F;
    /** A silence this long (nanoseconds) clears the echo, so a new sentence doesn't start in the last one's tail. */
    private static final long GAP = 400_000_000L;

    private final float[] line = new float[SIZE];
    private int write;
    private double shimmerA, shimmerB, tremble;
    private float low, high, highPrevious;
    private long last = Long.MIN_VALUE / 2;

    /** Turns one frame (in place) into the voice in your head; {@code nanos} is when it arrived. */
    public void process(short[] pcm, long nanos) {
        if (nanos - last > GAP) reset();
        last = nanos;
        for (int i = 0; i < pcm.length; i++) {
            float dry = pcm[i] / 32768F;
            // Thin it: a one-pole high-pass near 180 Hz, then a low-pass near 3.4 kHz.
            float hp = .977F * (high + dry - highPrevious);
            highPrevious = dry;
            high = hp;
            low += .31F * (hp - low);
            float x = low;
            // The echo off the inside of the skull, fed back into itself.
            float echo = .32F * tap(ECHO_A) + .22F * tap(ECHO_B);
            line[write] = x + echo * FEEDBACK;
            // Two copies a few milliseconds behind, each drifting: the doubled, shimmering quality.
            shimmerA += 2 * Math.PI * .43 / RATE;
            shimmerB += 2 * Math.PI * .29 / RATE;
            float a = glide(384 + 144 * Math.sin(shimmerA)), b = glide(624 + 192 * Math.sin(shimmerB));
            tremble += 2 * Math.PI * 5.2 / RATE;
            float wet = (.62F * x + .42F * a + .38F * b + .55F * echo) * (float) (1 + .12 * Math.sin(tremble));
            // A soft limit, so the doubling never clips.
            pcm[i] = (short) Math.round(Math.tanh(wet * 1.15F) * 32767);
            write = (write + 1) & MASK;
        }
        if (shimmerA > 2 * Math.PI) shimmerA -= 2 * Math.PI;
        if (shimmerB > 2 * Math.PI) shimmerB -= 2 * Math.PI;
        if (tremble > 2 * Math.PI) tremble -= 2 * Math.PI;
    }

    private float tap(int delay) { return line[(write - delay) & MASK]; }

    /** A delay tap between two samples. */
    private float glide(double delay) {
        int whole = (int) delay;
        float k = (float) (delay - whole);
        return line[(write - whole) & MASK] * (1 - k) + line[(write - whole - 1) & MASK] * k;
    }

    public void reset() {
        java.util.Arrays.fill(line, 0);
        low = high = highPrevious = 0;
    }
}
