package com.jayptucker.heroesevolved.particles;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, HeroesEvolved.MOD_ID);

    // A simple particle type needs no extra data in its network packet.
    public static final Supplier<SimpleParticleType> CONTRAIL =
            PARTICLE_TYPES.register(
                    "contrail",
                    () -> new SimpleParticleType(false)
            );

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
