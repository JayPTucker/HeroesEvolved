package com.jayptucker.heroesevolved.client;

import net.minecraft.client.KeyMapping;

import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * Spots a key pressed twice in quick succession, read from the player's own key bindings once a client tick. Pressing
 * a different key of the set in between starts the count again. Fighting-style dodges and Flight's Barrel Roll use it.
 */
final class DoubleTaps {
    private static final long NEVER = Long.MIN_VALUE;
    private final int window;
    private final boolean[] wasDown;
    private final long[] lastPress;

    /** @param window the most ticks apart two presses can be and still count */
    DoubleTaps(int keys, int window) {
        this.window = window;
        wasDown = new boolean[keys];
        lastPress = new long[keys];
        Arrays.fill(lastPress, NEVER);
    }

    void reset() {
        Arrays.fill(wasDown, false);
        Arrays.fill(lastPress, NEVER);
    }

    /** Reads the keys and calls {@code tapped} with the index of each one just double-tapped. */
    void poll(KeyMapping[] keys, long now, IntConsumer tapped) {
        for (int i = 0; i < keys.length; i++) {
            boolean down = keys[i].isDown();
            if (down && !wasDown[i]) {
                if (lastPress[i] != NEVER && now - lastPress[i] <= window && now >= lastPress[i]) {
                    tapped.accept(i);
                    lastPress[i] = NEVER;
                } else {
                    lastPress[i] = now;
                }
                for (int j = 0; j < keys.length; j++) if (j != i) lastPress[j] = NEVER;
            }
            wasDown[i] = down;
        }
    }
}
