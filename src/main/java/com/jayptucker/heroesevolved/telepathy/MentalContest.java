package com.jayptucker.heroesevolved.telepathy;

/** Server-owned resistance rules. Correct fresh presses matter; key repeat and packet spam do not. */
public final class MentalContest {
    public static int gain(int attackerLevel, int defenderLevel) {
        return Math.clamp(22 + (defenderLevel - attackerLevel) * 2, 9, 40);
    }
    public static int windup(int tier) { return 80 - Math.clamp(tier, 1, 5) * 10; }
    public static int duration(int tier) { return 60 + Math.clamp(tier, 1, 5) * 12; }
    private MentalContest() {}
}
