package com.jayptucker.heroesevolved.dampener;

/**
 * The Ability Dampener's numbers, shared by the server (the field, the gun, the rounds) and {@code DampenerArt}, so
 * the dome the player sees is exactly the field that stops them.
 *
 * <p>Everything here runs on Forge Energy (RF): the projector burns it every tick it stands, and the Nullifier stores
 * it in its battery, a round at a time.
 */
public final class DampenerGeometry {
    // ------------------------------------------------------------------
    // The projector
    // ------------------------------------------------------------------

    /** The radii a right-click cycles through, in blocks. A new projector starts at the second. */
    public static final int[] RADII = {4, 8, 12, 16};
    public static final int DEFAULT_RADIUS = 8;
    /** Forge Energy burned each tick per block of radius: 24, 48, 72 and 96 FE a tick. */
    public static final int UPKEEP_PER_BLOCK = 6;
    /** The projector's battery, and the most it takes in or gives out in one transfer. */
    public static final int CAPACITY = 500_000, MAX_RECEIVE = 20_000, MAX_EXTRACT = 24_000;
    /** Ticks a projector takes to spin up from off to a full field. The dome grows over them; powers still work. */
    public static final int SPIN_UP = 20;
    /** The field's set piece is re-sent this often and lasts {@link #FIELD_PIECE} ticks, so copies hand over. */
    public static final int FIELD_RESEND = 10, FIELD_PIECE = 12;

    // ------------------------------------------------------------------
    // The Nullifier and its rounds
    // ------------------------------------------------------------------

    /** Forge Energy one dampener round costs to make. */
    public static final int FE_PER_ROUND = 4_000;
    /** Rounds the Nullifier's battery holds, not counting the ones in its magazine. */
    public static final int BATTERY_ROUNDS = 24;
    /** The magazine. */
    public static final int MAGAZINE = 6;
    /** The most Forge Energy the battery takes in one transfer (a charger, a cable): two rounds. */
    public static final int GUN_MAX_RECEIVE = 8_000;
    /** How long a round dampens a player, and anything else. */
    public static final int ROUND_PLAYER_TICKS = 160, ROUND_MOB_TICKS = 200;
    /** A dampened body's mark is re-sent this often and lasts {@link #MARK_PIECE} ticks. */
    public static final int MARK_RESEND = 10, MARK_PIECE = 12;
    public static final double RANGE = 48;

    private DampenerGeometry() {}

    /** Forge Energy a projector burns each tick at {@code radius}. */
    public static int upkeep(int radius) { return radius * UPKEEP_PER_BLOCK; }

    /** The radius after {@code radius} in {@link #RADII}, wrapping round. */
    public static int next(int radius) {
        for (int i = 0; i < RADII.length; i++) if (RADII[i] == radius) return RADII[(i + 1) % RADII.length];
        return DEFAULT_RADIUS;
    }

    /** A saved radius, clamped to one the projector offers. */
    public static int valid(int radius) {
        for (int r : RADII) if (r == radius) return r;
        return DEFAULT_RADIUS;
    }
}
