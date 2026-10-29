package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;

/**
 * Power Level 1 to 20, in five stages from the show: Manifesting (1-4), Controlled (5-9), Practiced
 * (10-14), Mastered (15-19) and Evolved (20, after the power's trial).
 *
 * <p>Ability code still works in <b>tiers</b> 1 to 5 (an action's unlock level, a skill node's level,
 * every damage formula). A tier is reached at Power Level 1, 3, 6, 10 and 15. The Mastery curve is
 * built so each of those levels needs exactly the Mastery the old five-level curve needed for that
 * tier, so existing players keep every ability they had: old Level 5 is now Level 15.</p>
 */
public final class PowerLevels {
    public static final int MAX = 20, EVOLVED = 20;
    private static final int[] TIER_START = {0, 1, 3, 6, 10, 15};

    private PowerLevels() {}

    /** The Power Level at which a tier (an action's or skill node's "level") is reached. */
    public static int levelForTier(int tier) {
        return TIER_START[Math.clamp(tier, 1, 5)];
    }

    /** The tier a Power Level has reached. */
    public static int tierFor(int level) {
        int tier = 1;
        for (int t = 2; t <= 5; t++) if (level >= TIER_START[t]) tier = t;
        return tier;
    }

    public enum Stage {
        MANIFESTING("Manifesting", 1), CONTROLLED("Controlled", 5), PRACTICED("Practiced", 10),
        MASTERED("Mastered", 15), EVOLVED("Evolved", 20);

        public final String title;
        public final int from;

        Stage(String title, int from) { this.title = title; this.from = from; }
    }

    public static Stage stage(int level) {
        Stage result = Stage.MANIFESTING;
        for (Stage stage : Stage.values()) if (level >= stage.from) result = stage;
        return result;
    }

    /** The highest Power Level this server allows (fewer than 20 if its tier cap is below 5). */
    public static int maxLevel() {
        int max = HeroesEvolvedConfig.COMMON.maximumLevel.get();
        return max >= 5 ? MAX : levelForTier(max);
    }

    /** Old-curve Mastery for a tier (tiers past the server's configured maximum are unreachable). */
    private static long tierMastery(int tier) {
        int max = HeroesEvolvedConfig.COMMON.maximumLevel.get();
        return tier <= max ? ProgressionCalculator.masteryRequiredForLevel(tier) : Long.MAX_VALUE / 4;
    }

    /** Total Mastery needed to stand at {@code level}. */
    public static long masteryFor(int level) {
        level = Math.clamp(level, 1, MAX);
        if (level >= 15) {
            long base = tierMastery(5);
            // Past Mastered, each level costs half again what the Mastered stretch did.
            long step = Math.max(1, (tierMastery(5) - tierMastery(4)) * 3 / 10);
            return base + step * (level - 15);
        }
        int tier = tierFor(level);
        int from = TIER_START[tier], to = TIER_START[tier + 1];
        long low = tierMastery(tier), high = tierMastery(tier + 1);
        return low + (high - low) * (level - from) / (to - from);
    }

    /** The level this much Mastery reaches, before the Evolved trial (so at most 19 without it). */
    public static int levelFor(long mastery, boolean evolved) {
        int level = 1;
        for (int candidate = 2; candidate <= MAX; candidate++) if (mastery >= masteryFor(candidate)) level = candidate;
        return evolved ? level : Math.min(level, EVOLVED - 1);
    }

    /** Skill points from levels alone: 3 at awakening, then 4 every 3 levels (21 at 15, 27 at 19, 28 Evolved). */
    public static int points(int level) {
        return 3 + 4 * (Math.clamp(level, 1, MAX) - 1) / 3;
    }
}
