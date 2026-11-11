package com.jayptucker.heroesevolved.network;

/** The local player's Overrun, as the server last sent it (see {@link OverrunStatusPayload}). */
public final class ClientOverrun {
    private static volatile int state, value;
    private static volatile long received;

    private ClientOverrun() {}

    static void set(int newState, int newValue) {
        state = newState;
        value = newValue;
        received = System.currentTimeMillis();
    }

    public static int state() { return state; }

    public static int value() { return value; }

    /** Seconds left on a countdown, counting down between the server's once-a-second updates. */
    public static int secondsLeft() {
        long passed = (System.currentTimeMillis() - received) / 1000;
        return (int) Math.max(0, value - passed);
    }

    public static void clear() { state = OverrunStatusPayload.HIDDEN; value = 0; }
}
