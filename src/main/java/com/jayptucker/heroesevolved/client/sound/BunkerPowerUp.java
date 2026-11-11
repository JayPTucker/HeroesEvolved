package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * The stasis bunker's main power coming back as the first sleeper steps out of a pod. The alarm has
 * just cut off; a heavy breaker slams shut somewhere behind the walls, the transformers take the load
 * with a deep hum that sags and then settles, and the tube ballasts buzz and stutter before they catch.
 * Then it all quietens to the background. The lights themselves click on one by one, from the server
 * (BunkerPower), as the power reaches them.
 */
public final class BunkerPowerUp extends PcmStream {
    private static final int RATE = 44100;
    private static final float END = 6.5F;

    private final Dsp.Noise noise = new Dsp.Noise(System.nanoTime() ^ 0x5EEDL);
    private final Dsp.Wander flicker = new Dsp.Wander(RATE, 9, 7), sway = new Dsp.Wander(RATE, 0.4F, 3);
    private final Dsp.LowPass slam = new Dsp.LowPass(RATE, 700), clank = new Dsp.LowPass(RATE, 5200);
    private final Dsp.Biquad buzzBand = Dsp.Biquad.bandPass(RATE, 1900, 1.2F);
    private final Dsp.Delay preDelay = new Dsp.Delay(RATE / 30);
    private final Dsp.Reverb concrete = new Dsp.Reverb(RATE, 0.9F, 2.4F, 0.25F);
    private final float[] wet = new float[2];
    private double humPhase, buzzPhase;
    private long frame;

    private BunkerPowerUp() {
        super(RATE, 2);
    }

    public static void play() {
        var level = Minecraft.getInstance().level;
        Minecraft.getInstance().getSoundManager().play(new SynthSound(
                ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "bunker_power"),
                "subtitles.heroesevolved.bunker_power", SoundSource.BLOCKS, BunkerPowerUp::new)
                .whileTrue(() -> Minecraft.getInstance().level == level));
    }

    @Override protected int render(float[] out, int frames) {
        int made = 0;
        for (; made < frames; made++, frame++) {
            float t = frame / (float) RATE;
            if (t >= END) break;
            float breaker = breaker(t), hum = hum(t), buzz = buzz(t);
            preDelay.write(breaker * 0.8F + hum * 0.5F + buzz * 0.6F);
            concrete.run(preDelay.read(RATE / 40), wet);
            float dry = breaker + hum + buzz * 0.8F;
            out[2 * made] = Dsp.soft(dry * 0.9F + buzz * 0.15F + wet[0] * 0.55F);
            out[2 * made + 1] = Dsp.soft(dry * 0.9F - buzz * 0.1F + wet[1] * 0.55F);
        }
        return made;
    }

    /** The main breaker: a low slam through the floor and the clatter of its contacts. */
    private float breaker(float t) {
        if (t > 1.2F) return 0;
        float body = (float) Math.sin(Dsp.TAU * 41 * t * (1 - 0.25F * t)) * (float) Math.exp(-t / 0.16F);
        float n = noise.next();
        float contacts = 0;
        for (float at : new float[] {0, 0.021F, 0.034F, 0.052F}) {
            float age = t - at;
            if (age >= 0) contacts += n * (float) Math.exp(-age / 0.006F) * (at == 0 ? 1 : 0.45F);
        }
        return slam.run(body * 0.9F + n * (float) Math.exp(-t / 0.03F) * 0.5F) + clank.run(contacts) * 0.45F;
    }

    /** The transformers under load: 60 cycles that sag as they take it, then settle and fade back. */
    private float hum(float t) {
        float hz = 60 - 2.4F * (float) Math.exp(-t / 0.5F) * Dsp.ramp(t, 0.05F, 0.15F);
        humPhase += hz / RATE;
        humPhase -= Math.floor(humPhase);
        double p = Dsp.TAU * humPhase;
        float wave = (float) (Math.sin(p) + 0.7 * Math.sin(2 * p) + 0.4 * Math.sin(3 * p) + 0.25 * Math.sin(4 * p) + 0.1 * Math.sin(6 * p));
        float level = 0.07F * Dsp.ramp(t, 0.06F, 0.9F) * (1 - 0.7F * Dsp.ramp(t, 1.6F, 3.5F)) * (1 - Dsp.ramp(t, 4.5F, END));
        return wave * level * (1 + 0.1F * sway.next());
    }

    /** The tube ballasts: a bright 120-cycle buzz that stutters while the tubes strike, then steadies. */
    private float buzz(float t) {
        buzzPhase += 120.0 / RATE;
        buzzPhase -= Math.floor(buzzPhase);
        float saw = (float) (2 * buzzPhase - 1);
        float rasp = buzzBand.run(saw * saw * saw + 0.15F * noise.next());
        float striking = 1 - Dsp.ramp(t, 0.9F, 1.6F);
        float stutter = flicker.next() > 0.1F ? 1 : 0.15F;
        float gate = striking * stutter + (1 - striking);
        float level = 0.05F * Dsp.ramp(t, 0.35F, 0.5F) * gate * (1 - 0.6F * Dsp.ramp(t, 1.8F, 3.5F)) * (1 - Dsp.ramp(t, 4.5F, END));
        return rasp * level;
    }
}
