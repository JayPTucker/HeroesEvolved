package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.ClientTimeFlow;
import com.jayptucker.heroesevolved.client.effects.PowerParticle;
import com.jayptucker.heroesevolved.client.effects.TimedParticle;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * A particle's own clock: in slowed time it ticks once every few client ticks and draws the time between with a slowed
 * partial tick, so smoke, splashes, sparks and dust drift at the field's rate; stopped, it hangs still. Our own power
 * particles keep real time (their effects are already timed to the power).
 */
@Mixin(Particle.class)
public abstract class TemporalParticleMixin implements TimedParticle {
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    /** Starts due, so a particle's first tick (an explosion's first puff, a splash) happens the moment it appears. */
    @Unique private double heroes$progress = 1;
    @Unique private float heroes$rate = 1;

    @Override
    public boolean heroes$advance() {
        float rate = (Object) this instanceof PowerParticle ? 1 : ClientTimeFlow.rate(x, y, z);
        heroes$rate = rate;
        if (rate >= 1) {
            heroes$progress = 0;
            return true;
        }
        heroes$progress += rate;
        if (heroes$progress < 1) return false;
        heroes$progress -= 1;
        return true;
    }

    @Override
    public float heroes$partial(float partial) {
        if (heroes$rate >= 1) return partial;
        // Past 1 it carries on along the last step, which is where the next real tick will start from.
        return (float) Math.min(1 + heroes$rate, heroes$progress + partial * heroes$rate);
    }
}
