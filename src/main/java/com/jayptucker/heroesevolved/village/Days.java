package com.jayptucker.heroesevolved.village;

import net.minecraft.server.level.ServerLevel;

/**
 * The calendar every "day" in village and world events is counted on: the overworld's time of day, the clock the days
 * players see (village memories, the Eclipse) are numbered by, and the one sleeping moves on. A crater "heals over five
 * days" means five of these, however many of their nights were slept through. Durations that pace play (an operation's
 * rest, a fuse) stay in game ticks instead.
 */
public final class Days {
    public static final long DAY = 24_000;

    private Days() {}

    /** Now, on the calendar. */
    public static long clock(ServerLevel level) { return level.getServer().overworld().getDayTime(); }

    /**
     * Days since {@code since} on the calendar; never negative (an admin who sets the time back makes it now). Fractional.
     */
    public static float since(ServerLevel level, long since) { return Math.max(0, clock(level) - since) / (float) DAY; }
}
