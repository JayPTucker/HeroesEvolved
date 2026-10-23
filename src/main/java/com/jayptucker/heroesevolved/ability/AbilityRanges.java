package com.jayptucker.heroesevolved.ability;

/** Shared reach multiplier for aimed powers, separate from area-of-effect radii. */
public final class AbilityRanges {
    private AbilityRanges() {}

    public static double ranged(double baseDistance) { return baseDistance * 2.0D; }
    public static int ranged(int baseDistance) { return Math.multiplyExact(baseDistance, 2); }
}
