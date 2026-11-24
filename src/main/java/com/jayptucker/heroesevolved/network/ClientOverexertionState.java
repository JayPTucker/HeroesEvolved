package com.jayptucker.heroesevolved.network;

import net.minecraft.client.Minecraft;

public final class ClientOverexertionState {
    private static long expirationGameTime = -1L;
    private static long startedAt = -1000;

    private ClientOverexertionState() {
    }

    public static void start(int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || durationTicks <= 0) {
            return;
        }

        long newExpirationTime =
                minecraft.level.getGameTime() + durationTicks;
        if (expirationGameTime <= minecraft.level.getGameTime()) startedAt = minecraft.level.getGameTime();

        // Repeated overexertion extends the warning rather than shortening it.
        expirationGameTime = Math.max(
                expirationGameTime,
                newExpirationTime
        );
    }

    /** Clears the border timer when leaving a world; see ClientCameraShakeState. */
    public static void reset() {
        expirationGameTime = -1L;
        startedAt = -1000;
    }

    public static float getIntensity() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return 0.0F;
        }

        long remainingTicks =
                expirationGameTime - minecraft.level.getGameTime();

        // Overexertion lasts seconds; anything longer is left over from a
        // world whose clock ran ahead of this one.
        if (remainingTicks > 20L * 60L * 10L) {
            reset();
            return 0.0F;
        }

        if (remainingTicks <= 0) {
            return 0.0F;
        }

        // The border fades out over its final second.
        return Math.min(1.0F, remainingTicks / 30.0F)
                * Math.clamp((minecraft.level.getGameTime() - startedAt) / 6F, 0, 1);
    }
}
