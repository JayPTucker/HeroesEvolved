package com.jayptucker.heroesevolved.client.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;

final class FlightEffects {
    private FlightEffects() {}
    static void burst(ClientLevel level, Vec3 center, Vec3 axis, float size, boolean detail) {
        ring(level, center, axis, .4F, size, 11, 0, 0xE2EAED, .28F);
        for (int i = 0; i < (detail ? 5 : 2); i++) {
            add(new PowerParticle(level, PowerParticle.Shape.ARC, center.subtract(axis.scale(i * .35)), axis,
                    .35F + i * .1F, size * (.65F + i * .1F), 13 + i, i, 0xE6F4F5, .35F)
                    .arc(i * 2.4F, .035F, 1.5F, .045F));
        }
    }
    static void cyclone(ClientLevel level, Vec3 center, boolean detail) {
        int layers = detail ? 9 : 5;
        for (int i = 0; i < layers; i++) {
            float height = i * 16F / (layers - 1), radius = .6F + height * .30F;
            float phase = level.getGameTime() * .23F - height * .6F;
            for (int side = 0; side < (detail ? 2 : 1); side++)
                add(new PowerParticle(level, PowerParticle.Shape.ARC, center.add(0, height, 0), new Vec3(.04, 1, .03),
                        radius, radius * 1.04F, 10, 0, i < 2 ? 0xADABA0 : 0xD9E6E8, .32F)
                        .arc(phase + side * (float) Math.PI, .23F, 3.7F, .07F + height * .009F));
        }
    }
}
