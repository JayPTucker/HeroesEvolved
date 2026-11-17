package com.jayptucker.heroesevolved.network;

/**
 * The client's copy of its finisher state (no client classes, so the payloads can name it on a server): which power's
 * finisher this player has, whether it's on, PvP, its reach, and the finisher playing now (for the camera).
 */
public final class ClientFinisher {
    private static final FinisherStatePayload NONE = new FinisherStatePayload("", true, false, 0);
    private static FinisherStatePayload state = NONE;
    private static int runTarget = -1, runTicks;
    private static boolean runChanged;

    private ClientFinisher() {}

    public static void set(FinisherStatePayload payload) { state = payload; }

    public static FinisherStatePayload state() { return state; }

    public static boolean has() { return !state.power().isEmpty(); }

    static void run(FinisherRunPayload payload) {
        runTarget = payload.ticks() > 0 ? payload.target() : -1;
        runTicks = payload.ticks();
        runChanged = true;
    }

    /** The foe of the finisher playing now, or -1. */
    public static int runTarget() { return runTarget; }

    public static int runTicks() { return runTicks; }

    /** True once after each start or end, so the camera can react. */
    public static boolean takeChange() {
        boolean changed = runChanged;
        runChanged = false;
        return changed;
    }

    /** Forgets a finisher whose end never arrived, so the player is never left held in place. */
    public static void endRun() {
        runTarget = -1;
        runTicks = 0;
        runChanged = true;
    }

    public static void clear() {
        state = NONE;
        runTarget = -1;
        runTicks = 0;
        runChanged = true;
    }
}
