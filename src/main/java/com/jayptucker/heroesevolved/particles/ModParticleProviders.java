package com.jayptucker.heroesevolved.particles;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class ModParticleProviders {
    private ModParticleProviders() {
    }

    @SubscribeEvent
    public static void registerProviders(
            RegisterParticleProvidersEvent event
    ) {
        event.registerSpriteSet(
                ModParticles.WHITE_CONTRAIL_SMOKE.get(),
                WhiteContrailParticle.Provider::new
        );
    }
}
