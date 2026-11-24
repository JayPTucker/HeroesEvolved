package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.story.future.FutureWarningGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** A violet seam failing to hold open, with a black depth and white light hauling the traveler back through it. */
final class FutureWarningArt {
    private static final float[] DARK = Canvas.rgb(0x100D26), DEEP = Canvas.rgb(0x5B3FD1),
            VIOLET = Canvas.rgb(0x9A7CFF), CORE = Canvas.rgb(0xF4EBFF);
    private FutureWarningArt() {}
    static void draw(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 at = a.origin(), normal = a.facing(), side = normal.cross(Canvas.UP).normalize();
        boolean shut = a.piece() == Piece.FUTURE_SHUT, omen = a.piece() == Piece.FUTURE_OMEN;
        if (!shut) {
            for (Vec3 closed : SetPieceClient.where(Piece.FUTURE_SHUT)) if (closed.equals(at)) return;
            if (!omen && SetPieceClient.superseded(a, Set.of(Piece.FUTURE_BREACH), c.level.getGameTime())) return;
        }
        float age = omen || shut ? t : a.seed() + t;
        float alpha = Canvas.fade(t, a.piece().ticks, 2, shut ? 10 : 3);
        float open = shut ? 1 - FutureWarningGeometry.smooth(t / 8) : FutureWarningGeometry.smooth(age / (omen ? 6 : 28));
        float width = omen ? .1F + .14F * (.5F + .5F * Mth.sin(t * 1.4F)) : .85F;
        if (!omen && !shut) width *= 1 - .65F * FutureWarningGeometry.pulled(age);
        width *= open;
        float height = 1.6F * Math.max(.08F, open);
        int n = Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL ? 24 : 56;
        Vec3[] rim = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float theta = Mth.TWO_PI * i / n;
            float ripple = 1 + .045F * Mth.sin(theta * 7 + age * .24F);
            rim[i] = at.add(side.scale(Mth.cos(theta) * width * ripple)).add(0, Mth.sin(theta) * height * ripple, 0);
        }
        if (c.soft()) {
            for (int i = 0; i < n; i++) c.tri(at.add(normal.scale(-.08)), rim[i], rim[i + 1], DARK, alpha * .93F, alpha * .93F, alpha * .93F);
            c.ribbon(rim, .15F, DARK, alpha, alpha);
        }
        if (!c.glow()) return;
        c.ribbon(rim, .17F, DEEP, alpha * .25F, alpha * .25F);
        c.ribbon(rim, .055F, VIOLET, alpha * .85F, alpha * .85F);
        c.ribbon(rim, .014F, CORE, alpha, alpha);
        if (!omen && !shut && age >= FutureWarningGeometry.PULL) {
            Vec3 chest = FutureWarningGeometry.actor(at, normal, age).add(0, 1.1, 0);
            float pull = FutureWarningGeometry.pulled(age);
            for (int i = 0; i < 3; i++) {
                Vec3 anchor = at.subtract(normal.scale(.08)).add(side.scale((i - 1) * .24)).add(0, (i - 1) * .6, 0);
                Vec3 held = chest.add(side.scale((i - 1) * .22)).add(0, (i - 1) * .35, 0);
                c.line(anchor, held, .14F, VIOLET, alpha * .3F, alpha * .3F);
                c.line(anchor, held, .045F, CORE, alpha * (.6F + .4F * pull), alpha);
                c.line(anchor, held, .012F, CORE, alpha, alpha);
            }
        }
        if (shut && a.scale() > .5F) {
            float burst = Canvas.fade(t, 24, 1, 18);
            c.ring(at, normal, .2F + t * .28F, .07F * burst, 0, Mth.TWO_PI, VIOLET, burst * .7F);
            c.glow(at, 2 * burst, CORE, burst * .65F);
        }
    }
}
