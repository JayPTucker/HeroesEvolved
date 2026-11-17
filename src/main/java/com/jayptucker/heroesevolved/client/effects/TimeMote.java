package com.jayptucker.heroesevolved.client.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A chip of the ground (the block's own texture) lifted into slowed time: dust rising round whoever bent it, and what a
 * runner's feet kick up. It is an ordinary particle, so the particle clock ({@link TimedParticle}) slows it with the
 * world: in slowed time it drifts and hangs, stopped it holds still, and when time runs again it falls.
 */
public final class TimeMote extends TerrainParticle {
    private final float size;
    private final boolean ambient;
    private boolean settled;

    TimeMote(ClientLevel level, Vec3 at, Vec3 velocity, BlockState state, BlockPos pos, int life, float gravity, float scale, boolean ambient) {
        super(level, at.x, at.y, at.z, 0, 0, 0, state, pos);
        setParticleSpeed(velocity.x, velocity.y, velocity.z);
        this.gravity = gravity;
        this.friction = 0.96F;
        this.lifetime = life;
        this.quadSize *= scale;
        this.size = quadSize;
        this.ambient = ambient;
    }

    /** Grows in over a few ticks and shrinks away at the end, so nothing pops. */
    @Override
    public float getQuadSize(float partial) {
        float age = this.age + partial;
        float in = Mth.clamp(age / 3F, 0, 1), out = Mth.clamp((lifetime - age) / Math.max(1, lifetime * 0.3F), 0, 1);
        return size * in * out;
    }

    boolean ambient() { return ambient; }

    /** Time runs again: whatever hung in the air drops. */
    void settle() {
        if (settled) return;
        settled = true;
        gravity = Math.max(gravity, 0.6F);
        lifetime = Math.min(lifetime, age + 30);
    }
}
