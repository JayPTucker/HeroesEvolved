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
            case QUAKE_CHANNEL -> {
                int fronts = detail ? 6 : 3;
                double length = Math.min(24, delta.length());
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
            case ELECTRIC_ORB -> {
                // The shell moves with the projectile between its two-tick updates.
                Vec3 velocity = delta.length() > 1.0D ? axis : delta;
                movingCore(level, origin, velocity, 0.16F, 4, 0xE6F8FF);
                orbit(level, origin, UP, size, 4, BLUE, 0.35F, velocity);
                if (detail) {
                    orbit(level, origin, new Vec3(1, 0.3D, 0), size * 0.9F,
                            4, 0xBCEAFF, -0.4F, velocity);
                    orbit(level, origin, new Vec3(0, 0.25D, 1), size * 1.1F,
                            4, BLUE, 0.3F, velocity);
                    streak(level, origin.subtract(axis.scale(0.6D)), origin, 0.07F, 5, BLUE);
                }
            }
            case ELECTRIC_IMPACT -> {
                ring(level, origin, UP, 0.15F, size, 14, 0, BLUE, 0.65F);
                if (detail) {
                    orbit(level, origin, new Vec3(1, 0.2D, 0), size * 0.7F,
                            14, 0xD5F4FF, 0.3F, Vec3.ZERO);
                    orbit(level, origin, new Vec3(0, 0.2D, 1), size * 0.8F,
                            14, BLUE, -0.2F, Vec3.ZERO);
                    burst(level, origin, UP, BLUE, density, 0.2F);
                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI / 4;
                        Vec3 radial = new Vec3(Math.cos(angle), 0.15D, Math.sin(angle));
                        Vec3 elbow = origin.add(radial.scale(size * 0.5D)).add(0, 0.25D, 0);
                        streak(level, origin, elbow, 0.045F, 7, 0xE6FAFF);
                        streak(level, elbow, origin.add(radial.scale(size)), 0.025F, 9, BLUE);
                    }
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
                    streak(level, start, start.subtract(axis.scale(2.0D)), 0.055F, 6, CYAN);
                }
                if (detail) {
                    orbit(level, origin.subtract(axis.scale(0.4D)), axis, 0.4F,
                            6, 0xE8FBFF, 0.28F, Vec3.ZERO);
                }
            }
            case DIVE_IMPACT -> {
                Vec3 center = origin.add(0, 0.12D, 0);
                ring(level, center, UP, 0.3F, size, 14, 0, CYAN, 0.7F);
                if (detail) {
                    ring(level, center.add(0, 0.13D, 0), UP, 0.3F, size * 1.2F,
                            20, 3, 0xFFFFFF, 0.35F);
                    orbit(level, center.add(0, 0.3D, 0), UP, size * 0.6F,
                            20, CYAN, 0.15F, Vec3.ZERO);
                    shards(level, center, UP, size, density, 0xDDF8FF);
                    burst(level, center, UP, CYAN, density, 0.12F);
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
