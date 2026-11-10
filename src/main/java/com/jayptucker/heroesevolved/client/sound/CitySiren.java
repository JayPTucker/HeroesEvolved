package com.jayptucker.heroesevolved.client.sound;

/**
 * The city's air-raid sirens, made the way a mechanical siren makes its sound, so they never loop
 * and can play for as long as a scene lasts.
 *
 * <p>Each siren is a rotor chopping air through ports: its pitch is the rotor's speed, and its
 * motor is switched on and off so the speed, and with it the pitch, rises and falls in the long
 * wail of an attack warning. From rest the first rise starts as a low growl. Louder as it spins
 * faster, the tone goes out through a horn that turns slowly on its mast, so it swells and
 * brightens each time it faces the listener. Three sirens start one after another at different
 * distances across the city, each a little out of step and out of tune with the others; distance
 * dulls them, the air makes them waver, and the streets throw back echoes and a long tail.
 */
public final class CitySiren extends PcmStream {
    private static final int RATE = 44100;
    private static final float DT = 1F / RATE;
    /** The rotor's air pulses: a rounded square wave, odd harmonics strong, some even ones. */
    private static final float[] CHOPPER;

    static {
        float[] harmonics = new float[18];
        for (int n = 1; n <= harmonics.length; n++)
            harmonics[n - 1] = n % 2 == 1 ? (float) Math.pow(n, -1.05) : 0.35F * (float) Math.pow(n, -1.2);
        CHOPPER = Dsp.table(2048, harmonics);
    }

    private final Unit[] units;
    private final Dsp.Delay echoes = new Dsp.Delay(RATE / 2);
    private final Dsp.LowPass echoLeft = new Dsp.LowPass(RATE, 2600), echoRight = new Dsp.LowPass(RATE, 2200);
    private final Dsp.Delay preDelay = new Dsp.Delay(RATE / 20);
    private final Dsp.Reverb streets = new Dsp.Reverb(RATE, 1.6F, 3.2F, 0.45F);
    private final float[] wet = new float[2];
    private long frame;

    public CitySiren() {
        super(RATE, 2);
        long seed = System.nanoTime();
        units = new Unit[] {
                // The nearest, a few streets away: two tones a minor third apart.
                new Unit(seed, 0.00F, 0.58F, 0.30F, 0.32F, 5400, 470, 1.2F, 7.5F, 4.2F, 2.6F, 1.7F, 2.8F, 0.06F),
                // Across the city to the west: one deeper tone, a heavier rotor.
                new Unit(seed + 1, 0.85F, 0.27F, -0.55F, 0.60F, 2700, 438, 0, 11F, 5.0F, 3.0F, 2.2F, 3.4F, 0.14F),
                // Far off to the east, barely more than its echo.
                new Unit(seed + 2, 2.10F, 0.13F, 0.70F, 0.95F, 1500, 498, 1.25F, 9F, 3.6F, 2.4F, 1.8F, 2.9F, 0.22F),
        };
    }

    @Override protected int render(float[] out, int frames) {
        for (int i = 0; i < frames; i++, frame++) {
            float t = frame * DT, left = 0, right = 0, send = 0;
            for (Unit unit : units) {
                float s = unit.next(t);
                left += s * unit.left;
                right += s * unit.right;
                send += s * unit.send;
            }
            // Building faces a block or two away and across the street: a few slap-backs, dulled.
            echoes.write((left + right) * 0.5F);
            float slapLeft = echoLeft.run(0.30F * echoes.read(RATE * 87 / 1000) + 0.16F * echoes.read(RATE * 231 / 1000));
            float slapRight = echoRight.run(0.24F * echoes.read(RATE * 143 / 1000) + 0.12F * echoes.read(RATE * 377 / 1000));
            preDelay.write(send + (slapLeft + slapRight) * 0.4F);
            streets.run(preDelay.read(RATE * 45 / 1000), wet);
            out[2 * i] = Dsp.soft(0.9F * (left + slapLeft + wet[0] * 0.55F));
            out[2 * i + 1] = Dsp.soft(0.9F * (right + slapRight + wet[1] * 0.55F));
        }
        return frames;
    }

