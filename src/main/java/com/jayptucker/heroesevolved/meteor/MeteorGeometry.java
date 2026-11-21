package com.jayptucker.heroesevolved.meteor;

import net.minecraft.world.phys.Vec3;

/**
 * Every number a meteor and its drawing share: where it is in the sky at each moment of its fall, how big its blast is,
 * how long its warning ring takes to fill (exactly as long as the fall), and how far the answers reach. The server's
 * "looking at it" test and the client's drawn rock read the same {@link #position}.
 */
public final class MeteorGeometry {
    private MeteorGeometry() {}

    /** A falling star: thirty seconds' fall, a blast of 4, a crater of 3. */
    public static final int FALL = 600;
    public static final float BLAST = 4, CRATER = 3;
    /** A great meteor: two minutes' fall, a blast of 20, a crater of 9. */
    public static final int GREAT_FALL = 2400;
    public static final float GREAT_BLAST = 20, GREAT_CRATER = 9;
    /** How far off and how high each starts, from where it lands. */
    public static final double FAR = 170, HIGH = 190, GREAT_FAR = 420, GREAT_HIGH = 320;
    /** The burning rock's radius. */
    public static final float ROCK = 0.9F, GREAT_ROCK = 3.2F;

    /** Falling stars land this far from a player. */
    public static final double NEAREST = 24, FURTHEST = 96;

    /** The answers reach from this far off the ring's middle; a look within this angle counts as looking at it. */
    public static final double REACH = 64, LOOK_DEGREES = 15;
    /** Blocks a tick a telekinetic pushes it, and a flyer. */
    public static final double PUSH = 0.05, WIND_PUSH = 0.033;
    /** A sonic cracks it in this long; a quaker braces the ground in this long, within this far of the middle. */
    public static final int SHATTER_TICKS = 400, BRACE_TICKS = 100;
    public static final double BRACE_REACH = 8;
    /** A great meteor broken by a sonic falls as this many stars, landing within this far of its ring's middle. */
    public static final int SHARDS = 5;
    public static final double SHARD_SPREAD = 16;

    /** Where it starts in the sky, for one landing at {@code land} from {@code azimuth} (radians). */
    public static Vec3 start(Vec3 land, double azimuth, boolean great) {
        double far = great ? GREAT_FAR : FAR, high = great ? GREAT_HIGH : HIGH;
        return land.add(Math.cos(azimuth) * far, high, Math.sin(azimuth) * far);
    }

    /**
     * Where it is {@code fallen} of the way down (0 to 1): along the line from its start to where it lands, slow at first
     * and faster as it nears (the square of the time).
     */
    public static Vec3 position(Vec3 start, Vec3 land, float fallen) {
        float u = Math.max(0, Math.min(1, fallen));
        return start.lerp(land, u * u);
    }

    public static float blast(boolean great) { return great ? GREAT_BLAST : BLAST; }
    public static float crater(boolean great) { return great ? GREAT_CRATER : CRATER; }
    public static int fall(boolean great) { return great ? GREAT_FALL : FALL; }
    public static float rock(boolean great) { return great ? GREAT_ROCK : ROCK; }
}
