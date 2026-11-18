package com.jayptucker.heroesevolved.network;

/** The local earthbender's Root, Horse Stance and Breath, as the server last sent them. */
public final class ClientDensity {
    private static volatile int root, breath = -1;
    private static volatile boolean stance;

    private ClientDensity() {}

    static void set(int rooted, boolean held, int air) { root = Math.clamp(rooted, 0, 100); stance = held; breath = Math.clamp(air, -1, 100); }

    /** Root, 0 to 100. */
    public static int root() { return root; }

    public static boolean stance() { return stance; }

    /** Breath left underground, or -1 above ground. */
    public static int breath() { return breath; }

    public static boolean sunk() { return breath >= 0; }

    public static void clear() { root = 0; stance = false; breath = -1; }
}
