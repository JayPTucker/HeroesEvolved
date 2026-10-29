package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.PowerParticle.Shape.*;

/** Refraction collars and tension filaments decorate real, server-owned moving objects. */
final class TelekinesisEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int CORE = 0xECF5FF;
    private static final int EDGE = 0xBFC8EB;
    private static final int SHADE = 0x929FC5;

    private TelekinesisEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        Vec3 delta = target.subtract(origin);
        Vec3 axis = delta.lengthSqr() < .001 ? UP : delta.normalize();
        switch (kind) {
            case TK_GATHER -> gather(level, origin, target, axis, Mth.clamp(size, .3F, 2), detail);
            case TK_ORBIT -> orbitObject(level, origin, target, axis, Mth.clamp(size, .3F, 2), detail);
            case TK_LAUNCH -> launch(level, origin, axis, Mth.clamp(size, .5F, 3), detail);
            case TK_IMPACT -> impact(level, origin, axis, Mth.clamp(size, .5F, 12), detail);
            case TK_GRIP -> grip(level, origin, target, axis, Mth.clamp(size, .4F, 2.5F), detail);
            case TK_WAVE -> wave(level, origin, target, axis, Mth.clamp(size, .5F, 16), detail);
            case TK_BARRIER -> barrier(level, origin, Mth.clamp(size, 1, 12), detail);
            case TK_WELL -> well(level, origin, Mth.clamp(size, 1, 16), detail);
            case TK_STEP -> step(level, origin, axis, Mth.clamp(size, .5F, 2), detail);
            default -> { return false; }
        }
        return true;
    }

    private static void gather(ClientLevel level, Vec3 from, Vec3 to, Vec3 axis, float size, boolean detail) {
        // Collars close around the lifted fragment, then travel into the caster's orbit.
        Vec3 travel = to.subtract(from).scale(1 / 12.0);
        for (int side : new int[]{-1, 1})
            add(new PowerParticle(level, ARC, from, axis, size * .75F, .12F, 12, 0,
                    side > 0 ? CORE : EDGE, side > 0 ? .55F : .32F)
                    .arc(side * 1.5F, side * .21F, 3.1F, side > 0 ? .09F : .18F)
                    .moving(travel));
        thread(level, from, to, axis, detail ? 7 : 3, .045F, 12, .3F);
        ring(level, to, axis, .22F, size * .65F, 10, 8, CORE, .32F);
    }

    private static void orbitObject(ClientLevel level, Vec3 caster, Vec3 object, Vec3 axis,
                                    float size, boolean detail) {
        float radius = .25F + size * .32F;
        float phase = level.getGameTime() * .12F;
        // Broken rotating brackets leave the actual block silhouette unobscured.
        for (int side : new int[]{-1, 1})
            add(new PowerParticle(level, ARC, object, axis, radius, radius * 1.08F, 9, 0,
                    side > 0 ? CORE : EDGE, .46F)
                    .arc(phase + side * 1.6F, side * .13F, 1.8F, side > 0 ? .07F : .13F));
        if (detail) {
            add(new PowerParticle(level, ARC, object, new Vec3(.3, 1, .2), radius * 1.15F,
                    radius, 10, 0, SHADE, .26F).arc(phase, -.11F, 3.5F, .15F));
            thread(level, caster, object, axis, 4, .025F, 8, .23F);
        }
    }

    private static void grip(ClientLevel level, Vec3 hand, Vec3 held, Vec3 axis, float size, boolean detail) {
        thread(level, hand, held, axis, detail ? 8 : 4, .045F, 9, .38F);
        float phase = level.getGameTime() * -.15F;
        for (int side : new int[]{-1, 1}) {
            Vec3 at = held.add(0, side * size * .35, 0);
            add(new PowerParticle(level, ARC, at, UP, size * .65F, size * .6F, 10, 0,
                    side > 0 ? CORE : EDGE, .5F)
                    .arc(phase + side * 1.4F, side * .16F, 3.5F, .11F));
        }
        if (detail)
            add(new PowerParticle(level, ARC, held, axis, size * .8F, size * .82F, 10, 0,
                    SHADE, .3F).arc(-phase, .08F, 2.5F, .18F));
    }

    private static void launch(ClientLevel level, Vec3 center, Vec3 axis, float force, boolean detail) {
        int collars = detail ? 4 : 2;
        for (int i = 0; i < collars; i++) {
            Vec3 at = center.subtract(axis.scale(i * .3));
            add(new PowerParticle(level, ARC, at, axis, .2F, force * (.45F + i * .18F),
                    13 + i, i, i % 2 == 0 ? CORE : EDGE, i % 2 == 0 ? .5F : .3F)
                    .arc(i * 2.1F, -.18F, 3.3F, i % 2 == 0 ? .09F : .17F)
                    .moving(axis.scale(-.1)));
        }
        ring(level, center, axis, .18F, force * .9F, 10, 0, CORE, .45F);
    }

    private static void impact(ClientLevel level, Vec3 center, Vec3 normal, float radius, boolean detail) {
        Vec3 surface = center.add(normal.scale(.045));
        ring(level, surface, normal, .15F, radius, 11, 0, CORE, .58F);
        add(new PowerParticle(level, ARC, surface, normal, .3F, radius * .85F, 15, 2,
                EDGE, .4F).arc(0, -.1F, 5, .16F));
        int shards = detail ? 8 : 4;
        Vec3 right = basis(normal), up = right.cross(normal).normalize();
        for (int i = 0; i < shards; i++) {
            double angle = i * Math.PI * 2 / shards;
            Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 velocity = radial.scale(.14 + radius * .015).add(normal.scale(.08));
            add(new PowerParticle(level, CRYSTAL, surface.add(radial.scale(.2)), radial,
                    .075F, .015F, 16 + i, i % 3, i % 2 == 0 ? CORE : EDGE, .66F).moving(velocity));
        }
    }

    private static void wave(ClientLevel level, Vec3 from, Vec3 to, Vec3 axis, float radius, boolean detail) {
        double distance = Math.min(from.distanceTo(to), 12);
        int fronts = detail ? 4 : 2;
        for (int i = 0; i < fronts; i++) {
            float t = (i + .5F) / fronts;
            Vec3 at = from.add(axis.scale(distance * t));
            float width = radius * (.45F + t * .5F);
            ring(level, at, axis, width * .3F, width, 12, i * 2, i % 2 == 0 ? CORE : EDGE, .42F);
            add(new PowerParticle(level, ARC, at, axis, width * .35F, width, 14, i * 2,
                    SHADE, .3F).arc(i * 1.7F, .08F, 4.1F, .16F).moving(axis.scale(.08)));
        }
    }

    private static void barrier(ClientLevel level, Vec3 center, float radius, boolean detail) {
        int planes = detail ? 5 : 3;
        for (int i = 0; i < planes; i++) {
            double angle = i * Math.PI / planes;
            Vec3 normal = i == 0 ? UP : new Vec3(Math.cos(angle), .2, Math.sin(angle));
            add(new PowerParticle(level, ARC, center, normal, radius * .98F, radius,
                    16, 0, i % 2 == 0 ? CORE : EDGE, i % 2 == 0 ? .26F : .2F)
                    .arc(level.getGameTime() * .055F + i * 1.4F, i % 2 == 0 ? .045F : -.055F,
                            4.6F, i % 2 == 0 ? .09F : .17F));
        }
        if (detail) {
            for (int i = 0; i < 3; i++) {
                double angle = level.getGameTime() * .08 + i * Math.PI * 2 / 3;
                Vec3 radial = new Vec3(Math.cos(angle), .18 * Math.sin(angle * 2), Math.sin(angle)).normalize();
                add(new PowerParticle(level, CRYSTAL, center.add(radial.scale(radius)), radial,
                        .1F, .06F, 14, 0, EDGE, .35F)
                        .moving(new Vec3(-radial.z * .05, .01, radial.x * .05)));
            }
        }
    }

    private static void well(ClientLevel level, Vec3 origin, float radius, boolean detail) {
        Vec3 center = origin.add(0, .12, 0);
        ring(level, center, UP, radius, radius * .95F, 18, 0, EDGE, .28F);
        int rings = detail ? 5 : 3;
        for (int i = 0; i < rings; i++) {
            Vec3 normal = new Vec3(i % 2 == 0 ? .12 : -.12, 1, .08);
            add(new PowerParticle(level, ARC, center.add(0, i * .22, 0), normal,
                    radius * (.95F - i * .1F), radius * .22F, 18, 0,
                    i % 2 == 0 ? CORE : SHADE, .38F)
                    .arc(level.getGameTime() * -.12F + i * 1.6F, -.16F, 3.5F, .14F)
                    .moving(new Vec3(0, .025, 0)));
        }
        if (detail) {
            for (int i = 0; i < 3; i++) {
                double angle = level.getGameTime() * .14 + i * Math.PI * 2 / 3;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 at = center.add(radial.scale(radius * .55));
                add(new PowerParticle(level, CRYSTAL, at, radial, .08F, .03F, 16, 0, EDGE, .45F)
                        .moving(new Vec3(-radial.z * .07, .06, radial.x * .07)));
            }
        }
    }

    private static void step(ClientLevel level, Vec3 feet, Vec3 axis, float size, boolean detail) {
        Vec3 floor = feet.add(0, -.02, 0);
        ring(level, floor, UP, .25F, size * .75F, 15, 0, CORE, .44F);
        for (int i = 0; i < (detail ? 3 : 2); i++)
            add(new PowerParticle(level, ARC, floor.subtract(axis.scale(i * .18)), UP,
                    size * .55F, size * .8F, 16, i * 2, i % 2 == 0 ? EDGE : CORE, .4F)
                    .arc(i * 2.1F, -.1F, 2.3F, .14F).moving(new Vec3(0, -.025, 0)));
    }

    private static void thread(ClientLevel level, Vec3 from, Vec3 to, Vec3 axis,
                               int segments, float width, int ticks, float alpha) {
        Vec3 right = basis(axis);
        float phase = level.getGameTime() * .18F;
        Vec3 previous = from;
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            double bend = Math.sin(t * Math.PI) * (.12 + Math.sin(t * Math.PI * 3 + phase) * .035);
            Vec3 next = from.lerp(to, t).add(right.scale(bend)).add(0, Math.sin(t * Math.PI) * .12, 0);
            add(new PowerParticle(level, STREAK, previous, next, width, width * .5F,
                    ticks, 0, i % 3 == 0 ? CORE : EDGE, alpha));
            previous = next;
        }
        if (segments > 4) {
            Vec3 travel = to.subtract(from).scale(1 / (double) ticks);
            add(new PowerParticle(level, ARC, from, axis, .13F, .07F, ticks, 0, CORE, .52F)
                    .arc(phase, .23F, 4, .06F).moving(travel));
        }
    }

    private static Vec3 basis(Vec3 axis) {
        return axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : UP).normalize();
    }
}
