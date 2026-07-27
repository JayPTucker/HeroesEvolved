package com.jayptucker.heroesevolved.particles.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.particles.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class ContrailParticle extends TextureSheetParticle {
    private static final int LIFETIME_TICKS = 20 * 8;
    private final float startingSize;

    private ContrailParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        super(level, x, y, z, xSpeed * 0.25D, ySpeed * 0.25D, zSpeed * 0.25D);

        // Particle's velocity constructor introduces random motion even when
        // its supplied speed is zero. A contrail must remain at its emission
        // point, so clear that inherited random drift explicitly.
        xd = 0.0D;
        yd = 0.0D;
        zd = 0.0D;

        // The contrail remains still and lasts eight seconds.
        friction = 0.98F;
        gravity = 0.0F;
        lifetime = LIFETIME_TICKS + random.nextInt(11) - 5;
        startingSize = 0.12F;
        quadSize = startingSize;
        alpha = 0.78F;
        setColor(1.0F, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (removed) {
            return;
        }

        float progress = age / (float) lifetime;

        // The particle does not expand; the player's movement alone creates
        // the clean line of the contrail.
        quadSize = startingSize;
        alpha = 0.78F * (1.0F - progress);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @SubscribeEvent
    public static void registerProviders(
            RegisterParticleProvidersEvent event
    ) {
        event.registerSpriteSet(
                ModParticles.CONTRAIL.get(),
                Provider::new
        );
    }

    private static final class Provider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        private Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed
        ) {
            ContrailParticle particle = new ContrailParticle(
                    level,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed
            );
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
