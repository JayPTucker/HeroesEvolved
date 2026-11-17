package com.jayptucker.heroesevolved.time;

/** The last quarter-second of a voice drags down in pitch, reverberates, and dies into exact silence. 48 kHz mono. */
public final class TemporalVoice {
    public static final int FRAME = 960, HISTORY = FRAME * 12, LENGTH = 48_000 * 3;

    private TemporalVoice() {}

    public static short[] tail(short[] recent) {
        short[] result = new short[LENGTH];
        if (recent.length == 0) return result;
        float[] echo = new float[16_384];
        double read = Math.max(0, recent.length - HISTORY);
        for (int i = 0; i < result.length; i++) {
            double seconds = i / 48_000D;
            int index = (int) read;
            float dry = 0;
            if (index < recent.length - 1) {
                float fraction = (float) (read - index);
                dry = (recent[index] * (1 - fraction) + recent[index + 1] * fraction) / 32768F;
            }
            // The pitch falls continuously instead of simply replaying the fragment at one low pitch.
            read += .65 * Math.exp(-2.2 * seconds) + .045;
            float reflected = echo[(i - 4_081) & 16_383] * .43F
                    + echo[(i - 6_337) & 16_383] * .32F + echo[(i - 10_103) & 16_383] * .21F;
            echo[i & 16_383] = dry + reflected * .68F;
            double fade = Math.pow(1 - i / (double) LENGTH, 1.7);
            // Avoid a click at the join, and guarantee silence at the end of the three-second tail.
            double edge = Math.min(1, i / 240D) * Math.min(1, (LENGTH - i - 1) / 2400D);
            result[i] = (short) Math.round(Math.tanh((dry * .72 + reflected * .65) * fade * edge) * 32767);
        }
        return result;
    }
}
