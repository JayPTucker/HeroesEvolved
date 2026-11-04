package com.jayptucker.heroesevolved.data;

/**
 * The Eclipse calendar. An Eclipse falls on every {@code intervalDays}-th day, at noon, and a
 * solar eclipse can only happen at a new moon, so the lunar cycle is stretched to the same length:
 * the moon is new on Eclipse day and full halfway between.
 *
 * <p>The interval is a server config value; the client receives it with the Eclipse state, so
 * both sides render the same moon.
 */
public final class EclipseCalendar {
    public static final int DEFAULT_INTERVAL_DAYS = 10;
    public static final long TICKS_PER_DAY = 24_000L;
    /** Mid-totality lands at noon, when the sun is highest. */
    public static final long PEAK_TIME_OF_DAY = 6_000L;
    /** Vanilla moon phase indices run 0 (full) to 4 (new) and back. */
    public static final int NEW_MOON = 4;

    private static volatile int intervalDays = DEFAULT_INTERVAL_DAYS;

    private EclipseCalendar() {
    }

    public static int intervalDays() {
        return intervalDays;
    }

    public static void setIntervalDays(int days) {
        intervalDays = Math.max(1, days);
    }

    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    public static long timeOfDay(long dayTime) {
        return Math.floorMod(dayTime, TICKS_PER_DAY);
    }

    /** Day 9, 19, 29... for a ten-day interval: the tenth day of every cycle. */
    public static boolean isEclipseDay(long day) {
        return day >= 0 && Math.floorMod(day + 1, intervalDays) == 0;
    }

    /** The first Eclipse day on or after {@code day}. */
    public static long nextEclipseDay(long day) {
        long cycleDay = Math.floorMod(day + 1, intervalDays);
        return cycleDay == 0 ? day : day + (intervalDays - cycleDay);
    }

    /** Time of day that first contact must begin for mid-totality to land at noon. */
    public static long firstContactTimeOfDay(int approachTicks, int totalityTicks) {
        return Math.max(0, PEAK_TIME_OF_DAY - approachTicks - totalityTicks / 2);
    }

    /** New moon on Eclipse day, waxing to full halfway through the cycle, then waning. */
    public static int moonPhase(long dayTime) {
        int days = intervalDays;
        long sinceEclipse = Math.floorMod(day(dayTime) + 1, days);
        return (NEW_MOON + Math.round(sinceEclipse * 8.0F / days)) % 8;
    }
}
