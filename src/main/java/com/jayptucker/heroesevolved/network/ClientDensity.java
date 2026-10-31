package com.jayptucker.heroesevolved.network;

/** The local player's Density dial and Breath, as the server last sent them. */
public final class ClientDensity {
    private static volatile int density, breath = -1;

    private ClientDensity() {}

    static void set(int dial, int air) { density = Math.clamp(dial, -100, 100); breath = Math.clamp(air, -1, 100); }

    public static int get() { return density; }

    /** Breath left underground, or -1 above ground. */
    public static int breath() { return breath; }

    public static boolean sunk() { return breath >= 0; }

    public static void clear() { density = 0; breath = -1; }
}
