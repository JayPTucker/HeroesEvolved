package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.FinisherClient;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * The glisten on a foe low enough to finish, drawn live for every one in range: once a second a thin gold ring rises up
 * the body from the feet to the crown, a halo, a gold body and a white core, fading in and out; two small glints wink
 * on the body between rings; and the foe the key would take gets a ring at its feet. The same for every power, so a
 * player learns one signal. The outline itself is the vanilla glow, coloured by {@code FinisherOutlineMixin}.
 */
final class TakedownArt {
    private static final float[] HALO = rgb(0xE8A43C), GOLD = rgb(0xFFD27A), CORE = rgb(0xFFF8E6);

    private TakedownArt() {}

    static boolean live() {
        return FinisherClient.armed() && ClientVisualSettings.FINISHER_GLISTEN.get();
    }

    static void drawLive(Canvas c) {
        if (!c.glow() || !live()) return;
        Minecraft mc = Minecraft.getInstance();
        float now = c.time();
        LivingEntity aimed = FinisherClient.aimed();
        boolean thin = mc.options.particles().get() == ParticleStatus.MINIMAL;
        double range = Takedowns.GLISTEN_RANGE;
        for (LivingEntity foe : c.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(range),
                FinisherClient::glistens)) {
            Vec3 feet = foe.getPosition(c.partial);
            float h = foe.getBbHeight(), r = foe.getBbWidth() * .62F + .12F;
            float bright = foe == aimed ? 1.35F : 1;
            // Each foe's ring starts at its own moment, so a crowd doesn't pulse in step.
            float p = frac(now / 20F + hash(foe.getId(), 3));
            float a = sin(PI * p) * .85F * bright;
            Vec3 at = feet.add(0, h * (.04 + .96 * p), 0);
            c.ring(at, UP, r, .085F, 0, TWO_PI, HALO, a * .3F);
            c.ring(at, UP, r, .03F, 0, TWO_PI, GOLD, a * .8F);
            c.ring(at, UP, r, .011F, 0, TWO_PI, CORE, a);
            if (!thin) for (int i = 0; i < 2; i++) {
                // A glint winks once between rings, at a seeded place on the body.
                float wink = frac(now / 20F + hash(foe.getId(), 10 + i) + .5F);
                float flash = smooth(0, .12F, wink) * (1 - smooth(.12F, .3F, wink));
                if (flash < .02F) continue;
                float ang = hash(foe.getId(), 20 + i + (int) (now / 20)) * TWO_PI;
                Vec3 spot = feet.add(cos(ang) * r * .9, h * (.3 + .6 * hash(foe.getId(), 30 + i + (int) (now / 20))), sin(ang) * r * .9);
                c.glow(spot, .16F, GOLD, flash * .6F * bright);
                c.glow(spot, .06F, CORE, flash * bright);
            }
            if (foe == aimed) {
                // The one the key would take: a still ring at its feet.
                Vec3 floor = feet.add(0, .04, 0);
                float pulse = .75F + .25F * sin(now * .35F);
                c.ring(floor, UP, r + .35F, .07F, 0, TWO_PI, HALO, .35F * pulse);
                c.ring(floor, UP, r + .35F, .022F, 0, TWO_PI, GOLD, .9F * pulse);
            }
        }
    }
}
