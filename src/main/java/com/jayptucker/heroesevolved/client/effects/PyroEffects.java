package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;

/** Fire reads as rising heat: tapered tongues, embers and smoke. Blue flame is Flint's hotter core. */
final class PyroEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int CORE = 0xFFE08A, FLAME = 0xFF8A2A, EMBER = 0xFF4A1A;
    private static final int BLUE = 0x5AB4FF, WHITE_BLUE = 0xD6F1FF, STEAM = 0xE8EEF2;
    private PyroEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case PYRO_LASH -> lash(level, origin, target, detail);
            case PYRO_MANTLE -> mantle(level, origin, size >= 1.5F, detail);
            case PYRO_CAUTERIZE -> cauterize(level, origin, target, detail);
            case PYRO_MINE_ARM -> mine(level, origin, size >= 0.9F, detail);
            case PYRO_MINE_ERUPT -> mineErupt(level, origin, size, detail);
            case PYRO_LANCE -> lance(level, origin, target, size, detail);
            case PYRO_SHEPHERD -> shepherd(level, origin, target, detail);
            case PYRO_FIRESTORM -> firestorm(level, origin, size, detail);
            case PYRO_COLLAPSE -> collapse(level, origin, size, detail);
            case PYRO_HAND_JET -> handJet(level, origin, target.subtract(origin), size >= 1.5F, detail);
            case PYRO_FLIGHT -> flight(level, origin, target.subtract(origin), size >= 1.5F, detail);
            case PYRO_METEOR -> meteor(level, origin, target.subtract(origin), size >= 1.5F, detail);
            case PYRO_SPEAR_CHARGE -> spearCharge(level, origin, target, Math.clamp(size, 0, 1), detail);
            case PYRO_SUN -> sun(level, origin, target, Math.clamp(size >= 2 ? size - 2 : size, 0, 1), size >= 2, detail);
            case FIRE_BOLT -> bolt(level, origin, target, false, detail);
            case BLUE_FLAME -> {
                if (origin.distanceToSqr(target) > 1.5) bolt(level, origin, target, true, detail);
                else nova(level, origin, size, true, detail);
            }
            case FIRE_STREAM -> {
                Vec3 direction = target.subtract(origin);
                int puffs = detail ? 10 : 4;
                for (int i = 0; i < puffs; i++) {
                    double t = (i + level.random.nextDouble()) / puffs;
                    Vec3 point = origin.add(direction.scale(t));
                    Vec3 drift = direction.normalize().scale(0.12).add(jitter(level, 0.04 + t * 0.06)).add(0, 0.02, 0);
                    vanilla(level, t > 0.7 && level.random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.FLAME, point, drift);
                }
                streak(level, origin, target, 0.09F * size, 6, FLAME);
                if (detail) streak(level, origin, origin.lerp(target, 0.6), 0.05F * size, 5, CORE);
            }
            case FIRE_RING -> {
                ring(level, origin.add(0, 0.06, 0), UP, size * 0.85F, size, 18, 0, FLAME, 0.55F);
                if (detail) ring(level, origin.add(0, 0.4, 0), UP, size * 0.9F, size * 1.05F, 14, 3, EMBER, 0.4F);
                int tongues = Math.clamp((int) (size * (detail ? 6 : 3)), 4, 32);
                for (int i = 0; i < tongues; i++) {
                    double angle = i * Math.PI * 2 / tongues + level.random.nextDouble() * 0.2;
                    Vec3 base = origin.add(Math.cos(angle) * size, 0.1, Math.sin(angle) * size);
                    vanilla(level, ParticleTypes.FLAME, base, new Vec3(0, 0.06 + level.random.nextDouble() * 0.05, 0));
                }
            }
            case FIRE_NOVA -> nova(level, origin, size, false, detail);
            case STEAM -> {
                int puffs = Math.clamp((int) (size * (detail ? 8 : 3)), 4, 36);
                for (int i = 0; i < puffs; i++) {
                    Vec3 point = origin.add(jitter(level, size * 0.7)).add(0, level.random.nextDouble() * size * 0.5, 0);
                    vanilla(level, ParticleTypes.CLOUD, point, new Vec3(0, 0.03 + level.random.nextDouble() * 0.03, 0).add(jitter(level, 0.02)));
                }
                ring(level, origin.add(0, 0.2, 0), UP, 0.3F, size * 1.2F, 20, 0, STEAM, 0.45F);
            }
            case PYRO_AWAKEN_CHARGE -> {
                float progress = Math.clamp(size, 0, 1);
                Vec3 chest = origin.add(0, 1.1, 0);
                helix(level, origin, 0.5F + progress * 0.3F, 2.0F, progress > 0.7 ? BLUE : FLAME, detail ? 10 : 4, 12);
                for (int i = 0; i < (detail ? 6 : 2); i++) {
                    Vec3 from = chest.add(jitter(level, 1.6 - progress));
                    mote(level, from, chest.subtract(from).scale(0.12), 0.06F, 10, i % 2 == 0 ? CORE : EMBER);
                }
                vanilla(level, ParticleTypes.FLAME, chest.add(jitter(level, 0.4)), new Vec3(0, 0.04, 0));
            }
            case PYRO_AWAKEN_RELEASE -> {
                nova(level, origin, size, false, detail);
                ring(level, origin.add(0, 0.1, 0), UP, size * 0.4F, size * 1.1F, 26, 4, EMBER, 0.5F);
            }
            default -> { return false; }
        }
        return true;
    }

    /** An outward curling ribbon snaps at contact, then retracts toward its caster. */
    private static void lash(ClientLevel level, Vec3 palm, Vec3 contact, boolean detail) {
        Vec3 delta = contact.subtract(palm), axis = delta.normalize();
        Vec3 lateral = side(axis);
        int segments = detail ? 24 : 12;
        Vec3 previous = palm;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double curl = Math.sin(t * Math.PI) * Math.sin(t * Math.PI * 2);
            Vec3 point = palm.add(delta.scale(t)).add(lateral.scale(curl * Math.min(1.5, delta.length() * .12)))
                    .add(0, Math.sin(t * Math.PI) * .4, 0);
            ribbon(level, previous, point, .065F, 9, i / 4, FLAME, .9F);
            if (detail || i % 2 == 0)
                ribbon(level, point, previous, .025F, 5, 8 + (segments - i) / 4, CORE, .8F);
            previous = point;
        }
        ring(level, contact, axis, .12F, .75F, 7, 5, CORE, .8F);
        burst(level, contact, axis.reverse(), EMBER, detail ? 8 : 3, .1F);
    }

    /** Orbiting, staggered flame plates read as a protective shell, not another ground ring. */
    private static void mantle(ClientLevel level, Vec3 feet, boolean blue, boolean detail) {
        Vec3 center = feet.add(0, 1, 0);
        int outer = blue ? BLUE : FLAME, inner = blue ? WHITE_BLUE : CORE;
        float phase = level.getGameTime() * .17F;
        int plates = detail ? 5 : 3;
        for (int i = 0; i < plates; i++) {
            float angle = phase + i * (float) Math.PI * 2 / plates;
            add(new PowerParticle(level, PowerParticle.Shape.ARC, center.add(0, i % 2 == 0 ? .3 : -.25, 0),
                    UP, 1.0F, 1.08F, 7, 0, outer, .7F).arc(angle, .12F, .95F, .09F));
            Vec3 plate = center.add(Math.cos(angle) * .85, Math.sin(angle * 2) * .35, Math.sin(angle) * .85);
            vanilla(level, blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, plate, new Vec3(0, .035, 0));
            if (detail) mote(level, plate, new Vec3(0, .025, 0), .075F, 8, inner);
        }
    }

    /** Bright seams close inward, then two warm wing-like fans open up and dissolve. */
    private static void cauterize(ClientLevel level, Vec3 feet, Vec3 head, boolean detail) {
        Vec3 center = feet.lerp(head, .6);
        float phase = level.getGameTime() * .13F;
        int segments = detail ? 9 : 5;
        for (int wing : new int[]{-1, 1}) {
            Vec3 previous = center;
            for (int i = 1; i <= segments; i++) {
                double t = i / (double) segments;
                Vec3 point = center.add(wing * Math.sin(t * Math.PI * .6) * 1.5, t * .95 + Math.sin(t * Math.PI) * .3,
                        Math.sin(t * Math.PI) * .22);
                ribbon(level, previous, point, .09F * (1 - (float) t * .5F), 15, i / 2, i % 2 == 0 ? CORE : FLAME, .8F);
                if (detail && i % 2 == 0)
                    ribbon(level, point, point.add(wing * .15, -.45 * t, .1), .07F, 12, 2 + i / 2, CORE, .85F);
                // Warm feather plumes fill the silhouette so the phoenix reads clearly in daylight.
                if (detail || i % 2 == 0) {
                    Vec3 feather = point.add(wing * .1, -.3 * t, .08);
                    vanilla(level, ParticleTypes.SMALL_FLAME, feather,
                            new Vec3(wing * .025, .025, 0));
                    mote(level, feather, new Vec3(wing * .018, .018, 0), .12F, 15, CORE);
                }
                previous = point;
            }
        }
        int sparks = detail ? 12 : 5;
        for (int i = 0; i < sparks; i++) {
            double a = phase + i * Math.PI * 2 / sparks;
            Vec3 from = center.add(Math.cos(a) * .8, Math.sin(a * 2) * .65, Math.sin(a) * .8);
            mote(level, from, center.subtract(from).scale(.13), .08F, 12, CORE);
        }
        ring(level, feet.add(0, .08, 0), UP, .1F, 1.8F, 18, 5, CORE, .35F);
    }

    /** The private idle ember becomes an obvious tightening warning seal when triggered. */
    private static void mine(ClientLevel level, Vec3 center, boolean warning, boolean detail) {
        float radius = warning ? 1.05F : .48F;
        float phase = level.getGameTime() * (warning ? .3F : .08F);
        ring(level, center.add(0, .04, 0), UP, radius, warning ? .2F : radius * .9F,
                warning ? 8 : 28, 0, warning ? CORE : EMBER, warning ? .9F : .45F);
        int runes = detail ? 6 : 3;
        for (int i = 0; i < runes; i++) {
            double angle = phase + i * Math.PI * 2 / runes;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 at = center.add(radial.scale(radius)).add(0, .08, 0);
            ribbon(level, at, at.add(radial.scale(-.22)).add(0, warning ? .5 : .05, 0),
                    .045F, warning ? 8 : 25, 0, warning ? FLAME : CORE, .8F);
        }
        mote(level, center.add(0, .1, 0), new Vec3(0, .015, 0), warning ? .18F : .09F, warning ? 8 : 24, CORE);
    }

    /** A tripwire eruption vents vertically, then rolls embers low across the floor. */
    private static void mineErupt(ClientLevel level, Vec3 center, float radius, boolean detail) {
        nova(level, center, radius, false, detail);
        int vents = detail ? 8 : 4;
        for (int i = 0; i < vents; i++) {
            double a = i * Math.PI * 2 / vents;
            Vec3 base = center.add(Math.cos(a) * radius * .3, .1, Math.sin(a) * radius * .3);
            Vec3 top = base.add(Math.cos(a) * .25, 1.7 + (i % 3) * .3, Math.sin(a) * .25);
            ribbon(level, base, top, .13F, 11, i % 3, i % 2 == 0 ? CORE : FLAME, .85F);
        }
    }

    /** Twin blue helices and white compression rings form a short-lived drilling seam. */
    private static void lance(ClientLevel level, Vec3 origin, Vec3 target, float size, boolean detail) {
        Vec3 delta = target.subtract(origin), axis = delta.normalize(), right = side(axis), up = axis.cross(right).normalize();
        if (delta.lengthSqr() < .01) return;
        int segments = detail ? 24 : 12;
        double phase = level.getGameTime() * .35;
        for (int strand = 0; strand < 2; strand++) {
            Vec3 previous = origin;
            for (int i = 1; i <= segments; i++) {
                double t = i / (double) segments, angle = phase + t * Math.PI * 6 + strand * Math.PI;
                double width = .12 + Math.sin(t * Math.PI) * .16;
                Vec3 point = origin.add(delta.scale(t)).add(right.scale(Math.cos(angle) * width)).add(up.scale(Math.sin(angle) * width));
                ribbon(level, previous, point, strand == 0 ? .08F : .035F, 6, 0, strand == 0 ? BLUE : WHITE_BLUE, .9F);
                previous = point;
            }
        }
        if (detail) for (int i = 1; i <= 4; i++)
            ring(level, origin.lerp(target, i / 5.0), axis, .15F, .4F, 6, i % 2, WHITE_BLUE, .5F);
        burst(level, target, axis, BLUE, detail ? 6 : 2, .08F);
    }

    /** Each recovered flame visibly curls inward, rather than masquerading as an outward attack. */
    private static void shepherd(ClientLevel level, Vec3 source, Vec3 palms, boolean detail) {
        Vec3 delta = palms.subtract(source), axis = delta.normalize(), right = side(axis), up = axis.cross(right).normalize();
        int segments = detail ? 16 : 8;
        Vec3 previous = source;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments, a = t * Math.PI * 4;
            double radius = Math.sin(t * Math.PI) * .3;
            Vec3 point = source.add(delta.scale(t)).add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
            ribbon(level, previous, point, .04F + (float) t * .02F, 8, i / 3, i % 3 == 0 ? CORE : FLAME, .85F);
            previous = point;
        }
        mote(level, palms, Vec3.ZERO, .12F, 12, CORE);
        ring(level, palms, axis, .35F, .06F, 10, 4, CORE, .6F);
    }

    /** A rising, rotating wall with a quieter center makes its gathering behavior legible. */
    private static void firestorm(ClientLevel level, Vec3 center, float radius, boolean detail) {
        ring(level, center.add(0, .06, 0), UP, radius, radius, 12, 0, EMBER, .6F);
        int segments = detail ? 16 : 8, strands = detail ? 2 : 1;
        double phase = level.getGameTime() * .14;
        for (int strand = 0; strand < strands; strand++) {
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments, a = phase + t * Math.PI * 2 + strand * Math.PI;
                double reach = radius * (1 - t * .35);
                Vec3 point = center.add(Math.cos(a) * reach, .15 + t * 2.3, Math.sin(a) * reach);
                if (previous != null) ribbon(level, previous, point, .07F, 12, 0, t > .6 ? CORE : FLAME, .7F);
                if (i % 3 == 0) vanilla(level, ParticleTypes.FLAME, point,
                        new Vec3(-Math.sin(a) * .04, .05, Math.cos(a) * .04));
                previous = point;
            }
        }
        for (int i = 0; i < (detail ? 6 : 3); i++) {
            double a = phase + i * Math.PI * 2 / (detail ? 6 : 3);
            Vec3 from = center.add(Math.cos(a) * radius, .2, Math.sin(a) * radius);
            mote(level, from, center.add(0, 1, 0).subtract(from).scale(.035), .07F, 14, EMBER);
        }
    }

    /** The field collapses inward first, then a delayed central pillar punches upward. */
    private static void collapse(ClientLevel level, Vec3 center, float radius, boolean detail) {
        ring(level, center.add(0, .1, 0), UP, radius, .15F, 7, 0, CORE, .8F);
        ring(level, center.add(0, .7, 0), UP, radius * .7F, .1F, 7, 0, FLAME, .6F);
        int spokes = detail ? 10 : 5;
        for (int i = 0; i < spokes; i++) {
            double a = i * Math.PI * 2 / spokes;
            Vec3 edge = center.add(Math.cos(a) * radius, .15, Math.sin(a) * radius);
            ribbon(level, edge, center.add(0, .5, 0), .07F, 7, 0, FLAME, .75F);
        }
        ring(level, center.add(0, .15, 0), UP, .1F, radius, 12, 7, EMBER, .7F);
        for (int i = 0; i < (detail ? 6 : 3); i++) {
            double a = i * Math.PI * 2 / (detail ? 6 : 3);
            Vec3 base = center.add(Math.cos(a) * .3, .1, Math.sin(a) * .3);
            ribbon(level, base, base.add(0, 2.8, 0), .14F, 10, 7, i % 2 == 0 ? CORE : FLAME, .9F);
        }
    }

    private static Vec3 side(Vec3 axis) {
        return axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : UP).normalize();
    }

    private static void ribbon(ClientLevel level, Vec3 from, Vec3 to, float width, int ticks, int delay, int color, float alpha) {
        add(new PowerParticle(level, PowerParticle.Shape.STREAK, from, to,
                width, width * .3F, ticks, delay, color, alpha));
    }

    /** Twin foot jets; the separate hand jets use the server's yaw-aligned palm positions. */
    private static void flight(ClientLevel level, Vec3 feet, Vec3 velocity, boolean blue, boolean detail) {
        Vec3 horizontal = velocity.multiply(1, 0, 1);
        Vec3 right = horizontal.lengthSqr() > 0.01 ? horizontal.normalize().cross(UP) : new Vec3(1, 0, 0);
        Vec3 exhaust = exhaust(velocity);
        int outer = blue ? BLUE : FLAME, inner = blue ? WHITE_BLUE : CORE;
        double length = 1.0 + Math.min(1.8, velocity.length() * 1.4);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 nozzle = feet.add(right.scale(side * 0.19));
            Vec3 tip = nozzle.add(exhaust.scale(length));
            streak(level, nozzle, tip, 0.13F, 7, outer);
            streak(level, nozzle, nozzle.lerp(tip, 0.7), 0.055F, 6, inner);
            ring(level, nozzle.add(exhaust.scale(0.35)), exhaust, 0.1F, 0.28F, 7, 0, outer, 0.5F);
            int embers = detail ? 5 : 2;
            for (int i = 0; i < embers; i++) {
                Vec3 at = nozzle.lerp(tip, level.random.nextDouble());
                vanilla(level, blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        at.add(jitter(level, 0.1)), exhaust.scale(0.14).add(jitter(level, 0.025)));
            }
            if (detail) {
                mote(level, tip, exhaust.scale(0.08), 0.06F, 12, EMBER);
                vanilla(level, ParticleTypes.SMOKE, tip, exhaust.scale(0.04));
            }
        }
    }

    private static Vec3 exhaust(Vec3 velocity) {
        // At cruise speed the whole body lies along travel, so jets stream aft from palms and boots.
        // Gentle novice hover drift still points thrust down and does not spin the nozzle plume around.
        return velocity.lengthSqr() > 0.16 ? velocity.normalize().reverse() : new Vec3(0, -1, 0);
    }

    /** Both palms emit real flame plumes even at reduced particle detail. */
    private static void handJet(ClientLevel level, Vec3 palm, Vec3 velocity, boolean blue, boolean detail) {
        Vec3 exhaust = exhaust(velocity);
        double length = 1.1 + Math.min(1.5, velocity.length());
        Vec3 tip = palm.add(exhaust.scale(length));
        int outer = blue ? BLUE : FLAME, inner = blue ? WHITE_BLUE : CORE;
        streak(level, palm, tip, 0.15F, 7, outer);
        streak(level, palm, palm.lerp(tip, 0.75), 0.065F, 6, inner);
        mote(level, palm, Vec3.ZERO, 0.12F, 5, inner);
        for (int i = 0; i < (detail ? 6 : 3); i++) {
            Vec3 at = palm.lerp(tip, level.random.nextDouble()).add(jitter(level, 0.08));
            vanilla(level, blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    at, exhaust.scale(0.15).add(jitter(level, 0.025)));
        }
        if (detail) vanilla(level, ParticleTypes.SMOKE, tip, exhaust.scale(0.05));
    }

    /** A compact falling fireball with a long, turbulent wake above the caster. */
    private static void meteor(ClientLevel level, Vec3 feet, Vec3 velocity, boolean blue, boolean detail) {
        Vec3 center = feet.add(0, 0.9, 0);
        Vec3 wake = velocity.lengthSqr() > 0.01 ? velocity.normalize().reverse() : UP;
        int outer = blue ? BLUE : EMBER, inner = blue ? WHITE_BLUE : CORE;
        float phase = level.getGameTime() * 0.25F;
        ring(level, center, wake, 0.5F, 0.85F, 6, 0, inner, 0.8F);
        helix(level, feet, 0.7F, 1.8F, outer, detail ? 10 : 4, 7);
        int strands = detail ? 5 : 2;
        Vec3 sideways = Math.abs(wake.y) > 0.9 ? new Vec3(1, 0, 0) : wake.cross(UP).normalize();
        Vec3 other = wake.cross(sideways).normalize();
        for (int i = 0; i < strands; i++) {
            double angle = phase + i * Math.PI * 2 / strands;
            Vec3 offset = sideways.scale(Math.cos(angle) * 0.6).add(other.scale(Math.sin(angle) * 0.6));
            Vec3 tail = center.add(wake.scale(3 + level.random.nextDouble() * 2)).add(offset.scale(0.4));
            streak(level, center.add(offset), tail, 0.13F, 8, outer);
            vanilla(level, blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    center.add(offset), wake.scale(0.16).add(jitter(level, 0.06)));
            if (detail) mote(level, tail, wake.scale(0.08), 0.08F, 14, EMBER);
        }
    }

    /** Sparks converge on a bright lance held just ahead of the palm. */
    private static void spearCharge(ClientLevel level, Vec3 feet, Vec3 center, float progress, boolean detail) {
        Vec3 direction = center.subtract(feet.add(0, 1.5, 0)).normalize();
        if (direction.lengthSqr() < 0.01) direction = new Vec3(0, 0, 1);
        float radius = 0.25F + progress * 0.35F;
        ring(level, center, direction, radius * 1.2F, radius * 0.6F, 7, 0, FLAME, 0.65F);
        streak(level, center.subtract(direction.scale(0.2)), center.add(direction.scale(0.2 + progress)),
                0.06F + progress * 0.05F, 6, progress > 0.85F ? WHITE_BLUE : CORE);
        int sparks = detail ? 7 : 3;
        for (int i = 0; i < sparks; i++) {
            Vec3 from = center.add(jitter(level, 0.8 + progress * 0.5));
            mote(level, from, center.subtract(from).scale(0.17), 0.04F + progress * 0.02F, 8,
                    i % 3 == 0 ? CORE : EMBER);
        }
        if (detail) vanilla(level, ParticleTypes.SMALL_FLAME, center, jitter(level, 0.015));
    }

    /** A molten sun grows above a clearly marked impact circle, with orbiting solar flares. */
    private static void sun(ClientLevel level, Vec3 center, Vec3 impact, float progress, boolean blue, boolean detail) {
        float radius = 0.45F + progress * 1.55F;
        float phase = level.getGameTime() * 0.13F;
        int outer = blue ? BLUE : FLAME;
        int inner = blue ? WHITE_BLUE : CORE;
        // Layered luminous cores keep this a mass of fire rather than an empty wire sphere.
        mote(level, center, Vec3.ZERO, radius * 0.95F, 7, outer);
        mote(level, center, Vec3.ZERO, radius * 0.65F, 7, inner);
        if (detail) {
            for (int i = 0; i < 5; i++) {
                Vec3 offset = jitter(level, radius * 0.45);
                mote(level, center.add(offset), offset.scale(0.015), radius * 0.3F, 7, inner);
            }
        }
        // Three intersecting coronas leave a readable sphere from every camera angle.
        ring(level, center, UP, radius * 0.9F, radius, 7, 0, outer, 0.8F);
        ring(level, center, new Vec3(Math.cos(phase), 0.4, Math.sin(phase)), radius, radius * 0.85F,
                7, 0, inner, 0.75F);
        if (detail) ring(level, center, new Vec3(-Math.sin(phase), 0.3, Math.cos(phase)), radius * 0.8F,
                radius * 1.05F, 8, 0, EMBER, 0.7F);
        LightningRenderer.star(center, radius * 0.65F, inner, 0.9F, 6, 0);
        int flares = detail ? 12 : 5;
        for (int i = 0; i < flares; i++) {
            double longitude = phase + i * Math.PI * 2 / flares;
            double latitude = Math.sin(longitude * 2 + phase) * 0.8;
            Vec3 radial = new Vec3(Math.cos(longitude) * Math.cos(latitude), Math.sin(latitude),
                    Math.sin(longitude) * Math.cos(latitude));
            Vec3 surface = center.add(radial.scale(radius));
            streak(level, surface, surface.add(radial.scale(0.15 + progress * 0.6)), 0.07F, 7, outer);
            vanilla(level, blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    surface, radial.scale(0.025));
        }
        float zone = 4 + progress * 6;
        ring(level, impact.add(0, 0.08, 0), UP, zone, zone * 0.97F, 8, 0, EMBER, 0.6F);
        if (detail) {
            for (int i = 0; i < 3; i++) {
                double angle = phase + i * Math.PI * 2 / 3;
                Vec3 from = impact.add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
                mote(level, from, center.subtract(from).normalize().scale(0.2), 0.055F, 12, CORE);
            }
        }
    }

    private static void bolt(ClientLevel level, Vec3 origin, Vec3 target, boolean blue, boolean detail) {
        int outer = blue ? BLUE : FLAME, inner = blue ? WHITE_BLUE : CORE;
        streak(level, origin, target, blue ? 0.08F : 0.065F, 10, outer);
        if (detail) streak(level, origin, target, 0.035F, 8, inner);
        double length = origin.distanceTo(target);
        int puffs = Math.clamp((int) (length * (detail ? 1.5 : 0.6)), 2, 40);
        ParticleOptions flame = blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        for (int i = 0; i < puffs; i++) vanilla(level, flame, origin.lerp(target, level.random.nextDouble()), jitter(level, 0.015));
        burst(level, target, UP, outer, detail ? 12 : 5, 0.12F);
        vanilla(level, ParticleTypes.LAVA, target, Vec3.ZERO);
    }

    private static void nova(ClientLevel level, Vec3 origin, float radius, boolean blue, boolean detail) {
        int outer = blue ? BLUE : FLAME, inner = blue ? WHITE_BLUE : CORE;
        ring(level, origin.add(0, 0.15, 0), UP, 0.3F, radius, 14, 0, inner, 0.75F);
        ring(level, origin.add(0, 0.6, 0), UP, 0.5F, radius * 0.9F, 18, 2, outer, 0.6F);
        if (detail) ring(level, origin.add(0, 1.2, 0), UP, 0.4F, radius * 0.7F, 20, 4, EMBER, 0.45F);
        burst(level, origin.add(0, 1, 0), UP, outer, detail ? 24 : 8, 0.25F + radius * 0.02F);
        ParticleOptions flame = blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        int jets = Math.clamp((int) (radius * (detail ? 5 : 2)), 6, 60);
        for (int i = 0; i < jets; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            Vec3 direction = new Vec3(Math.cos(angle), 0.15 + level.random.nextDouble() * 0.25, Math.sin(angle));
            vanilla(level, flame, origin.add(0, 0.5, 0), direction.scale(radius * 0.045));
        }
        if (detail) for (int i = 0; i < 8; i++)
            vanilla(level, ParticleTypes.LARGE_SMOKE, origin.add(jitter(level, radius * 0.4)).add(0, 1, 0), new Vec3(0, 0.08, 0));
    }

    private static Vec3 jitter(ClientLevel level, double scale) {
        return new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(scale * 2);
    }

    private static void vanilla(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.addParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }
}
