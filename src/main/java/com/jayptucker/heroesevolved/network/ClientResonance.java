package com.jayptucker.heroesevolved.network;

/** The local player's Resonance (Sonic Resonance), as the server last sent it. */
public final class ClientResonance {
    private static volatile int resonance;

    private ClientResonance() {}

    static void set(int value) { resonance = Math.clamp(value, 0, 100); }

    public static int get() { return resonance; }

    public static void clear() { resonance = 0; }
}
