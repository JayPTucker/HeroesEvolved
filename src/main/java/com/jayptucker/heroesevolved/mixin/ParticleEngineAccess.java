package com.jayptucker.heroesevolved.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Queue;

/** Every live particle, by render type, so Wind Arms can blow the ones in their funnels ({@code WindArmsAir}). */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccess {
    @Accessor("particles") Map<ParticleRenderType, Queue<Particle>> heroesEvolved$particles();
}
