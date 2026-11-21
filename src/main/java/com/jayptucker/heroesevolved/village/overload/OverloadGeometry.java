package com.jayptucker.heroesevolved.village.overload;

import com.jayptucker.heroesevolved.speed.TornadoShape;

/**
 * Every number an overload's storm and its drawing share, so the fire the player sees is where it burns, the funnel's
 * wall is where it lifts, and a warning ring fills exactly as the thing it warns of lands. {@code progress} is how much
 * of the countdown has run (0 to 1); {@code power} is how hard the storm is blowing right now ({@link #power}).
 */
public final class OverloadGeometry {
    private OverloadGeometry() {}

    // ------------------------------------------------------------------
    // Every storm
    // ------------------------------------------------------------------

    /** Ten seconds beside them, with one person, calms them. */
    public static final int CALM_TICKS = 200;
    /** Close enough to calm them; a telepath reaches them from this far if they can see them. */
    public static final double CALM_REACH = 4, MIND_REACH = 16;
    /** With nobody calming them, the calm slips back this fast (a quarter as fast as it came). */
    public static final float CALM_SLIP = 0.25F;
    /** The break's warning, how long the storm burns itself out afterwards, and how long a calmed storm takes to die away. */
    public static final int BREAK_WARNING = 60, BURNOUT = 400, DIE_AWAY = 60;
    /** Players this close see the bar and the storm. */
    public static final int WATCH = 160;
    /** Each blow takes ten seconds off (at most once a second). */
    public static final int HIT_PENALTY = 200;

    /** How hard it blows: a fifth at the start, all of it at the end, and half of that taken off by a full calm. */
    public static float power(float progress, float calm) {
        return (0.2F + 0.8F * clamp(progress)) * (1 - 0.5F * clamp(calm));
    }

    // ------------------------------------------------------------------
    // The wildfire
    // ------------------------------------------------------------------

    /** The front's radius: 5 blocks at first, 28 at the end. */
    public static float front(float progress) { return 5 + 23 * clamp(progress); }
    /** The flames of the front are this deep, half each side of its radius. */
    public static final float FRONT_DEPTH = 3;
    /** The Firestorm's wall reaches this far. */
    public static final float FIRESTORM = 36;
    /** How long an ember's ring glows before it lands, how long its spot fire burns, and how wide it is. */
    public static final int EMBER_WARNING = 30, EMBER_BURN = 100;
    public static final float EMBER_RADIUS = 1.25F;
    /** Embers land up to this far past the front. */
    public static final float EMBER_THROW = 10;
    /** Ticks between embers: every four seconds at first, every second and a half at full power. */
    public static int emberEvery(float power) { return Math.round(80 - 50 * clamp(power)); }
    /** The column of fire round them. */
    public static final float PILLAR_RADIUS = 1.6F;

    // ------------------------------------------------------------------
    // The supercell
    // ------------------------------------------------------------------

    /**
     * The funnel is the shared tornado ({@link TornadoShape}) drawn {@code scale} times its size: a Tempest's at first,
     * 1.6 times it at the end, and 2.2 times it for Touchdown.
     */
    public static float scale(float progress) { return 1 + 0.6F * clamp(progress); }
    public static final float TOUCHDOWN_SCALE = 2.2F;
    /** The ring the funnel stands on, before scaling (a Tempest's). */
    public static final float RING = 4;
    /** The wind drags things in from this far: 10 blocks at first, 24 at the end. */
    public static float pull(float progress) { return 10 + 14 * clamp(progress); }
    /** The funnel's wall's radius {@code h} blocks up, for a funnel this old and this big. */
    public static float wall(float h, float built, float scale) {
        float height = TornadoShape.height(built) * scale;
        return TornadoShape.radius(h / scale, height / scale, RING) * scale;
    }
    /** How tall it stands. */
    public static float height(float built, float scale) { return TornadoShape.height(built) * scale; }
    /** Inside this fraction of the wall's radius is the calm eye; the wall itself is this thick. */
    public static final float EYE = 0.7F, WALL_DEPTH = 3;
    /** They float this far up in the eye. */
    public static final double LIFT = 1.2;
    /** It wanders at a walk, toward points this far from the bell at most, and shows where it is going this far ahead. */
    public static final double WANDER_SPEED = 0.06, WANDER = 36;
    public static final int TRACK_AHEAD = 100;
    /** Faster than this, a runner goes through the wall before it can grab them. */
    public static final double OUTRUN = 0.9;
    /** A Tempest or a Cyclone this close holds it back. */
    public static final double UNWIND_REACH = 24;
    /** The deck of cloud over it. */
    public static final float DECK_RADIUS = 46, DECK_HEIGHT = 34;

    // ------------------------------------------------------------------
    // The blizzard
    // ------------------------------------------------------------------

    /** How far it reaches: 12 blocks at first, 48 at the end. */
    public static float reach(float progress) { return 12 + 36 * clamp(progress); }
    /** Warmth this close keeps the cold off. */
    public static final double WARM_REACH = 5;
    /** The spire of ice round them. */
    public static float spire(float progress) { return 2.5F + 6 * clamp(progress); }
    /** In the open, the cold gains this many frozen ticks a tick (on top of thawing's two): one at full power. */
    public static float chill(float power) { return 0.35F + 0.65F * clamp(power); }

    static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
}
