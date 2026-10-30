package com.jayptucker.heroesevolved.network;

/** The local player's Momentum (Superspeed), as the server last sent it. */
public final class ClientMomentum {
    private static volatile int momentum;

    private ClientMomentum() {}

    static void set(int value) { momentum = Math.clamp(value, 0, 100); }

    public static int get() { return momentum; }

    public static void clear() { momentum = 0; }
}
