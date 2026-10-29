package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.PowerParticle.Shape.*;

/** Bounded wind compositions for the six learned Flight maneuvers. */
final class FlightSkillEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int WIND = 0xD9EEF2;
    private static final int BLUE = 0x9DCBD9;
    private static final int CORE = 0xF0FAF8;

    private FlightSkillEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target,
                        float size, boolean detail) {
        Vec3 delta = target.subtract(origin);
        Vec3 axis = delta.lengthSqr() < .001 ? UP : delta.normalize();
        switch (kind) {
            case FLIGHT_RESCUE -> rescue(level, origin, target, axis, detail);
            case FLIGHT_SANCTUARY -> sanctuary(level, origin, Mth.clamp(size, 1, 16), detail);
            case FLIGHT_ROLL -> roll(level, origin, axis, Mth.clamp(size, 0, 1), detail);
            case FLIGHT_JETSTREAM -> jetstream(level, origin, target, Mth.clamp(size, .5F, 4), detail);
            case FLIGHT_THERMAL_MARK -> thermalMark(level, origin, Mth.clamp(size, 1, 12), detail);
            case FLIGHT_THERMAL -> thermal(level, origin, Mth.clamp(size, 1, 12), detail);
            case FLIGHT_VACUUM -> vacuum(level, origin, axis, Mth.clamp(size, 1, 16), detail);
            case FLIGHT_PRESSURE_WAVE -> pressure(level, origin, Mth.clamp(size, 1, 32), detail);
            default -> { return false; }
        }
        return true;
    }

    private static void rescue(ClientLevel level, Vec3 from, Vec3 to, Vec3 axis, boolean detail) {
        Vec3 right = basis(axis), up = right.cross(axis).normalize();
        int segments = detail ? 12 : 6;
        for (int strand = 0; strand < 2; strand++) {
            Vec3 previous = from;
            for (int i = 1; i <= segments; i++) {
                float t = i / (float) segments;
                double angle = t * Math.PI * 4 + strand * Math.PI;
                double radius = Math.sin(t * Math.PI) * .55;
                Vec3 point = from.lerp(to, t).add(right.scale(Math.cos(angle) * radius))
                        .add(up.scale(Math.sin(angle) * radius));
                ribbon(level, previous, point, strand == 0 ? .1F : .16F,
                        16, i / 3, strand == 0 ? CORE : BLUE, .55F);
                previous = point;
            }
        }
        ring(level, to, axis, .35F, 1.8F, 16, 3, CORE, .5F);
        ring(level, to, UP, .4F, 1.5F, 18, 5, BLUE, .4F);
        WindParticles.gust(level, to, detail ? 2 : 1);
    }

    private static void sanctuary(ClientLevel level, Vec3 origin, float radius, boolean detail) {
        Vec3 floor = origin.add(0, .12, 0);
        WindParticles.swirl(level, floor, radius, 1.0, detail ? 6 : 2, true);
        ring(level, floor, UP, radius * .96F, radius, 22, 0, BLUE, .38F);
        int ribs = detail ? 4 : 2;
        for (int i = 0; i < ribs; i++) {
            double angle = Math.PI * i / ribs + level.getGameTime() * .008;
            Vec3 normal = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            // For a horizontal normal the arc's positive half lies above the ground.
            add(new PowerParticle(level, ARC, floor, normal, radius, radius, 22, 0,
                    i % 2 == 0 ? WIND : BLUE, .32F)
                    .arc(0, 0, Mth.PI, i % 2 == 0 ? .16F : .25F));
        }
        int bands = detail ? 3 : 2;
        for (int i = 0; i < bands; i++) {
            float height = radius * (.25F + i * .25F);
            float ringRadius = (float) Math.sqrt(radius * radius - height * height);
            add(new PowerParticle(level, ARC, floor.add(0, height, 0), UP,
                    ringRadius, ringRadius, 22, 0, WIND, .36F)
                    .arc(level.getGameTime() * .1F + i * 2.3F, .1F, 3.5F, .2F));
        }
    }

    private static void roll(ClientLevel level, Vec3 center, Vec3 axis, float progress, boolean detail) {
        float phase = progress * Mth.TWO_PI;
        WindParticles.wake(level, center, axis, detail ? 3 : 1);
        int turns = detail ? 3 : 2;
        for (int i = 0; i < turns; i++)
            add(new PowerParticle(level, ARC, center.subtract(axis.scale(i * .55)), axis,
                    .5F + i * .15F, 1.15F + i * .22F, 12, i, i % 2 == 0 ? CORE : BLUE, .5F)
                    .arc(phase - i * 1.8F, .24F, 4.2F, .15F)
                    .moving(axis.scale(-.1)));
    }

    private static void jetstream(ClientLevel level, Vec3 from, Vec3 to, float radius, boolean detail) {
        Vec3 horizontal = to.subtract(from).multiply(1, 0, 1);
        Vec3 axis = horizontal.lengthSqr() < .001 ? new Vec3(0, 0, 1) : horizontal.normalize();
        Vec3 right = new Vec3(axis.z, 0, -axis.x);
        Vec3 start = from.add(0, .16, 0), end = to.add(0, .16, 0);
        WindParticles.current(level, start, end, detail ? 8 : 3);
        for (int side : new int[]{-1, 1})
            ribbon(level, start.add(right.scale(radius * side)), end.add(right.scale(radius * side)),
                    .16F, 18, 0, BLUE, .4F);
        int arrows = detail ? 8 : 4;
        for (int i = 0; i < arrows; i++) {
            float t = (i + .5F) / arrows;
            Vec3 tip = start.lerp(end, t).add(axis.scale(.55));
            for (int side : new int[]{-1, 1})
                ribbon(level, tip.subtract(axis.scale(.7)).add(right.scale(radius * .6 * side)), tip,
                        .11F, 18, 0, WIND, .5F);
            if (detail && i % 2 == 0)
                add(new PowerParticle(level, ARC, start.lerp(end, t).add(0, .4, 0), axis,
                        radius * .65F, radius * .85F, 18, 0, CORE, .32F)
                        .arc(level.getGameTime() * .15F + i, .12F, 2.4F, .13F)
                        .moving(axis.scale(.18)));
        }
    }

    private static void thermalMark(ClientLevel level, Vec3 origin, float radius, boolean detail) {
        Vec3 floor = origin.add(0, .12, 0);
        WindParticles.updraft(level, origin, radius * .8, detail ? 3 : 1);
        ring(level, floor, UP, radius * .9F, radius, 10, 0, BLUE, .6F);
        int curls = detail ? 4 : 2;
        for (int i = 0; i < curls; i++)
            add(new PowerParticle(level, ARC, floor.add(0, .08 * i, 0), UP,
                    radius * .85F, radius * .35F, 9, 0, i % 2 == 0 ? CORE : BLUE, .52F)
                    .arc(i * Mth.TWO_PI / curls, .16F, 1.5F, .15F)
                    .moving(new Vec3(0, .08, 0)));
    }

    private static void thermal(ClientLevel level, Vec3 origin, float radius, boolean detail) {
        Vec3 floor = origin.add(0, .12, 0);
        WindParticles.updraft(level, origin, radius, detail ? 16 : 6);
        WindParticles.gust(level, floor.add(0, 1, 0), detail ? 2 : 1);
        ring(level, floor, UP, radius * .3F, radius, 15, 0, CORE, .5F);
        int layers = detail ? 6 : 3;
        for (int i = 0; i < layers; i++) {
            float height = i * 6F / (layers - 1);
            add(new PowerParticle(level, ARC, floor.add(0, height, 0), UP,
                    radius * (.82F - height * .07F), radius * (.65F - height * .05F),
                    18, i, i % 2 == 0 ? CORE : BLUE, .55F)
                    .arc(i * 1.7F, .25F, 4.2F, .22F)
                    .moving(new Vec3(0, .22, 0)));
        }
        int streaks = detail ? 4 : 2;
        for (int i = 0; i < streaks; i++) {
            double angle = i * Math.PI * 2 / streaks;
            Vec3 offset = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(radius * .55);
            ribbon(level, floor.add(offset), floor.add(offset.scale(.35)).add(0, 8, 0),
                    .13F, 13, i, WIND, .4F);
        }
    }

    private static void vacuum(ClientLevel level, Vec3 center, Vec3 axis, float radius, boolean detail) {
        int coils = detail ? 5 : 3;
        WindParticles.gather(level, center, radius * .6, detail ? 8 : 3);
        for (int i = 0; i < coils; i++) {
            Vec3 at = center.add(axis.scale(.7 + i * .7));
            add(new PowerParticle(level, ARC, at, axis, radius * (.4F + i * .12F), .3F,
                    9, 0, i % 2 == 0 ? CORE : BLUE, .5F)
                    .arc(level.getGameTime() * -.22F + i * 1.8F, -.24F, 3.8F,
                            i % 2 == 0 ? .18F : .28F)
                    .moving(axis.scale(-.09)));
        }
    }

    private static void pressure(ClientLevel level, Vec3 origin, float radius, boolean detail) {
        Vec3 center = origin.add(0, .15, 0);
        WindParticles.gust(level, center.add(0, .5, 0), detail ? 3 : 1);
        WindParticles.groundBlast(level, center, radius, detail ? 24 : 10);
        ring(level, center, UP, .35F, radius, 13, 0, CORE, .55F);
        ring(level, center.add(0, .15, 0), UP, .5F, radius * .95F, 18, 3, BLUE, .45F);
        int arches = detail ? 4 : 2;
        for (int i = 0; i < arches; i++) {
            double angle = Math.PI * i / arches;
            Vec3 normal = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            add(new PowerParticle(level, ARC, center, normal, .3F, radius * .8F, 16, i % 2,
                    WIND, .4F).arc(0, 0, Mth.PI, .2F));
        }
        if (detail) {
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3;
                Vec3 direction = new Vec3(Math.cos(angle), .12, Math.sin(angle));
                mote(level, center.add(direction.scale(.5)), direction.scale(.24), .09F, 18, WIND);
            }
        }
    }

    private static Vec3 basis(Vec3 axis) {
        return axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : UP).normalize();
    }

    private static void ribbon(ClientLevel level, Vec3 from, Vec3 to, float width,
                               int ticks, int delay, int color, float alpha) {
        add(new PowerParticle(level, STREAK, from, to, width, width * .5F,
                ticks, delay, color, alpha));
    }
}
