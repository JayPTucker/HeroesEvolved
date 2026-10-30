package com.jayptucker.heroesevolved.network;

/** The local player's Clarity (Precognition), as the server last sent it. */
public final class ClientClarity {
    private static volatile int clarity;

    private ClientClarity() {}

    static void set(int value) { clarity = Math.clamp(value, 0, 100); }

    public static int get() { return clarity; }

    public static void clear() { clarity = 0; }
}
