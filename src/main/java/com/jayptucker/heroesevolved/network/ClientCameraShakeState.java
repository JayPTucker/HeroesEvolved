package com.jayptucker.heroesevolved.network;

import net.minecraft.client.Minecraft;

/** Client-side camera shake timer. The strongest active shake wins. */
public final class ClientCameraShakeState {
    private static final float MAXIMUM_INTENSITY = 4.0F;

    private static float intensity;
    private static long startGameTime;
    private static long endGameTime = -1L;

    private ClientCameraShakeState() {
    }

    public static void start(float newIntensity, int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || durationTicks <= 0
                || newIntensity <= 0.0F) {
            return;
        }

        long now = minecraft.level.getGameTime();
        // A weaker tremor arriving mid-earthquake must not cut it short.
        if (getStrength(now, 0.0F) >= newIntensity) {
            endGameTime = Math.max(endGameTime, now + durationTicks);
            return;
        }

        intensity = Math.min(newIntensity, MAXIMUM_INTENSITY);
        startGameTime = now;
        endGameTime = now + durationTicks;
    }

    /**
     * Called when leaving a world. The timer stores absolute game times, so
     * a shake left over from a world with a later clock would otherwise keep
     * running in the next world until that world's clock caught up.
     */
    public static void reset() {
        intensity = 0.0F;
        startGameTime = 0L;
        endGameTime = -1L;
    }

    /** Current shake strength, easing out across the shake's duration. */
    public static float getStrength(long gameTime, float partialTick) {
        // A clock earlier than the shake's start means a different world.
        if (gameTime < startGameTime) {
            reset();
        }
        if (endGameTime <= startGameTime) {
            return 0.0F;
        }

        double now = gameTime + partialTick;
        if (now >= endGameTime) {
            return 0.0F;
        }

        double remaining = (endGameTime - now) / (endGameTime - startGameTime);
        return (float) (intensity * Math.min(1.0D, remaining * 1.5D));
    }
}
