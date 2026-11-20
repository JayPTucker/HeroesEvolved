package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.function.Supplier;

/**
 * The Last Day's music (tools/generate-lastday-music.py): two stems of one loop, started together so they stay in step.
 * The evening's bed plays from when the doors open; the fight's layer runs silent under it and comes up over it when
 * Peter and Sylar meet ({@link #action}). Underground both fall away to a murmur, and they fade out for Noah's scene.
 * Vanilla's background music is kept out of the way while it plays.
 */
public final class LastDayMusic {
    /** Ticks to fade in, to bring the fight up, and to fade away. */
    private static final int FADE_IN = 80, ACTION_IN = 50, FADE_OUT = 100;
    private static Stem bed, run;
    private static boolean action, below;

    private LastDayMusic() {}

    /** The doors open on the evening: the bed fades in, the fight's layer waits silent under it. */
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (bed != null && mc.getSoundManager().isActive(bed) && !bed.leaving) return;
        stop();
        mc.getMusicManager().stopPlaying();
        action = below = false;
        bed = new Stem(ModSounds.LASTDAY_EVENING);
        run = new Stem(ModSounds.LASTDAY_RUN);
        // In the same tick, so the two loops start together and stay together.
        mc.getSoundManager().play(bed);
        mc.getSoundManager().play(run);
    }

    /** The fight: its layer comes up over the bed. */
    public static void action() { action = true; }

    /** Down in the safe house: everything far above and muffled. */
    public static void underground() { below = true; }

    /** Noah's scene, the pods, the end: it all fades away. */
    public static void exit() {
        if (bed != null) bed.leaving = true;
        if (run != null) run.leaving = true;
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (bed != null) mc.getSoundManager().stop(bed);
        if (run != null) mc.getSoundManager().stop(run);
        bed = run = null;
    }

    private static final class Stem extends AbstractTickableSoundInstance {
        final boolean fight;
        int age, actionAge = -1;
        boolean leaving;
        float fade = 1;

        Stem(Supplier<SoundEvent> sound) {
            super(sound.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            fight = sound == ModSounds.LASTDAY_RUN;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.001F;
        }

        @Override public boolean canStartSilent() { return true; }

        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !TimeIntroService.isCity(mc.level)) { stop(); return; }
            age++;
            if (leaving) fade -= 1F / FADE_OUT;
            if (fade <= 0) { stop(); return; }
            float in = smooth(age / (float) FADE_IN);
            float level = in;
            if (fight) {
                if (action && actionAge < 0) actionAge = 0;
                level *= actionAge < 0 ? 0 : smooth(++actionAge / (float) ACTION_IN);
            } else if (action) level *= 0.85F;
            if (below) level *= 0.3F;
            volume = Math.max(0.001F, level * fade);
            mc.getMusicManager().stopPlaying();
        }

        private static float smooth(float x) { x = Math.min(1, Math.max(0, x)); return x * x * (3 - 2 * x); }
    }
}
