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
 * client tick that forward flight stops or the selected speed returns to normal.</p>
 */
public final class FlightBoostWindSound
        extends AbstractTickableSoundInstance {
    private static final float FADE_IN_PER_TICK = 0.06F;
    private static final float FADE_OUT_PER_TICK = 0.08F;

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
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stop();
            return;
        }
        float speed = (float) Math.clamp(minecraft.player.getDeltaMovement().length() / 2.8, 0, 1);
        float targetVolume = boostActive ? .22F + speed * .43F : 0.0F;
        float fadeSpeed = boostActive
                ? FADE_IN_PER_TICK
                : FADE_OUT_PER_TICK;

        volume = Mth.approach(volume, targetVolume, fadeSpeed);
        pitch = Mth.approach(
                pitch,
                boostActive ? .82F + speed * .55F : .80F,
                0.025F
        );

        // The fade begins immediately on release, but stopping waits until
        // it becomes inaudible so there is no abrupt cutoff.
        if (!boostActive && volume <= 0.01F) {
            stop();
        }
    }
}
