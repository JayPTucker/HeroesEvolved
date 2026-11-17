package com.jayptucker.heroesevolved.network;

/** The local player's Superspeed state, as the server last sent it. */
public final class ClientMomentum {
    private static volatile MomentumPayload state = MomentumPayload.INACTIVE;

    private ClientMomentum() {}

    static void set(MomentumPayload payload) { state = payload; }

    public static int get() { return Math.clamp(state.momentum(), 0, 100); }
    public static int speedStep() { return state.speedStep(); }
    public static int maximumSpeedStep() { return state.maximumSpeedStep(); }

    public static void clear() { state = MomentumPayload.INACTIVE; }
}
