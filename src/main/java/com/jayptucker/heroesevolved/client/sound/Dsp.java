package com.jayptucker.heroesevolved.client.sound;

/**
 * Small building blocks for the sounds the client makes itself: filters, a reverb, noise and
 * wavetables. Everything here runs on the sound thread, one sample at a time.
 */
final class Dsp {
    private Dsp() {}

    static final float TAU = (float) (2 * Math.PI);

    /** A one-pole low-pass: air absorption, a muffling lid, a horn turned away. */
    static final class LowPass {
        private final float rate;
        private float a, z;

        LowPass(float rate, float cutoff) { this.rate = rate; set(cutoff); }

        void set(float cutoff) { a = (float) (1 - Math.exp(-TAU * Math.min(cutoff, rate * 0.49F) / rate)); }

        float run(float x) { return z += a * (x - z); }
    }

    /** A second-order filter from the RBJ cookbook. */
    static final class Biquad {
        private float b0, b1, b2, a1, a2, x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = (float) (b0 / a0); this.b1 = (float) (b1 / a0); this.b2 = (float) (b2 / a0);
            this.a1 = (float) (a1 / a0); this.a2 = (float) (a2 / a0);
        }

        static Biquad lowPass(float rate, float f, float q) {
            double w = TAU * f / rate, c = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad highPass(float rate, float f, float q) {
            double w = TAU * f / rate, c = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad bandPass(float rate, float f, float q) {
            double w = TAU * f / rate, c = Math.cos(w), alpha = Math.sin(w) / (2 * q);
            return new Biquad(alpha, 0, -alpha, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad peak(float rate, float f, float q, float gainDb) {
            double w = TAU * f / rate, c = Math.cos(w), alpha = Math.sin(w) / (2 * q), a = Math.pow(10, gainDb / 40);
            return new Biquad(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a);
        }

        float run(float x) {
            float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }

    /** A delay line that can be read at any age up to its length. */
    static final class Delay {
        private final float[] line;
        private int head;

        Delay(int samples) { line = new float[Math.max(1, samples)]; }

        void write(float x) { line[head] = x; head = (head + 1) % line.length; }

        /** The sample written {@code age} samples ago (1 is the last one). */
        float read(int age) { return line[Math.floorMod(head - age, line.length)]; }
    }

    /**
     * A stereo reverb after Jezar's Freeverb: eight damped combs and four all-passes a side, the
     * right side's delays a little longer than the left's. Sized by a factor on its delays and by
     * the seconds its tail takes to fall 60 dB.
     */
    static final class Reverb {
        private static final int[] COMBS = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        private static final int[] PASSES = {556, 441, 341, 225};
        private static final int SPREAD = 23;
        private final float[][] combs = new float[16][], passes = new float[8][];
        private final int[] combAt = new int[16], passAt = new int[8];
        private final float[] feedback = new float[16], store = new float[16];
        private final float damp;

        Reverb(float rate, float size, float seconds, float damping) {
            float scale = rate / 44100F * size;
            for (int i = 0; i < 16; i++) {
                int length = Math.round((COMBS[i % 8] + (i >= 8 ? SPREAD : 0)) * scale);
                combs[i] = new float[length];
                feedback[i] = (float) Math.pow(10, -3.0 * length / (seconds * rate));
            }
            for (int i = 0; i < 8; i++) passes[i] = new float[Math.round((PASSES[i % 4] + (i >= 4 ? SPREAD : 0)) * scale)];
            damp = damping;
        }

        /** Runs one frame: {@code out[0]} and {@code out[1]} receive the wet left and right. */
        void run(float in, float[] out) {
            float input = in * 0.015F, left = 0, right = 0;
            for (int i = 0; i < 16; i++) {
                float[] line = combs[i];
                float y = line[combAt[i]];
                // The 1e-25 keeps a dying tail out of denormal numbers, which are slow on most CPUs.
                store[i] = y * (1 - damp) + store[i] * damp + 1e-25F;
                line[combAt[i]] = input + store[i] * feedback[i];
                if (++combAt[i] == line.length) combAt[i] = 0;
                if (i < 8) left += y; else right += y;
            }
            for (int i = 0; i < 8; i++) {
                float[] line = passes[i];
                float buffered = line[passAt[i]];
                float x = i < 4 ? left : right;
                float y = buffered - x;
                line[passAt[i]] = x + buffered * 0.5F;
                if (++passAt[i] == line.length) passAt[i] = 0;
                if (i < 4) left = y; else right = y;
            }
            out[0] = left;
            out[1] = right;
        }
    }

    /** Fast white noise in [-1, 1). */
    static final class Noise {
        private long s;

        Noise(long seed) { s = seed == 0 ? 0x9E3779B97F4A7C15L : seed; }

        float next() {
            s ^= s << 13; s ^= s >>> 7; s ^= s << 17;
            return (s >>> 40) / (float) (1 << 23) - 1;
        }
    }

    /** A value that drifts smoothly at random, about {@code hz} new values a second, in [-1, 1]. */
    static final class Wander {
        private final Noise noise;
        private final float step;
        private float from, to, t;

        Wander(float rate, float hz, long seed) {
            noise = new Noise(seed);
            step = hz / rate;
            from = noise.next();
            to = noise.next();
        }

        float next() {
            t += step;
            if (t >= 1) { t -= 1; from = to; to = noise.next(); }
            return from + (to - from) * (0.5F - 0.5F * (float) Math.cos(Math.PI * t));
        }
    }

    /** One cycle of a waveform with the given harmonic amplitudes, scaled to a peak of 1. */
    static float[] table(int size, float[] harmonics) {
        float[] table = new float[size + 1];
        float peak = 0;
        for (int i = 0; i < size; i++) {
            double sum = 0;
            for (int h = 0; h < harmonics.length; h++) sum += harmonics[h] * Math.sin(TAU * (h + 1) * i / (double) size);
            table[i] = (float) sum;
            peak = Math.max(peak, Math.abs(table[i]));
        }
        for (int i = 0; i < size; i++) table[i] /= peak;
        table[size] = table[0];
        return table;
    }

    /** Reads a {@link #table} at a phase in [0, 1). */
    static float wave(float[] table, double phase) {
        double at = phase * (table.length - 1);
        int i = (int) at;
        float f = (float) (at - i);
        return table[i] + (table[i + 1] - table[i]) * f;
    }

    /** A soft limiter: transparent at low levels, rounding peaks toward ±1. */
    static float soft(float x) {
        if (x > 3) return 1;
        if (x < -3) return -1;
        return x * (27 + x * x) / (27 + 9 * x * x);
    }

    /** Raised-cosine ramp from 0 at {@code a} to 1 at {@code b}. */
    static float ramp(float t, float a, float b) {
        if (t <= a) return 0;
        if (t >= b) return 1;
        return 0.5F - 0.5F * (float) Math.cos(Math.PI * (t - a) / (b - a));
    }
}
