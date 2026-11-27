package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * What every Ultimate's drawing shares (each power's own look stays in its own art class): stopping when the Ultimate
 * was cut short, finding the foe whose id rides in the seed, and the frame the server measured the beats in.
 */
final class UltArt {
    private static final Set<Piece> CUT = Set.of(Piece.ULT_CUT);

    private UltArt() {}

    /** The Ultimate this piece belongs to was cut short (its caster died, logged out, was dampened). */
    static boolean cut(Canvas c, Active a) {
        return a.entity() >= 0 && SetPieceClient.superseded(a, CUT, c.level.getGameTime());
    }

    /** The held foe, whose id rides in the seed, or null. */
    static Entity foe(Canvas c, Active a) { return a.seed() >= 0 && c.level != null ? c.level.getEntity(a.seed()) : null; }

    /** The right of a level facing. */
    static Vec3 side(Vec3 facing) { return new Vec3(-facing.z, 0, facing.x); }

    /** A point {@code f} along, {@code s} right of and {@code u} above {@code base} in the frame of {@code facing}. */
    static Vec3 frame(Vec3 base, Vec3 facing, double f, double s, double u) {
        return base.add(facing.scale(f)).add(side(facing).scale(s)).add(0, u, 0);
    }

    static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }

    /** 0 before {@code from}, rising to 1 over {@code in} ticks, falling back over {@code out} before {@code to}. */
    static float window(float t, float from, float to, float in, float out) {
        return Canvas.smooth(from, from + in, t) * (1 - Canvas.smooth(to - out, to, t));
    }

    /** Where a body's hand is, roughly, on the side {@code sign} (1 right), {@code reach} out along its look. */
    static Vec3 hand(Entity body, float partial, int sign, double reach) {
        float yaw = body instanceof net.minecraft.world.entity.LivingEntity l
                ? net.minecraft.util.Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD
                : body.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 shoulder = body.getPosition(partial).add(0, body.getBbHeight() * .76, 0).add(right.scale(.31 * sign));
        Vec3 look = body.getViewVector(partial);
        return shoulder.add(look.scale(reach));
    }
}
