package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Waking in the stasis bunker, heard from inside the pod while the screen is still dark.
 *
 * <p>The emergency supply cuts in with a thump and a hum. The facility alarm starts: a rising
 * electronic whoop through an old horn speaker out in the hall, muffled by the pod's lid and ringing
 * down the concrete, sagging for its first few cycles while the voltage recovers. Each whoop peaks
 * with the red light's pulse. Inside the pod, close and dry, a two-tone warning counts down and
 * quickens; when the lid opens the pod chimes three rising notes. The alarm goes on until the sleeper
 * steps out ({@link #stop}), and then the main power may come back ({@link BunkerPowerUp}).
 */
public final class BunkerAlarm extends PcmStream {
    private static final int RATE = 44100;
    /** From the cue (BunkerService, age 12) to the lid opening (RELEASE, age 110), in seconds. */
    static final float RELEASE = (110 - 12) / 20F;
    /** Nobody is left alone with it for ever: a sleeper who never steps out hears it this long. */
    private static final float END = 180;
    /** The alarm now sounding, to be stopped when the sleeper steps out. */
    @org.jetbrains.annotations.Nullable private static SynthSound playing;
    /** The red screen pulse's period: sin(millis / 160) in ClientScreenTintState. */
    private static final float PERIOD = (float) (2 * Math.PI * 0.160);
    /** How long each whoop climbs. */
    private static final float SWEEP = 0.70F;
    private static final float[] SAW, BEEP;

    static {
        float[] saw = new float[20];
        for (int n = 1; n <= saw.length; n++) saw[n - 1] = 1F / n;
        SAW = Dsp.table(2048, saw);
        BEEP = Dsp.table(1024, new float[] {1, 0, 0.18F, 0, 0.08F});
    }

    private final float offset;
    private final boolean inPod;
    private final Dsp.Noise noise = new Dsp.Noise(System.nanoTime());
    private final Dsp.Wander wobble = new Dsp.Wander(RATE, 0.3F, 11);
    private final Dsp.Biquad speakerLow = Dsp.Biquad.highPass(RATE, 280, 0.7F), speakerHigh = Dsp.Biquad.lowPass(RATE, 3400, 0.8F);
    private final Dsp.Biquad cone = Dsp.Biquad.peak(RATE, 1150, 1.4F, 6);
    private final Dsp.LowPass lid = new Dsp.LowPass(RATE, 2600), farLid = new Dsp.LowPass(RATE, 2600), thumpFilter = new Dsp.LowPass(RATE, 900);
    private final Dsp.Delay farSpeaker = new Dsp.Delay(RATE / 20), capsule = new Dsp.Delay(RATE / 50), preDelay = new Dsp.Delay(RATE / 40);
    private final Dsp.Reverb concrete = new Dsp.Reverb(RATE, 0.85F, 2.1F, 0.22F);
    private final float[] wet = new float[2];
    private double sweepPhase, beepPhase, humPhase;
    private float nextBeep = 0.05F, beepStart = -1, beepHz;
    private boolean high = true;
    private long frame;

    private BunkerAlarm(float offset, boolean inPod) {
        super(RATE, 2);
        this.offset = offset;
        this.inPod = inPod;
        if (!inPod) {
            lid.set(8000);
            farLid.set(8000);
        }
    }

    /**
     * Starts the alarm, lined up so each whoop tops out as the red light is brightest. {@code inPod}: heard by
     * the sleeper, muffled by the lid, with the pod's own warning and chime; otherwise as heard out in the bunker.
     */
    public static void play(boolean inPod) {
        // The light is brightest where sin(millis / 160) peaks: seconds from now until it next does.
        double angle = (Util.getMillis() / 160.0) % (2 * Math.PI);
        double toBrightest = ((Math.PI / 2 - angle) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) * 0.160;
        float latency = 0.06F;
        float offset = (float) (((SWEEP - toBrightest + latency) % PERIOD + PERIOD) % PERIOD);
        Minecraft mc = Minecraft.getInstance();
        if (playing != null) playing.end();
        var level = mc.level;
        playing = new SynthSound(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "bunker_alarm"),
                "subtitles.heroesevolved.bunker_alarm", SoundSource.BLOCKS, () -> new BunkerAlarm(offset, inPod))
                .whileTrue(() -> Minecraft.getInstance().level == level);
        mc.getSoundManager().play(playing);
    }

    /**
     * The sleeper is out of the pod: the alarm cuts off, and with {@code power} the main breaker closes
     * and the bunker's power comes back.
     */
    public static void stop(boolean power) {
        if (playing != null) playing.fadeOut(3);
        playing = null;
        if (power) BunkerPowerUp.play();
    }

    @Override protected int render(float[] out, int frames) {
        int made = 0;
        for (; made < frames; made++, frame++) {
            float t = frame / (float) RATE;
            if (t >= END) break;
            // With the lid open the hall speaker is no longer muffled.
            if (inPod && t >= RELEASE && t < RELEASE + 0.5F && (frame & 63) == 0) {
                float cutoff = 2600 + 5400 * Dsp.ramp(t, RELEASE, RELEASE + 0.4F);
                lid.set(cutoff);
                farLid.set(cutoff);
            }
            float whoop = klaxon(t);
            farSpeaker.write(whoop);
            float hall = lid.run(whoop), farHall = farLid.run(farSpeaker.read(RATE * 19 / 1000)) * 0.85F;
            float pod = inPod ? beeps(t) : 0, chime = inPod ? chime(t) : 0, power = power(t);
            capsule.write(pod);
            pod += 0.25F * capsule.read(RATE * 53 / 10000) + 0.18F * capsule.read(RATE * 89 / 10000);
            preDelay.write(hall * 0.9F + farHall * 0.9F + pod * 0.15F + chime * 0.3F + power * 0.2F);
            concrete.run(preDelay.read(RATE * 14 / 1000), wet);
            float centre = pod + chime + power;
            out[2 * made] = Dsp.soft(hall * 0.55F + farHall * 0.25F + centre + wet[0] * 0.5F);
            out[2 * made + 1] = Dsp.soft(hall * 0.30F + farHall * 0.50F + centre + wet[1] * 0.5F);
        }
        return made;
    }

    /** The hall alarm: a whoop every pulse, through a tired horn speaker. */
    private float klaxon(float t) {
        float clock = t + offset;
        float cycleStart = (float) Math.floor(clock / PERIOD) * PERIOD - offset;
        float c = clock - (float) Math.floor(clock / PERIOD) * PERIOD;
        // Whoops that would begin after the end never start; the one already going finishes.
        if (c >= SWEEP || cycleStart > END - 1) return 0;
        float u = c / SWEEP;
        float sag = 1 - 0.22F * (float) Math.exp(-t / 0.9F);
        float hz = 360 * (float) Math.pow(2.55, Math.pow(u, 0.85)) * sag;
        sweepPhase += hz / RATE;
        sweepPhase -= Math.floor(sweepPhase);
        float envelope = Dsp.ramp(c, 0, 0.015F) * (1 - Dsp.ramp(c, SWEEP - 0.05F, SWEEP)) * (1 - (float) Math.exp(-t / 0.5F));
        float x = Dsp.wave(SAW, sweepPhase) * envelope;
        x = cone.run(speakerHigh.run(speakerLow.run(x)));
        return (float) Math.tanh(1.8 * x) / (float) Math.tanh(1.8) * 0.32F * Dsp.ramp(t, 0, 0.03F);
    }

    /** The pod's own warning: two tones, every half second, quickening before the lid opens. */
    private float beeps(float t) {
        if (t >= nextBeep && nextBeep < RELEASE - 0.05F) {
            beepStart = nextBeep;
            beepHz = high ? 1318.5F : 987.8F;
            high = !high;
            nextBeep += t < RELEASE - 1.6F ? 0.5F : 0.25F;
        }
        if (beepStart < 0) return 0;
        float age = t - beepStart, length = 0.13F;
        if (age > length) return 0;
        beepPhase += beepHz / RATE;
        beepPhase -= Math.floor(beepPhase);
        return Dsp.wave(BEEP, beepPhase) * Dsp.ramp(age, 0, 0.003F) * (1 - Dsp.ramp(age, length - 0.025F, length)) * 0.2F;
    }

    /** The lid opening: three rising notes with a bell's decay. */
    private float chime(float t) {
        float sum = 0;
        float[] notes = {783.99F, 1046.5F, 1568F};
        for (int i = 0; i < notes.length; i++) {
            float age = t - (RELEASE + 0.05F + i * 0.12F);
            if (age < 0) continue;
            float f = notes[i];
            float ring = (float) (Math.sin(Dsp.TAU * f * age) + 0.3 * Math.sin(Dsp.TAU * 2 * f * age) + 0.1 * Math.sin(Dsp.TAU * 3.01 * f * age));
            sum += ring * Dsp.ramp(age, 0, 0.004F) * (float) Math.exp(-age / 0.35F);
        }
        return sum * 0.12F;
    }

    /** The emergency supply's thump and hum. */
    private float power(float t) {
        float thump = (float) Math.sin(Dsp.TAU * 52 * t) * (float) Math.exp(-t / 0.09F) * 0.7F
                + noise.next() * (float) Math.exp(-t / 0.004F) * 0.35F;
        humPhase += 60.0 / RATE;
        humPhase -= Math.floor(humPhase);
        double p = Dsp.TAU * humPhase;
        float hum = (float) (Math.sin(p) + 0.5 * Math.sin(2 * p) + 0.35 * Math.sin(3 * p) + 0.15 * Math.sin(4 * p));
        float level = 0.035F * Dsp.ramp(t, 0.05F, 0.45F) * (1 - Dsp.ramp(t, END - 1, END)) * (1 + 0.15F * wobble.next());
        return thumpFilter.run(thump) + hum * level;
    }
}
