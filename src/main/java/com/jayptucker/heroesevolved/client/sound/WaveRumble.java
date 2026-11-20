package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.util.Random;

/**
 * The Last Day's big wave, coming ({@code client/effects/intro/WaveApproach}): a rumble from under the street that builds
 * for as long as it has until the wave arrives. Felt first, a sub-bass swell and the low grind of the ground; then the roar
 * of air and stone on top, and debris rattling, loudest as it hits, where the crash of the glass and the ringing take
 * over. Made on the fly (no recording), on the master channel.
 */
public final class WaveRumble extends PcmStream {
    private static final int RATE = 44100;
    private final int build, total;
    private final Dsp.Noise noise = new Dsp.Noise(0x5EED0F0DE55AL);
    private final Dsp.Wander sway = new Dsp.Wander(RATE, 2.5F, 3);
    private final Dsp.LowPass ground = new Dsp.LowPass(RATE, 70), ground2 = new Dsp.LowPass(RATE, 70);
    private final Dsp.Biquad roar = Dsp.Biquad.bandPass(RATE, 320, 0.6F), hiss = Dsp.Biquad.bandPass(RATE, 1100, 0.8F),
            rattle = Dsp.Biquad.bandPass(RATE, 2400, 2.5F);
    private final Random random = new Random(41);
    private double phase;
    private float click;
    private int at;

    private WaveRumble(float seconds) {
        super(RATE, 2);
        build = Math.max(RATE / 2, (int) (seconds * RATE));
        total = build + (int) (0.7F * RATE);
    }

    /** Starts the rumble, arriving {@code seconds} from now. */
    public static SynthSound play(float seconds) {
        SynthSound sound = new SynthSound(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "lastday_rumble"),
                "subtitles.heroesevolved.lastday_rumble", SoundSource.MASTER, () -> new WaveRumble(seconds));
        sound.set(1, 1);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    @Override protected int render(float[] out, int frames) {
        for (int i = 0; i < frames; i++) {
            if (at >= total) return i;
            // How near it is, 0 to 1 at the moment it arrives; a moment's tail past that in case it lands a tick late.
            float p = Math.min(1, at / (float) build);
            float tail = at <= build ? 1 : 1 - (at - build) / (float) (total - build);
            // The swell under everything: deep, rising in pitch as it nears, wavering.
            phase += (32 + 16 * p) / RATE;
            float sub = (float) Math.sin(phase * Dsp.TAU) * (0.7F + 0.3F * sway.next());
            float n = noise.next();
            float low = ground2.run(ground.run(n)) * 6;
            // The roar of air and stone on top, coming in late and fast.
            float air = roar.run(n) * 1.4F + hiss.run(noise.next()) * 0.5F * p;
            // Debris: clicks and rattles, more of them as it nears.
            if (random.nextFloat() < 0.0009F * p * p) click = 0.6F + 0.4F * random.nextFloat();
            click *= 0.9965F;
            float debris = rattle.run(click * noise.next()) * 1.5F;
            float level = 0.05F + 0.95F * p * p;
            float mix = (sub * 0.55F + low * 0.6F) * level + air * p * p * p * 0.9F + debris * p;
            float fade = Dsp.ramp(at, 0, RATE * 0.25F) * tail;
            float sample = Dsp.soft(mix * fade * 0.9F);
            out[2 * i] = sample;
            out[2 * i + 1] = sample;
            at++;
        }
        return frames;
    }
}
