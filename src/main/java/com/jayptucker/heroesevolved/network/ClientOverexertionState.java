package com.jayptucker.heroesevolved.network;

import net.minecraft.client.Minecraft;

public final class ClientOverexertionState {
    private static long expirationGameTime = -1L;

    private ClientOverexertionState() {
    }

    public static void start(int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || durationTicks <= 0) {
            return;
        }

        long newExpirationTime =
                minecraft.level.getGameTime() + durationTicks;

        // Repeated overexertion extends the warning rather than shortening it.
        expirationGameTime = Math.max(
                expirationGameTime,
                newExpirationTime
        );
    }

    public static float getIntensity() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return 0.0F;
        }

        long remainingTicks =
                expirationGameTime - minecraft.level.getGameTime();

        if (remainingTicks <= 0) {
            return 0.0F;
        }

        // The border fades out over its final second.
        return Math.min(1.0F, remainingTicks / 20.0F);
    }
}