package com.jayptucker.heroesevolved.ability;

/** Actual world-block reach for ranged attacks. Never pass these through AbilityRanges.ranged again.
 * Narrow shots reach farther than broad waves or sustained control; damage and blast radii stay independent. */
public final class ProjectileRanges {
    private ProjectileRanges() {}

    public static final double FIREBALL = 80, FIRE_SPEAR = 80, BLUE_LANCE = 96;
    public static final double QUAKE_BLAST = 40;
    public static final double ICE_STREAM = 24, ICE_BOLT = 48, CRYSTAL_LANCE = 64, ICE_SHARDS = 24;
    public static final double CHAIN_LIGHTNING = 64, LIVE_CURRENT = 24, BALL_LIGHTNING = 64;
    public static final double VIBE_PALM = 48, VIBE_LANCE = 72, VIBE_HEAVY = 40;
    public static final double WATER_DAGGERS = 40, WATER_WAVE = 32, TSUNAMI = 48;
    public static final double FORCE_PUSH = 24, HARD_FORCE_PUSH = 32;
    public static final double RADIATION_BEAM = 64, GAMMA_LANCE = 80;
    public static final double ARC_LANCE = 80, FOCUSED_SHRIEK = 80;
    public static final double KINETIC_SHARD = 48, KINETIC_SPEAR = 64, IRON_RAIN = 64;
}
