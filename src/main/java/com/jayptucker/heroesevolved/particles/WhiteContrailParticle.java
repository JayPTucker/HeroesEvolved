package com.jayptucker.heroesevolved.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A white, long-lived version of Minecraft's cozy campfire smoke. Reusing
 * the vanilla smoke movement keeps the contrail natural while the custom
 * sprite provides the clean white color of a high-altitude contrail.
 */
public final class WhiteContrailParticle extends CampfireSmokeParticle {
    private WhiteContrailParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, true);
        setColor(1.0F, 1.0F, 1.0F);
        setAlpha(0.92F);
        setLifetime(20 * 8);
        scale(0.85F);
    }

    public static final class Provider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
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
            WhiteContrailParticle particle = new WhiteContrailParticle(
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
