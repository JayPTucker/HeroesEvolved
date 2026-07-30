package com.jayptucker.heroesevolved.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A local, controllable wind loop for the player using Flight Boost.
 *
 * <p>Unlike a server one-shot sound, this instance can begin fading on the
 * exact client tick that the player releases the boost keys.</p>
 */
public final class FlightBoostWindSound
        extends AbstractTickableSoundInstance {
    private static final float TARGET_VOLUME = 0.60F;
    private static final float FADE_IN_PER_TICK = 0.12F;
    private static final float FADE_OUT_PER_TICK = 0.10F;

    private static FlightBoostWindSound activeSound;

    private boolean boostActive;

    private FlightBoostWindSound() {
        super(
                SoundEvents.ELYTRA_FLYING,
                SoundSource.PLAYERS,
                RandomSource.create()
        );

        looping = true;
        relative = true;
        volume = 0.01F;
        pitch = 0.90F;
    }

    public static void update(boolean boostActive) {
        if (boostActive && (activeSound == null || activeSound.isStopped())) {
            activeSound = new FlightBoostWindSound();
            Minecraft.getInstance().getSoundManager().play(activeSound);
        }

        if (activeSound != null) {
            activeSound.boostActive = boostActive;

            if (activeSound.isStopped()) {
                activeSound = null;
            }
        }
    }

    @Override
    public void tick() {
        float targetVolume = boostActive ? TARGET_VOLUME : 0.0F;
        float fadeSpeed = boostActive
                ? FADE_IN_PER_TICK
                : FADE_OUT_PER_TICK;

        volume = Mth.approach(volume, targetVolume, fadeSpeed);
        pitch = Mth.approach(
                pitch,
                boostActive ? 1.15F : 0.90F,
                0.04F
        );

        // The fade begins immediately on release, but stopping waits until
        // it becomes inaudible so there is no abrupt cutoff.
        if (!boostActive && volume <= 0.01F) {
            stop();
        }
    }
}
