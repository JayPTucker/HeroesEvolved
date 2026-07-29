package com.jayptucker.heroesevolved.particles;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Server-safe particle type registrations. Client rendering providers belong
 * in {@link ModParticleProviders} so a dedicated server never loads classes
 * from the Minecraft client package.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, HeroesEvolved.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            WHITE_CONTRAIL_SMOKE = PARTICLES.register(
                    "white_contrail_smoke",
                    () -> new SimpleParticleType(false)
            );

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
