package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.PowerParticle.Shape.*;

/** Wind has a direction: inward coils, a released pressure wake, then spreading fronts. */
final class FlightEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int WIND = 0xDDEFF2;
    private static final int CORE = 0xF1FAFA;

    private FlightEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target,
                        float size, boolean detail) {
        Vec3 axis = target.subtract(origin);
        axis = axis.lengthSqr() < .001 ? UP : axis.normalize();
        switch (kind) {
            case FLIGHT_CHARGE -> charge(level, origin, axis, Mth.clamp(size, 0, 1), detail, false);
            case FLIGHT_DIVE_CHARGE -> charge(level, origin, axis, Mth.clamp(size, 0, 1), detail, true);
            case FLIGHT_SKYBURST -> skyburst(level, origin, axis, size, detail);
            case FLIGHT_TEMPEST_CHARGE -> tempestCharge(level, origin, size, detail);
            case FLIGHT_MOMENTUM -> {
                WindParticles.wake(level, origin, axis, detail ? 3 : 1);
                // A compact halo announces a full reserve without filling the crosshair.
                ring(level, origin.subtract(axis.scale(.35)), axis, .45F, 1.65F, 18, 0, CORE, .48F);
                for (int side : new int[]{-1, 1})
                    add(new PowerParticle(level, ARC, origin, axis, .8F, 1.35F, 22, 0, WIND, .5F)
                            .arc(side * 1.5F, side * .12F, 2.4F, .09F));
            }
            default -> { return false; }
        }
        return true;
    }

    static void burst(ClientLevel level, Vec3 center, Vec3 axis, float size, boolean detail) {
        WindParticles.gust(level, center.subtract(axis.scale(.6)), detail ? 3 : 1);
        ring(level, center, axis, .35F, size, 12, 0, WIND, .45F);
        for (int i = 0; i < (detail ? 4 : 2); i++)
            add(new PowerParticle(level, ARC, center.subtract(axis.scale(i * .3)), axis,
                    .25F + i * .1F, size * (.6F + i * .1F), 13 + i, i, CORE, .45F)
                    .arc(i * 2.4F, .07F, 2.3F, .09F));
    }

    private static void charge(ClientLevel level, Vec3 center, Vec3 axis, float progress,
                               boolean detail, boolean dive) {
        float phase = level.getGameTime() * (dive ? -.32F : .24F);
        // Air visibly gathers in toward the flier while the wind coils.
        WindParticles.gather(level, center, 2.4 - progress, detail ? 3 : 1);
        int coils = detail ? 4 : 2;
        for (int i = 0; i < coils; i++) {
            float radius = .8F + i * .18F + progress * .5F;
            Vec3 at = center.add(axis.scale((i - (coils - 1) * .5) * .35));
            boolean core = i % 2 == 0;
            // Broad blue-gray wisps support bright bands without becoming a solid shell.
            float width = core ? .105F + progress * .055F : .19F + progress * .07F;
            add(new PowerParticle(level, ARC, at, axis, radius, .3F + progress * .15F,
                    9, 0, core ? CORE : 0xA9CCD5, core ? .52F + progress * .1F : .34F + progress * .08F)
                    .arc(phase + i * 1.7F, dive ? -.22F : .18F, 3.2F, width));
        }
        // The groundward disc makes the suspended dive direction readable from the side.
        if (dive)
            ring(level, center.add(axis.scale(1.1)), axis, .7F + progress * .4F, .25F,
                    9, 0, WIND, .45F);
    }

    private static void skyburst(ClientLevel level, Vec3 center, Vec3 axis, float strength, boolean detail) {
        float size = Mth.clamp(strength, .5F, 4);
        WindParticles.gust(level, center.subtract(axis.scale(.8)), detail ? 4 : 2);
        WindParticles.wake(level, center, axis, detail ? 5 : 2);
        // Pressure peels away behind the flier in a corkscrew, rather than an opaque flash.
        int fronts = detail ? 5 : 3;
        for (int i = 0; i < fronts; i++) {
            Vec3 at = center.subtract(axis.scale(.35 + i * .55));
            float end = size * (.65F + i * .24F);
            add(new PowerParticle(level, ARC, at, axis, .25F, end, 16 + i * 2, i,
                    i == 0 ? CORE : WIND, .52F)
                    .arc(i * 1.9F, .14F, 3.9F, .16F - i * .015F)
                    .moving(axis.scale(-.09 - i * .015)));
        }
        ring(level, center, axis, .3F, size * 1.4F, 13, 0, CORE, .48F);
        if (detail) {
            Vec3 right = basis(axis), up = right.cross(axis).normalize();
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2 * i / 6;
                Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                Vec3 start = center.add(radial.scale(.45));
                streak(level, start, start.subtract(axis.scale(1.6 + size)).add(radial.scale(size * .7)),
                        .05F, 10 + i, WIND);
            }
        }
    }

    private static void tempestCharge(ClientLevel level, Vec3 center, float size, boolean detail) {
        float radius = Mth.clamp(size, 1, 32);
        Vec3 floor = center.add(0, .12, 0);
        WindParticles.swirl(level, floor, radius, 1.2, detail ? 16 : 6, false);
        // This boundary matches the coming updraft's reach throughout its wind-up.
        ring(level, floor, UP, radius, radius * .94F, 12, 0, WIND, .35F);
        int strands = detail ? 6 : 3;
        for (int i = 0; i < strands; i++) {
            float phase = level.getGameTime() * .16F + i * Mth.TWO_PI / strands;
            add(new PowerParticle(level, ARC, floor.add(0, i * .12, 0), UP,
                    radius * .92F, radius * .25F, 14, 0, i % 2 == 0 ? WIND : 0xA9C3CC, .5F)
                    .arc(phase, .19F, 1.7F, i % 2 == 0 ? .17F : .25F).moving(new Vec3(0, .045, 0)));
        }
    }

    static void cyclone(ClientLevel level, Vec3 center, float size, boolean detail) {
        float radius = Mth.clamp(size, 1, 32);
        float height = 16;
        int layers = detail ? 9 : 5;
        WindParticles.swirl(level, center.add(0, .1, 0), radius, height * .8, detail ? 30 : 10, false);
        // Small gusts tumbling round the funnel make its shape readable against the sky.
        for (int i = 0; i < (detail ? 3 : 1); i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, t = level.random.nextDouble();
            double r = radius * (.25 + .7 * t);
            WindParticles.gust(level, center.add(Math.cos(angle) * r, .5 + t * height * .8, Math.sin(angle) * r), 0);
        }
        ring(level, center.add(0, .12, 0), UP, radius * .9F, radius, 12, 0, WIND, .3F);
        for (int i = 0; i < layers; i++) {
            float t = i / (float) (layers - 1);
            float y = .35F + t * height;
            float r = radius * (.2F + .72F * t);
            float phase = level.getGameTime() * .27F - t * 7;
            // Leaning alternating layers avoid the look of stacked horizontal hoops.
            Vec3 normal = new Vec3(Math.sin(phase) * .07, 1, Math.cos(phase) * .07);
            for (int side = 0; side < (detail ? 2 : 1); side++)
                add(new PowerParticle(level, ARC, center.add(0, y, 0), normal,
                        r, r * 1.07F, 12, 0, i < 3 ? 0xA9C0C5 : WIND, .55F - t * .1F)
                        .arc(phase + side * Mth.PI, .26F, 3.7F,
                                side == 0 ? .26F + t * .19F : .18F + t * .12F)
                        .moving(new Vec3(0, .07 + t * .025, 0)));
        }
        if (detail) {
            // Short flecks spiral up with the wind; they are cosmetic, never terrain debris.
            for (int i = 0; i < 6; i++) {
                double angle = level.getGameTime() * .25 + i * Math.PI / 3;
                double r = radius * (.35 + i * .07);
                Vec3 at = center.add(Math.cos(angle) * r, .3 + i * .35, Math.sin(angle) * r);
                mote(level, at, new Vec3(-Math.sin(angle) * .2, .16, Math.cos(angle) * .2),
                        .09F, 14, 0xBACECF);
            }
        }
    }

    static void diveTrail(ClientLevel level, Vec3 origin, Vec3 axis, float size, boolean detail) {
        Vec3 right = basis(axis);
        int trails = detail ? 2 : 1;
        float strength = Mth.clamp(size, .75F, 3);
        WindParticles.wake(level, origin, axis, detail ? 2 : 1);
        for (int i = 0; i < trails; i++) {
            Vec3 start = origin.add(right.scale(trails == 1 ? 0 : (i == 0 ? -.2 : .2)));
            add(new PowerParticle(level, STREAK, start, start.subtract(axis.scale(2.5 + strength)),
                    .075F, .025F, 11, 0, CORE, .6F));
        }
        add(new PowerParticle(level, ARC, origin.subtract(axis.scale(.45)), axis,
                .35F, .85F + strength * .15F, 10, 0, WIND, .45F)
                .arc(level.getGameTime() * -.4F, -.18F, detail ? 4.5F : 2.8F, .12F));
    }

    static void diveImpact(ClientLevel level, Vec3 origin, float size, boolean detail) {
        Vec3 center = origin.add(0, .14, 0);
        // The ground takes the hit: a gust at the point of impact and dust thrown out in a ring.
        WindParticles.gust(level, center.add(0, .4, 0), detail ? 4 : 2);
        WindParticles.groundBlast(level, center, size, detail ? 28 : 12);
        // Immediate contact, a broad pressure front, then a delayed, softer rolling edge.
        ring(level, center, UP, .2F, size, 13, 0, CORE, .48F);
        ring(level, center.add(0, .08, 0), UP, .3F, size * .96F, 19, 3, WIND, .3F);
        int arcs = detail ? 8 : 4;
        for (int i = 0; i < arcs; i++)
            add(new PowerParticle(level, ARC, center, UP, .4F, size, 18, i % 3,
                    i % 2 == 0 ? WIND : 0xB5C7C6, .46F)
                    .arc(i * Mth.TWO_PI / arcs, .045F, .7F, .2F)
                    .moving(new Vec3(0, .035, 0)));
        if (detail) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                mote(level, center.add(outward.scale(.5)), outward.scale(.2).add(0, .08, 0),
                        .065F, 18, WIND);
            }
        }
    }

    private static Vec3 basis(Vec3 axis) {
        return axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : UP).normalize();
    }
}
