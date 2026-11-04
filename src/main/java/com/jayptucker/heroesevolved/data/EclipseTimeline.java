package com.jayptucker.heroesevolved.data;

import net.minecraft.util.Mth;

/**
 * One Eclipse, measured in game ticks from first contact. Shared by the server (phases,
 * suppression, rolls) and the client (moon position, shadow), so both agree on every frame.
 */
public record EclipseTimeline(long startGameTime, int approachTicks, int totalityTicks, int departureTicks) {
    /** A sentinel start, since skipping to totality in a young world can start before tick 0. */
    private static final long UNSCHEDULED = Long.MIN_VALUE;
    public static final EclipseTimeline NONE = new EclipseTimeline(UNSCHEDULED, 0, 0, 0);

    public enum Phase { NONE, APPROACH, TOTALITY, DEPARTURE }

    /** Ticks over which the umbra sweeps in (or out) at the edges of totality. */
    public static final float UMBRA_SWEEP_TICKS = 60;

    public boolean scheduled() {
        return startGameTime != UNSCHEDULED;
    }

    public long totalityStart() {
        return startGameTime + approachTicks;
    }

    public long totalityEnd() {
        return totalityStart() + totalityTicks;
    }

    public long endGameTime() {
        return totalityEnd() + departureTicks;
    }

    public Phase phaseAt(double time) {
        if (!scheduled() || time < startGameTime || time >= endGameTime()) return Phase.NONE;
        if (time < totalityStart()) return Phase.APPROACH;
        if (time < totalityEnd()) return Phase.TOTALITY;
        return Phase.DEPARTURE;
    }

    /**
     * Signed moon offset in sun-radii: -1 at first contact, 0 at mid-totality, +1 at last contact.
     * The moon covers the whole sun for the length of totality, drifting only slightly.
     */
    public float moonTravel(double time) {
        Phase phase = phaseAt(time);
        return switch (phase) {
            case NONE -> time < startGameTime ? -1 : 1;
            case APPROACH -> -(1 - (float) ((time - startGameTime) / approachTicks)) * (1 - TOTALITY_DRIFT) - TOTALITY_DRIFT;
            case TOTALITY -> (float) Mth.lerp((time - totalityStart()) / totalityTicks, -TOTALITY_DRIFT, TOTALITY_DRIFT);
            case DEPARTURE -> (float) ((time - totalityEnd()) / departureTicks) * (1 - TOTALITY_DRIFT) + TOTALITY_DRIFT;
        };
    }

    /** How much of the sun is hidden, 0 (none) to 1 (totality). */
    public float coverage(double time) {
        Phase phase = phaseAt(time);
        if (phase == Phase.NONE) return 0;
        if (phase == Phase.TOTALITY) return 1;
        return Mth.clamp(1 - (Math.abs(moonTravel(time)) - TOTALITY_DRIFT) / (1 - TOTALITY_DRIFT), 0, 1);
    }

    /**
     * Strength of the shadow on the ground, 0 to 1. Daylight barely changes until the last sliver
     * of sun, then the umbra rushes in over a few seconds, the way a real totality arrives.
     */
    public float darkness(double time) {
        Phase phase = phaseAt(time);
        if (phase == Phase.NONE) return 0;
        float partial = (float) Math.pow(coverage(time), 4) * PARTIAL_SHADOW;
        if (phase != Phase.TOTALITY) return partial;
        double fromEdge = Math.min(time - totalityStart(), totalityEnd() - time);
        float sweep = Mth.clamp((float) (fromEdge / UMBRA_SWEEP_TICKS), 0, 1);
        return Mth.lerp(sweep * sweep * (3 - 2 * sweep), PARTIAL_SHADOW, 1);
    }

    /** 0..1 strength of the "diamond ring" flare in the first and last seconds of totality. */
    public float diamondRing(double time) {
        if (phaseAt(time) != Phase.TOTALITY) return 0;
        double fromEdge = Math.min(time - totalityStart(), totalityEnd() - time);
        return Mth.clamp(1 - (float) (fromEdge / UMBRA_SWEEP_TICKS), 0, 1);
    }

    /** Fraction of the moon's offset left during totality, so it visibly drifts but never uncovers. */
    private static final float TOTALITY_DRIFT = 0.04F;
    private static final float PARTIAL_SHADOW = 0.45F;
}
