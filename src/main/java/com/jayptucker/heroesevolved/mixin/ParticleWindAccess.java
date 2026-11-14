package com.jayptucker.heroesevolved.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A particle's velocity and age, so Wind Arms can blow smoke along and burn it out faster ({@code WindArmsAir}). */
@Mixin(Particle.class)
public interface ParticleWindAccess {
    @Accessor("xd") double heroesEvolved$xd();
    @Accessor("yd") double heroesEvolved$yd();
    @Accessor("zd") double heroesEvolved$zd();
    @Accessor("age") int heroesEvolved$age();
}
