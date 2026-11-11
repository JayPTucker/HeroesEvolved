package com.jayptucker.heroesevolved.speed;

/**
 * The shape of Cyclone's tornado, shared by the server's wind and the client's drawing so that what is
 * carried rides the wall you see. The funnel stands on a slim foot inside the runner's ring, pinches to a neck
 * a few blocks up, then flares out to a wide top.
 */
public final class TornadoShape {
    /** Full height, the neck's radius, the top's radius; ticks to grow to full height and to collapse. */
    public static final float HEIGHT = 26, NECK = 2.6F, TOP = 11, GROW = 30, COLLAPSE = 24;
    /** Where the neck is, as a fraction of the height; the foot's radius as a fraction of the ring's. */
    private static final float WAIST = 0.12F, FOOT = 0.52F;

    private TornadoShape() {}

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }

    /**
     * The funnel wall's radius {@code h} blocks above the ground, for a funnel {@code height} tall on a ring of
     * {@code ring}: a slim foot inside the runner's ring, a neck just above it, then a flare that widens as the
     * funnel grows taller.
     */
    public static float radius(float h, float height, float ring) {
        float u = clamp(h / Math.max(1, height), 0, 1), foot = Math.max(NECK, ring * FOOT);
        if (u < WAIST) {
            float k = u / WAIST;
            return foot + (NECK - foot) * k * (2 - k);
        }
        float top = NECK + (TOP - NECK) * clamp(height / HEIGHT, 0.25F, 1);
        return NECK + (top - NECK) * (float) Math.pow((u - WAIST) / (1 - WAIST), 1.7);
    }

    /** How tall the funnel stands {@code age} ticks after it began to rise (smoothly from 3 blocks to full). */
    public static float height(float age) {
        float k = clamp(age / GROW, 0, 1);
        return 3 + (HEIGHT - 3) * k * k * (3 - 2 * k);
    }

    /** How far up the funnel lifts what it carries, {@code age} ticks after it began to rise. */
    public static float lift(float age) {
        return 3 + 9 * clamp(age / (GROW * 2), 0, 1);
    }
}
