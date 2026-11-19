package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * The waking's music (story_wake_music, a seamless loop with its stinger swells inside it). It fades in when the server
 * starts it (once the player is out of their pod and the alarm is over) and loops until the server says they have left the
 * bunker, then fades away. Stops by itself if the player leaves the Overworld (the server starts it again if they come
 * back inside). Vanilla's background music is kept out of the way while it plays.
 */
public final class BunkerMusic {
    /** Ticks to fade in and to fade away. */
    private static final int FADE_IN = 80, FADE_OUT = 100;
    private static Loop playing;

    private BunkerMusic() {}

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (playing != null && mc.getSoundManager().isActive(playing) && !playing.leaving) return;
        if (playing != null) mc.getSoundManager().stop(playing);
        mc.getMusicManager().stopPlaying();
        playing = new Loop();
        mc.getSoundManager().play(playing);
    }

    /** Out of the bunker: the music fades away. */
    public static void exit() {
        if (playing != null) playing.leaving = true;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        int age;
        boolean leaving;
        float fade = 1;

        Loop() {
            super(ModSounds.STORY_WAKE_MUSIC.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.001F;
        }

        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
                end();
                return;
            }
            age++;
            if (leaving) fade -= 1F / FADE_OUT;
            if (fade <= 0) {
                end();
                return;
            }
            // Eased in, so it rises out of the quiet the alarm leaves rather than arriving.
            float in = Math.min(1, age / (float) FADE_IN);
            volume = Math.max(0.001F, in * in * (3 - 2 * in) * fade);
            mc.getMusicManager().stopPlaying();
        }

        private void end() {
            stop();
            if (playing == this) playing = null;
        }
    }
}