    /** One siren on its mast somewhere in the city. */
    private static final class Unit {
        final float start, gain, left, right, send, top, second, turnRate, flutterDepth;
        final float onSeconds, offSeconds, up, down;
        final Dsp.Noise noise;
        final Dsp.Wander flutter, timing;
        final Dsp.LowPass facing = new Dsp.LowPass(RATE, 8000), air1, air2;
        final Dsp.Biquad cut = Dsp.Biquad.highPass(RATE, 220, 0.7F), horn = Dsp.Biquad.peak(RATE, 1800, 0.9F, 5);
        final Dsp.Biquad rushBand = Dsp.Biquad.bandPass(RATE, 3400, 0.8F);
        double speed, phase1, phase2, angle;
        float motorClock, phaseLength;
        boolean motor = true;
        int counter;

        Unit(long seed, float start, float gain, float pan, float send, float airCutoff, float top, float second,
             float turnSeconds, float onSeconds, float offSeconds, float upSeconds, float downSeconds, float flutterDepth) {
            this.start = start;
            this.gain = gain;
            this.left = (float) Math.cos((pan + 1) * Math.PI / 4);
            this.right = (float) Math.sin((pan + 1) * Math.PI / 4);
            this.send = send;
            this.top = top;
            this.second = second;
            this.turnRate = Dsp.TAU / turnSeconds;
            this.onSeconds = onSeconds;
            this.offSeconds = offSeconds;
            this.up = 1 - (float) Math.exp(-DT / upSeconds);
            this.down = 1 - (float) Math.exp(-DT / downSeconds);
            this.flutterDepth = flutterDepth;
            noise = new Dsp.Noise(seed * 31 + 7);
            flutter = new Dsp.Wander(RATE, 0.45F, seed * 17 + 3);
            timing = new Dsp.Wander(RATE, 0.2F, seed * 13 + 5);
            air1 = new Dsp.LowPass(RATE, airCutoff);
            air2 = new Dsp.LowPass(RATE, airCutoff * 1.4F);
            angle = (seed % 628) / 100.0;
            phaseLength = onSeconds;
        }

        float next(float t) {
            if (t < start) return 0;
            // The operator's motor switch: on to climb, off to coast down, never quite in rhythm.
            motorClock += DT;
            if (motorClock >= phaseLength) {
                motorClock = 0;
                motor = !motor;
                phaseLength = (motor ? onSeconds : offSeconds) * (1 + 0.12F * timing.next());
            }
            speed += motor ? (1 - speed) * up : -speed * down;
            double f = speed * top;
            phase1 += f * DT;
            phase1 -= Math.floor(phase1);
            float tone = Dsp.wave(CHOPPER, phase1);
            if (second > 0) {
                phase2 += f * second * DT;
                phase2 -= Math.floor(phase2);
                tone = (tone + 0.8F * Dsp.wave(CHOPPER, phase2)) * 0.56F;
            }
            float s = (float) speed;
            float loudness = (float) Math.pow(s, 2.2);
            // Air rushing through the ports: a hiss that rides the chopping and grows with speed.
            float rush = rushBand.run(noise.next()) * (0.5F + 0.5F * Math.abs(tone)) * s * s * s * 0.22F;
            float x = horn.run(cut.run(tone * loudness + rush));
            // The horn turns: louder and brighter facing this way, dull and quiet facing away.
            angle += turnRate * DT;
            float facingUs = 0.5F + 0.5F * (float) Math.cos(angle);
            if ((counter++ & 31) == 0) facing.set(1300 + 9000 * facingUs * facingUs);
            x = facing.run(x) * (0.3F + 0.7F * (float) Math.pow(facingUs, 1.6));
            // Distance: air absorption, and wind making the level waver.
            x = air2.run(air1.run(x));
            return x * gain * (1 + flutterDepth * flutter.next()) * Dsp.ramp(t, start, start + 0.05F);
        }
    }
}
