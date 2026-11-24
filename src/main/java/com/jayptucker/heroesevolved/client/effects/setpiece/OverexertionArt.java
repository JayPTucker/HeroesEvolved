package com.jayptucker.heroesevolved.client.effects.setpiece;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/** A small nosebleed in the body's frame, with an occasional drop; blood stays in the lit matter pass. */
final class OverexertionArt {
    private static final float[] BLOOD = rgb(0x70212D), WET = rgb(0xAB3D43);
    static void draw(Canvas c, SetPieceClient.Active a, float t) {
        var body = a.bound(c);
        if (!(body instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive() || !c.soft()) return;
        var mc = Minecraft.getInstance();
        if (body == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        if (SetPieceClient.superseded(a, java.util.Set.of(a.piece()), c.level.getGameTime())) return;
        float duration = Math.clamp(a.seed(), 1, 6000);
        float alpha = smooth(0, 6, t) * (1 - smooth(Math.max(0, duration - 30), duration, t));
        if (alpha < .003) return;
        Vec3 look = living.getViewVector(c.partial), side = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 nose = living.getEyePosition(c.partial).add(look.scale(.265)).add(side.scale(.025)).add(0, -.09, 0);
        float length = .06F + .09F * smooth(0, 35, t);
        Vec3 end = nose.add(0, -length, 0);
        c.line(nose, end, .012F, BLOOD, .9F * alpha, .8F * alpha);
        c.line(nose.add(look.scale(.001)), end.add(look.scale(.001)), .004F, WET, .5F * alpha, .3F * alpha);
        float drop = (t % 32) / 32;
        if (drop < .6F) c.line(end.add(0, -drop * drop * 1.4, 0), end.add(0, -drop * drop * 1.4 - .025, 0),
                .009F * (1 - drop), BLOOD, alpha * (1 - drop), alpha * (1 - drop));
    }
}
