package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.PowerParticle.Shape.*;

/** Per-action compositions: a projectile, channel, area pulse, or impact has its own silhouette. */
final class DetailedAbilityEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int CYAN = 0xA9E8FF;
    private static final int BLUE = 0x55AFFF;
    private static final int VIOLET = 0xBC8AFF;
    private static final int CRIMSON = 0xFF697F;
    private static final int GOLD = 0xFFE0A1;

    private DetailedAbilityEffects() {
    }

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target,
                        float size, boolean detail, int density) {
        Vec3 delta = target.subtract(origin);
        Vec3 axis = delta.lengthSqr() < 0.0001D ? UP : delta.normalize();
        float phase = (float) (level.getGameTime() * 0.3D);
        switch (kind) {
            case SPEED_TRAIL -> {
                Vec3 from = origin.add(0, .65, 0), to = target.add(0, .65, 0);
                if (from.distanceToSqr(to) > .001) {
                    streak(level, from, to, .065F, 32, 0xFFB52E);
                    streak(level, from.add(0, .65, 0), to.add(0, .65, 0), .03F, 26, 0xFFE9AD);
                    if (detail) {
                        Vec3 right = basis(axis);
                        Vec3 previous = from;
                        // A fixed jagged ribbon budget, independent of speed or distance.
                        for (int i = 1; i <= 6; i++) {
                            Vec3 point = from.lerp(to, i / 6.0).add(right.scale(Math.sin(i * 4.3 + phase) * .20));
                            streak(level, previous, point, .025F, 20 + i, i % 2 == 0 ? 0xFFF2CE : 0xFF6A20);
                            previous = point;
                        }
                        ring(level, origin.add(0, .9, 0), axis, .34F, .06F, 16, 0, GOLD, .30F);
                    }
                }
            }
            case SPEED_BURST, SPEED_IMPACT -> {
                Vec3 point = kind == Kind.SPEED_IMPACT ? target : origin.add(0, .8, 0);
                ring(level, point, axis, .1F, size, 12, 0, 0xFFD16A, .55F);
                if (detail) {
                    burst(level, point, axis, 0xFFE6AB, 8, .18F);
                    for (int i = 0; i < 3; i++)
                        add(new PowerParticle(level, ARC, point, new Vec3(i == 0 ? 1 : .1, i == 1 ? 1 : .3, i == 2 ? 1 : .2),
                                .1F, size * .7F, 15, i, 0xFF8B24, .6F).arc(i * 2.1F, .22F, 3.5F, .025F));
                }
            }
            case SPEED_VORTEX -> {
                ring(level, origin.add(0, .1, 0), UP, size * .7F, size, 16, 0, GOLD, .35F);
                for (int i = 0; i < (detail ? 5 : 2); i++)
                    add(new PowerParticle(level, ARC, origin.add(0, .3 + i * .45, 0), UP,
                            size * (1 - i * .12F), size * (.8F - i * .10F), 18, 0,
                            i % 2 == 0 ? 0xFFD881 : 0xD9F4FF, .45F).arc(phase + i * 1.7F, .30F, 4.2F, .06F));
            }
            case SONIC_WAVE -> {
                int fronts = detail ? 10 : 4;
                for (int i = 0; i < fronts; i++) {
                    float t = (i + .5F) / fronts;
                    Vec3 point = origin.lerp(target, t);
                    ring(level, point, axis, .12F + t, .35F + t * 3, 12, i / 2, i % 2 == 0 ? 0xF8D983 : 0xC0F0E7, .48F);
                    if (detail) add(new PowerParticle(level, ARC, point, axis, .2F + t, .5F + t * 3,
                            14, i / 2, GOLD, .4F).arc(i * 2.4F, .07F, 2.2F, .025F));
                }
            }
            case SONIC_BURST, SONIC_SENSE -> {
                boolean sense = kind == Kind.SONIC_SENSE;
                ring(level, origin.add(0, .12, 0), UP, .3F, size, sense ? 32 : 16, 0, GOLD, .6F);
                for (int i = 0; i < (detail ? 4 : 1); i++)
                    add(new PowerParticle(level, ARC, origin.add(0, 1, 0), new Vec3(i % 2, .35, 1 - i % 2),
                            .3F, size * .7F, 20, i * 2, 0xBEF0DF, .35F).arc(i * 1.6F, .045F, 4.4F, .04F));
            }

            case NUCLEAR_RAY -> {
                // A tapered incandescent core, warm halo, and sparse turbulent eddies.
                streak(level, origin, target, .13F, 10, 0xFF6B16);
                streak(level, origin, target, .035F, 7, 0xFFF4C9);
                ring(level, target, axis, .12F, 1.8F, 12, 0, 0xFFAE41, .65F);
                if (detail) {
                    int eddies = Math.clamp((int) delta.length() / 6, 1, 10);
                    for (int i = 0; i < eddies; i++)
                        add(new PowerParticle(level, ARC, origin.lerp(target, (i + .5) / eddies), axis,
                                .12F, .38F, 12, i / 2, 0xFFB64B, .45F)
                                .arc(i * 2.4F, .12F, 2.2F, .025F));
                    burst(level, target, axis.reverse(), 0xFFDC8D, 10, .13F);
                }
            }
            case MIND_LINK -> {
                // Interleaved waveforms converge on a small halo at the target's head.
                int segments = detail ? 12 : 6;
                Vec3 right = basis(axis);
                Vec3 previous = origin;
                for (int i = 1; i <= segments; i++) {
                    double t = i / (double) segments;
                    Vec3 next = origin.lerp(target, t).add(right.scale(Math.sin(t * Math.PI * 4 + phase)
                            * Math.sin(t * Math.PI) * .14));
                    streak(level, previous, next, .018F, 16, 0xC2ABEA);
                    previous = next;
                }
                orbit(level, target, UP, .32F, 20, 0xEBDAFF, -.08F, Vec3.ZERO);
                if (detail) orbit(level, target, axis, .42F, 18, VIOLET, .09F, Vec3.ZERO);
            }
            case MIND_PULSE -> {
                Vec3 head = origin.add(0, 1.65, 0);
                for (int i = 0; i < (detail ? 3 : 1); i++)
                    add(new PowerParticle(level, ARC, head, new Vec3(.15 * i, 1, .1),
                            .2F, size * (.5F + i * .2F), 22, i * 3, 0xCDB6EB, .38F)
                            .arc(i * 2.1F, -.045F, 4.2F, .018F));
            }
            case QUAKE_CHANNEL -> {
                int fronts = detail ? 6 : 3;
                double length = Math.min(128, delta.length());
                for (int i = 0; i < fronts; i++) {
                    double along = length * (i + 0.5D) / fronts;
                    float radius = 0.18F + (float) along * 0.035F;
                    ring(level, origin.add(axis.scale(along)), axis, radius, radius * 1.7F,
                            6, 0, CYAN, 0.4F);
                }
                if (detail) {
                    coil(level, origin, target, 0.2F, 2, 12, 6, CYAN, phase);
                    orbit(level, origin, axis, 0.28F, 7, 0xEEFCFF, 0.24F, Vec3.ZERO);
                }
            }
            case QUAKE_CONTACT -> {
                Vec3 center = origin.add(axis.scale(0.035D));
                ring(level, center, axis, 0.15F, size, 10, 0, CYAN, 0.65F);
                if (detail) {
                    orbit(level, center, axis, size * 0.45F, 10, 0xF1FAFF, -0.18F, Vec3.ZERO);
                    burst(level, center, axis, CYAN, density / 2, 0.08F);
                }
            }
            case PSYCHIC_TETHER -> {
                // A curved force filament pulls toward the caster, unlike an electrical bolt.
                Vec3 previous = origin;
                int segments = detail ? 12 : 4;
                for (int i = 1; i <= segments; i++) {
                    double t = i / (double) segments;
                    Vec3 next = origin.lerp(target, t).add(0, Math.sin(t * Math.PI) * 0.35D, 0);
                    streak(level, previous, next, 0.035F, 12, VIOLET);
                    previous = next;
                }
                if (detail) {
                    orbit(level, target, axis, size, 14, VIOLET, -0.2F, Vec3.ZERO);
                    for (int i = 0; i < density / 2; i++) {
                        Vec3 point = target.lerp(origin, i / (double) density * 2);
                        mote(level, point, origin.subtract(point).normalize().scale(0.12D),
                                0.06F, 15, 0xE6D9FF);
                    }
                }
            }
            case PSYCHIC_IMPACT -> {
                ring(level, origin, axis, 0.1F, size, 12, 0, VIOLET, 0.6F);
                if (detail) {
                    orbit(level, origin, axis, size * 0.65F, 14, 0xE9D8FF, -0.25F, Vec3.ZERO);
                    shards(level, origin, axis, size, density / 2, VIOLET);
                }
            }
            case HEALING_AURA -> {
                Vec3 center = origin.add(0, 0.12D, 0);
                ring(level, center, UP, 0.4F, size, 24, 0, CRIMSON, 0.5F);
                // The boundary marks the full eight-block healing range.
                ring(level, center, UP, size, size, 24, 0, GOLD, 0.22F);
                if (detail) {
                    orbit(level, center.add(0, 0.05D, 0), UP, size, 24, GOLD, -0.07F, Vec3.ZERO);
                    orbit(level, center.add(0, 0.18D, 0), UP, size * 0.65F,
                            24, CRIMSON, 0.1F, Vec3.ZERO);
                    for (int i = 0; i < density; i++) {
                        double angle = i * Math.PI * 2 / density + phase;
                        double radius = size * (i % 2 == 0 ? 0.65D : 0.95D);
                        mote(level, center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                                new Vec3(0, 0.04D, 0), 0.055F, 28, i % 2 == 0 ? CRIMSON : GOLD);
                    }
                    helix(level, origin, 0.55F, 1.9F, CRIMSON, density / 2, 24);
                }
            }
            case TEMPORAL_BURST, TEMPORAL_FIELD -> {
                boolean field = kind == Kind.TEMPORAL_FIELD;
                Vec3 center = origin.add(0, .08, 0);
                float radius = Math.min(size, 48);
                // Clock-face arcs are world-anchored meshes, not hundreds of particles.
                ring(level, center, UP, field ? radius * .15F : 1.2F, field ? radius : .05F,
                        field ? 32 : 16, 0, GOLD, .45F);
                ring(level, center.add(0, .05, 0), UP, field ? radius * .2F : .1F,
                        field ? radius * .95F : 1.6F, 24, 2, VIOLET, .25F);
                if (detail) {
                    for (int i = 0; i < 12; i++) {
                        double a = i * Math.PI / 6;
                        Vec3 direction = new Vec3(Math.cos(a), 0, Math.sin(a));
                        streak(level, center.add(direction.scale(radius * .85)),
                                center.add(direction.scale(radius)), .025F, 28, i % 3 == 0 ? GOLD : CYAN);
                    }
                    for (int i = 0; i < 3; i++)
                        add(new PowerParticle(level, ARC, origin.add(0, .8, 0), new Vec3(i == 0 ? 1 : 0, i == 1 ? 1 : .2, i == 2 ? 1 : 0),
                                .2F, Math.min(radius, 1.6F), 22, i * 2, GOLD, .55F)
                                .arc(i * 2.1F, field ? .04F : -.16F, 4.4F, .025F));
                }
            }
            case TEMPORAL_REWIND -> {
                // Scale carries the ordered delay of this history segment.
                int delay = Math.clamp(Math.round(size), 0, 24);
                Vec3 from = origin.add(0, 0.9D, 0);
                Vec3 to = target.add(0, 0.9D, 0);
                delayedRibbon(level, from, to, 0.045F, 14, delay, 0xB89CFF);
                if (detail) {
                    delayedRibbon(level, from.add(0, 0.35D, 0), to.add(0, 0.35D, 0),
                            0.018F, 12, delay + 1, GOLD);
                    ring(level, target.add(0, 0.12D, 0), UP, 0.6F, 0.1F,
                            14, delay, 0xD7C5FF, 0.4F);
                    add(new PowerParticle(level, ARC, to, axis, 0.5F, 0.15F,
                            16, delay, GOLD, 0.55F).arc(phase, -0.18F, 2.2F, 0.025F));
                }
            }
            case DIVE_TRAIL -> {
                Vec3 right = basis(axis);
                int trails = detail ? 2 : 1;
                for (int i = 0; i < trails; i++) {
                    Vec3 start = origin.add(right.scale(trails == 1 ? 0 : (i == 0 ? -0.16D : 0.16D)));
                    add(new PowerParticle(level, STREAK, start, start.subtract(axis.scale(2.5D)),
                            .035F, .1F, 9, 0, 0xE0ECEF, .4F));
                }
                if (detail) {
                    orbit(level, origin.subtract(axis.scale(0.4D)), axis, 0.4F,
                            6, 0xE8FBFF, 0.28F, Vec3.ZERO);
                }
            }
            case DIVE_IMPACT -> {
                Vec3 center = origin.add(0, 0.12D, 0);
                FlightEffects.burst(level, center, UP, size, detail);
                if (detail) {
                    for (int i = 0; i < 6; i++)
                        add(new PowerParticle(level, ARC, center, UP, .4F, size * 1.2F,
                                18, i % 3, 0xC7D2CE, .3F).arc(i * 1.05F, .02F, .85F, .12F));
                    burst(level, center, UP, 0xD7E4E6, 10, .12F);
                }
            }

            default -> { return false; }
        }
        return true;
    }

    static void orbit(ClientLevel level, Vec3 center, Vec3 normal, float radius,
                      int ticks, int color, float speed, Vec3 velocity) {
        float phase = (float) (level.getGameTime() * speed);
        add(new PowerParticle(level, ARC, center, normal, radius, radius * 1.05F,
                ticks, 0, color, 0.7F).arc(phase, speed, 3.4F, 0.035F).moving(velocity));
    }

    /** Cosmetic fissures break up the perfect circular silhouette without scanning terrain. */
    static void faults(ClientLevel level, Vec3 center, float radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + level.getGameTime() * .013;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
            Vec3 elbow = center.add(radial.scale(radius * .45)).add(tangent.scale(radius * .08));
            delayedRibbon(level, center.add(radial.scale(.5)), elbow, .028F, 16, i % 3, 0xB7C8CE);
            delayedRibbon(level, elbow, center.add(radial.scale(radius * .8)), .018F, 12, 3 + i % 3, CYAN);
        }
    }

    private static void movingCore(ClientLevel level, Vec3 point, Vec3 velocity,
                                   float radius, int ticks, int color) {
        add(new PowerParticle(level, MOTE, point, UP, radius, radius * 0.7F,
                ticks, 0, color, 0.9F).moving(velocity));
    }

    private static void coil(ClientLevel level, Vec3 from, Vec3 to, float radius, int strands,
                             int segments, int ticks, int color, float phase) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 0.001D) {
            return;
        }
        Vec3 axis = delta.normalize();
        Vec3 right = basis(axis);
        Vec3 up = right.cross(axis).normalize();
        for (int strand = 0; strand < strands; strand++) {
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double angle = phase + strand * Math.PI + t * Math.PI * 4;
                double spread = radius * (1 + t * 0.6D);
                Vec3 point = from.lerp(to, t).add(right.scale(Math.cos(angle) * spread))
                        .add(up.scale(Math.sin(angle) * spread));
                if (previous != null) {
                    streak(level, previous, point, 0.022F, ticks, color);
                }
                previous = point;
            }
        }
    }

    private static void shards(ClientLevel level, Vec3 center, Vec3 axis,
                               float radius, int count, int color) {
        Vec3 right = basis(axis);
        Vec3 up = right.cross(axis).normalize();
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 from = center.add(radial.scale(0.4D));
            Vec3 to = center.add(radial.scale(radius * (0.4D + level.random.nextDouble() * 0.6D)))
                    .add(axis.scale(level.random.nextDouble() * 0.4D));
            delayedRibbon(level, from, to, 0.025F, 12, i % 4, color);
        }
    }

    private static void delayedRibbon(ClientLevel level, Vec3 from, Vec3 to, float width,
                                      int ticks, int delay, int color) {
        add(new PowerParticle(level, STREAK, from, to, width, width * 0.2F,
                ticks, delay, color, 0.75F));
    }

    private static Vec3 basis(Vec3 axis) {
        return axis.cross(Math.abs(axis.y) > 0.9D ? new Vec3(1, 0, 0) : UP).normalize();
    }
}
