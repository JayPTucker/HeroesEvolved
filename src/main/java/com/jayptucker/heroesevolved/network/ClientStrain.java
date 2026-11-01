package com.jayptucker.heroesevolved.network;

/** The local player's Strain (Quake) and whether her arms are fractured, as the server last sent them. */
public final class ClientStrain {
    private static volatile int strain;
    private static volatile boolean fractured;

    private ClientStrain() {}

    static void set(int value, boolean broken) { strain = Math.clamp(value, 0, 100); fractured = broken; }

    public static int get() { return strain; }

    public static boolean fractured() { return fractured; }

    public static void clear() { strain = 0; fractured = false; }
}
