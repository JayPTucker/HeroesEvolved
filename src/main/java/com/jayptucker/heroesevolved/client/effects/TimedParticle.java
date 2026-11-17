package com.jayptucker.heroesevolved.client.effects;

/**
 * Every particle keeps a clock of its own ({@code mixin/TemporalParticleMixin}), so particles in slowed time drift at the
 * field's rate and hang still when time stops, the way the mob clock slows creatures ({@code ClientMobTime}).
 */
public interface TimedParticle {
    /** Moves the clock on one client tick; true when the particle should really tick now. */
    boolean heroes$advance();

    /** The partial tick to draw with: how far the particle's own clock is between its last two real ticks. */
    float heroes$partial(float partial);
}
