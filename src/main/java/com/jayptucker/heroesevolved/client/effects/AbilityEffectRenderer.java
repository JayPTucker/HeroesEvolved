package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.PowerParticle.Shape.*;

/** All geometry is cosmetic and owned by the world's particle engine. */
public final class AbilityEffectRenderer {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int QUAKE = 0xB9E8F4;
    private static final int ELECTRIC = 0x63BAFF;
    private static final int PSYCHIC = 0xBD94FF;
    private static final int TEMPORAL = 0xDAC8FF;
    private static final int BLOOD = 0xFF667A;
    private static final int WIND = 0xD9F4FF;
    private static ClientLevel budgetWorld;
    private static long budgetTick;
    private static int emitted, motes;

    private AbilityEffectRenderer() {
    }

    static void clear() { budgetWorld = null; emitted = 0; motes = 0; }

    public static void play(AbilityEffectPayload effect) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !valid(effect.origin()) || !valid(effect.target())
                || !Float.isFinite(effect.scale())) {
            return;
        }
        Vec3 origin = effect.origin();
        Vec3 target = effect.target();
        // Bound geometry even if a server sends an unreasonable endpoint.
        if (origin.distanceToSqr(target) > 256 * 256) {
            target = origin.add(target.subtract(origin).normalize().scale(256));
        }
        float size = Math.clamp(effect.scale(), 0.1F, 64.0F);
        Vec3 view = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 segment = target.subtract(origin);
        double along = segment.lengthSqr() < .001 ? 0 : Math.clamp(view.subtract(origin).dot(segment) / segment.lengthSqr(), 0, 1);
        double viewDistance = view.distanceTo(origin.add(segment.scale(along)));
        if (viewDistance > 192 + size) return;
        boolean detail = minecraft.options.particles().get() != ParticleStatus.MINIMAL && viewDistance < 72 + size;
        int density = minecraft.options.particles().get() == ParticleStatus.ALL && viewDistance < 40 + size ? 20 : 10;
        Vec3 direction = target.subtract(origin).normalize();
        if (direction.lengthSqr() < 0.001D) {
            direction = UP;
        }

        if (NuclearEffects.play(level, effect.kind(), origin, size, detail)
                || CryoEffects.play(level, effect.kind(), origin, target, size, detail)
                || AwakeningEffects.play(level, effect.kind(), origin, size, detail, density)
                || DetailedAbilityEffects.play(level, effect.kind(), origin, target, size, detail, density)) {
            return;
        }

        switch (effect.kind()) {
            case QUAKE_BEAM -> {
                double length = origin.distanceTo(target);
                int rings = Math.clamp((int) Math.ceil(length / 3.5D), 1, detail ? 16 : 6);
                for (int i = 0; i < rings; i++) {
                    double distance = length * (i + .5) / rings;
                    float radius = (float) (0.23D + distance * 0.065D);
                    add(new PowerParticle(level, ARC, origin.add(direction.scale(distance)), direction,
                            radius * .65F, radius * 1.3F, 10, i / 3, QUAKE, .5F)
                            .arc(i * 2.4F, .045F, 4.8F, .045F));
                }
                if (detail) {
                    burst(level, target, direction.reverse(), QUAKE, density / 2, 0.12F);
                }
            }
            case QUAKE_IMPACT -> {
                Vec3 center = origin.add(0, 0.12D, 0);
                // QuakeService advances damage at 0.8 blocks/tick from radius 0.5.
                int duration = Math.max(1, (int) Math.ceil((size - 0.5F) / 0.8F));
                ring(level, center, UP, 0.5F, size, duration, 0, QUAKE, 0.8F);
                if (detail) {
                    ring(level, center.add(0, 0.09D, 0), UP, 0.5F, size * 0.9F,
                            duration + 3, 3, 0xFFFFFF, 0.4F);
                    DetailedAbilityEffects.faults(level, center, size, density / 2);
                }
            }
            case QUAKE_PULSE -> {
                ring(level, origin.add(0, 0.15D, 0), UP, 0.3F, size, 18, 0, QUAKE, 0.5F);
                if (detail) {
                    ring(level, origin.add(0, 0.3D, 0), UP, 0.15F, size * 0.75F,
                            20, 3, 0xE8FAFF, 0.32F);
                    DetailedAbilityEffects.faults(level, origin.add(0, .08, 0), size * .65F, 6);
                }
            }
            case TREMOR_SCAN -> {
                ring(level, origin.add(0, 0.08D, 0), UP, 0.3F, size, 36, 0, 0x58C8EE, 0.6F);
                if (detail) {
                    ring(level, origin.add(0, 0.11D, 0), UP, 0.3F, size, 36, 5, QUAKE, 0.35F);
                }
            }
            case QUAKE_PLUNGE -> {
                ring(level, origin.add(0, 0.4D, 0), UP, 0.7F, 1.5F, 9, 0, QUAKE, 0.5F);
                if (detail) {
                    helix(level, origin, 0.5F, 1.6F, QUAKE, density / 2, 10);
                }
            }
            case ELECTRIC_ARC -> lightning(level, origin, target, detail);
            case ELECTRIC_NOVA -> {
                ring(level, origin.add(0, 0.5D, 0), UP, 0.2F, size, 14, 0, ELECTRIC, 0.6F);
                if (detail) {
                    for (int i = 0; i < 5; i++) {
                        double angle = i * Math.PI * 2 / 5;
                        lightning(level, origin.add(0, .8, 0), origin.add(Math.cos(angle) * size,
                                .1, Math.sin(angle) * size), false);
                    }
                }
            }
            case PSYCHIC_PUSH -> {
                for (int i = 0; i < (detail ? 3 : 1); i++) {
                    Vec3 center = origin.add(0, 1.1D, 0).add(direction.scale(0.8D + i * 1.2D));
                    ring(level, center, direction, 0.3F, size * (0.5F + i * 0.2F),
                            14, i * 2, PSYCHIC, 0.42F);
                }
            }
            case PSYCHIC_BIND -> {
                DetailedAbilityEffects.orbit(level, origin.add(0, .35, 0), new Vec3(.2, 1, .1), size, 12, PSYCHIC, .12F, Vec3.ZERO);
                DetailedAbilityEffects.orbit(level, origin.add(0, 1.2, 0), new Vec3(-.2, 1, .1), size * .8F, 12, 0xE6DAFF, -.12F, Vec3.ZERO);
                if (detail) {
                    DetailedAbilityEffects.orbit(level, origin.add(0, 0.8D, 0),
                            new Vec3(0.3D, 1, 0.2D), size, 12, PSYCHIC, 0.13F, Vec3.ZERO);
                    helix(level, origin, size, 1.7F, PSYCHIC, density / 2, 12);
                }
            }
            case PSYCHIC_FIELD -> {
                ring(level, origin.add(0, 0.15D, 0), UP, 0.3F, size, 24, 0, PSYCHIC, 0.5F);
                if (detail) {
                    helix(level, origin, Math.min(size, 1.5F), 2.1F, PSYCHIC, density, 22);
                }
            }
            case TEMPORAL_BURST, TEMPORAL_FIELD -> {
                boolean field = effect.kind() == AbilityEffectPayload.Kind.TEMPORAL_FIELD;
                ring(level, origin.add(0, 0.12D, 0), UP, field ? 0.4F : size,
                        field ? size : 0.1F, 24, 0, TEMPORAL, 0.55F);
                if (detail) {
                    helix(level, origin, 0.65F, 1.9F, TEMPORAL, density, 24);
                    Vec3 dial = origin.add(0, .18, 0);
                    for (int hand = 0; hand < 2; hand++) {
                        double angle = level.getGameTime() * (hand == 0 ? .03 : .13);
                        Vec3 tip = dial.add(Math.cos(angle) * size * (hand == 0 ? .45 : .7), 0,
                                Math.sin(angle) * size * (hand == 0 ? .45 : .7));
                        streak(level, dial, tip, .025F, 18, 0xFFE2AB);
                    }
                    add(new PowerParticle(level, ARC, dial, UP, size * .72F, size * .72F,
                            24, 0, TEMPORAL, .5F).arc(0, field ? .08F : -.16F, 4.6F, .018F));
                    // Clock-like radial ticks; the center stays clear for aiming.
                    for (int i = 0; i < 12; i++) {
                        double angle = Math.PI * 2 * i / 12;
                        Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                        Vec3 center = origin.add(0, 0.16D, 0);
                        streak(level, center.add(radial.scale(size * 0.8D)),
                                center.add(radial.scale(size)), 0.025F, 24, 0xFFE2AB);
                    }
                }
            }
            case REGENERATION -> {
                helix(level, origin, 0.45F * size, 1.8F, BLOOD, detail ? density : 6, 24);
                if (detail) {
                    helix(level, origin.add(0, 0.1D, 0), 0.35F * size, 1.65F,
                            0xFFD7A5, density / 2, 28);
                }
            }
            case BLOOD_THREAD -> {
                int steps = detail ? 12 : 6;
                Vec3 previous = origin;
                for (int i = 1; i <= steps; i++) {
                    float t = i / (float) steps;
                    Vec3 point = origin.lerp(target, t).add(0, Math.sin(t * Math.PI) * 0.3D, 0);
                    streak(level, previous, point, .025F, 18, BLOOD);
                    if (i % 3 == 0) mote(level, point, target.subtract(point).normalize().scale(.07), .045F, 20, 0xFFD7A5);
                    previous = point;
                }
            }
            case FLIGHT_BURST -> {
                FlightEffects.burst(level, origin, direction, size, detail);
            }
            case FLIGHT_CYCLONE -> {
                FlightEffects.cyclone(level, origin, detail);
            }
        }
    }

    private static void lightning(ClientLevel level, Vec3 from, Vec3 to, boolean detail) {
        Vec3 delta = to.subtract(from);
        int segments = Math.clamp((int) (delta.length() * 2), 3, detail ? 32 : 12);
        Vec3 previous = from;
        RandomSource random = level.random;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double jitter = Math.sin(t * Math.PI) * 0.4D;
            Vec3 next = i == segments ? to : from.add(delta.scale(t)).add(
                    (random.nextDouble() - 0.5D) * jitter,
                    (random.nextDouble() - 0.5D) * jitter,
                    (random.nextDouble() - 0.5D) * jitter);
            streak(level, previous, next, 0.055F, 5, ELECTRIC);
            if (detail) {
                streak(level, previous, next, 0.018F, 4, 0xEEFAFF);
                if (i % 4 == 0) {
                    Vec3 branch = next.add((random.nextDouble() - 0.5D) * 1.2D,
                            random.nextDouble() * 0.7D, (random.nextDouble() - 0.5D) * 1.2D);
                    streak(level, next, branch, 0.035F, 5, ELECTRIC);
                }
            }
            previous = next;
        }
    }

    static void ring(ClientLevel level, Vec3 center, Vec3 normal, float start,
                             float end, int ticks, int delay, int color, float alpha) {
        add(new PowerParticle(level, RING, center, normal, start, end, ticks, delay, color, alpha));
    }

    static void streak(ClientLevel level, Vec3 from, Vec3 to, float width, int ticks, int color) {
        add(new PowerParticle(level, STREAK, from, to, width, width * 0.3F, ticks, 0, color, 0.9F));
    }

    static void helix(ClientLevel level, Vec3 origin, float radius, float height,
                              int color, int count, int ticks) {
        // Two continuous rising strands, with sparse cells at their tips.
        double phase = level.getGameTime() * .18;
        int segments = count <= 6 ? 5 : 10;
        for (int strand = 0; strand < 2; strand++) {
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments, angle = t * Math.PI * 2 + phase + strand * Math.PI;
                Vec3 point = origin.add(Math.cos(angle) * radius, height * t, Math.sin(angle) * radius);
                if (previous != null) add(new PowerParticle(level, STREAK, previous, point,
                        .026F, .009F, ticks, i / 3, color, .48F));
                if (i % 3 == 0) mote(level, point, new Vec3(0, .025, 0), .045F, ticks, color);
                previous = point;
            }
        }
    }

    static void burst(ClientLevel level, Vec3 center, Vec3 axis, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Vec3 direction = new Vec3(level.random.nextDouble() - 0.5D,
                    level.random.nextDouble() - 0.35D, level.random.nextDouble() - 0.5D).normalize();
            mote(level, center.add(direction.scale(0.2D)), direction.add(axis.scale(0.4D)).scale(speed),
                    0.07F, 16 + level.random.nextInt(8), color);
        }
    }

    static void mote(ClientLevel level, Vec3 position, Vec3 velocity, float size, int ticks, int color) {
        resetBudget(level);
        int limit = Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL ? 96 : 40;
        if (motes++ >= limit) return;
        PowerParticle particle = new PowerParticle(level, MOTE, position, UP,
                size, size * 0.4F, ticks, 0, color, 0.85F);
        particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
        add(particle);
    }

    static void add(PowerParticle particle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        resetBudget(mc.level);
        int limit = switch (mc.options.particles().get()) { case ALL -> 240; case DECREASED -> 140; case MINIMAL -> 72; };
        if (emitted++ < limit) mc.particleEngine.add(particle);
    }

    private static void resetBudget(ClientLevel level) {
        if (budgetWorld != level || budgetTick != level.getGameTime()) {
            budgetWorld = level; budgetTick = level.getGameTime(); emitted = 0; motes = 0;
        }
    }

    private static boolean valid(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }
}
