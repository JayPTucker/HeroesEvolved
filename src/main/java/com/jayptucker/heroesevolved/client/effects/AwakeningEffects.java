package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.DetailedAbilityEffects.orbit;

/** Each awakening has a rising charge silhouette and a separate full-size release. */
final class AwakeningEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int CYAN = 0xB8EAFF, BLUE = 0x5AABFF, VIOLET = 0xBE8BFF;
    private static final int WHITE = 0xECF9FF, RED = 0xF76B87, GOLD = 0xFFE1A4;
    private AwakeningEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 feet, float size, boolean detail, int density) {
        Vec3 chest = feet.add(0, 1, 0);
        switch (kind) {
            case QUAKE_AWAKEN_CHARGE -> {
                float radius = 0.8F + size * 2;
                ring(level, feet.add(0, 0.08, 0), UP, radius, 0.35F, 12, 0, CYAN, 0.65F);
                orbit(level, chest.add(-0.5, 0, 0), new Vec3(1, 0.2, 0), 0.25F + size * 0.25F, 10, WHITE, 0.35F, Vec3.ZERO);
                orbit(level, chest.add(0.5, 0, 0), new Vec3(1, -0.2, 0), 0.25F + size * 0.25F, 10, CYAN, -0.35F, Vec3.ZERO);
                if (detail) inward(level, chest, radius, CYAN, density / 2);
            }
            case QUAKE_AWAKEN_RELEASE -> {
                for (int i = 0; i < (detail ? 3 : 1); i++) {
                    ring(level, feet.add(0, 0.12 + i * 0.18, 0), UP, 0.4F, size,
                            40, i * 3, i == 1 ? WHITE : CYAN, 0.85F - i * 0.18F);
                }
                ring(level, chest, new Vec3(1, 0, 0), 0.5F, size * 0.65F, 24, 0, WHITE, 0.35F);
                ring(level, chest, new Vec3(0, 0, 1), 0.5F, size * 0.65F, 24, 0, CYAN, 0.35F);
                spokes(level, feet.add(0, 0.3, 0), size, detail ? 24 : 10, CYAN, 34);
                if (detail) burst(level, chest, UP, WHITE, density * 2, 0.3F);
            }
            case FLIGHT_AWAKEN_CHARGE -> {
                ring(level, feet.add(0, 0.05, 0), UP, 1.2F + size, 0.4F, 12, 0, WHITE, 0.55F);
                helix(level, feet, 0.8F + size * 0.6F, 2.5F + size * 2, CYAN, detail ? 18 : 6, 14);
                if (detail) inward(level, feet.add(0, 0.4, 0), 2.2F, WHITE, 12);
            }
            case FLIGHT_AWAKEN_RELEASE -> {
                ring(level, feet.add(0, 0.15, 0), UP, 0.4F, size, 25, 0, WHITE, 0.8F);
                FlightEffects.burst(level, chest, UP, size * .7F, detail);
                for (int i = 0; i < (detail ? 5 : 2); i++)
                    add(new PowerParticle(level, PowerParticle.Shape.ARC, feet.add(0, i * 2.2, 0), UP,
                            .5F + i * .2F, 1.4F + i * .3F, 20, i * 2, WHITE, .3F)
                            .arc(i * 2.4F, .14F, 2.4F, .06F));
                helix(level, feet, 1.5F, 13, WHITE, detail ? 48 : 12, 35);
                if (detail) spokes(level, chest, 7, 18, WHITE, 22);
            }
            case ELECTRIC_AWAKEN_CHARGE -> {
                orbit(level, chest, UP, 0.55F + size, 10, BLUE, 0.35F, Vec3.ZERO);
                orbit(level, chest, new Vec3(1, 0.3, 0), 0.65F + size, 10, WHITE, -0.35F, Vec3.ZERO);
                int count = detail ? 6 : 3;
                for (int i = 0; i < count; i++) {
                    double angle = i * Math.PI * 2 / count + level.getGameTime() * 0.13;
                    Vec3 point = chest.add(Math.cos(angle) * (1.5 + size),
                            Math.sin(angle * 2) * 1.5, Math.sin(angle) * (1.5 + size));
                    bolt(level, point, chest, BLUE);
                }
            }
            case ELECTRIC_AWAKEN_RELEASE -> {
                ring(level, feet.add(0, 0.2, 0), UP, 0.4F, 11, 22, 0, BLUE, 0.85F);
                bolt(level, feet.add(0, 19, 0), chest, WHITE);
                for (int i = 0; i < (detail ? 10 : 4); i++) {
                    double angle = i * Math.PI * 2 / (detail ? 10 : 4);
                    Vec3 point = feet.add(Math.cos(angle) * 7, 0.2, Math.sin(angle) * 7);
                    bolt(level, chest, point.add(0, 3, 0), BLUE);
                    bolt(level, point.add(0, 3, 0), point, WHITE);
                }
                for (Vec3 axis : new Vec3[] { UP, new Vec3(1, 0, 0), new Vec3(0, 0, 1) })
                    orbit(level, chest, axis, 2.8F, 30, BLUE, 0.2F, Vec3.ZERO);
                if (detail) burst(level, chest, UP, WHITE, density * 2, 0.28F);
            }
            case PSYCHIC_AWAKEN_CHARGE -> {
                ring(level, feet.add(0, 0.12, 0), UP, 2 + size, 0.4F, 14, 0, VIOLET, 0.65F);
                orbit(level, chest, new Vec3(0.3, 1, 0.4), 0.8F + size, 14, VIOLET, 0.16F, Vec3.ZERO);
                if (detail) inward(level, chest, 3, GOLD, density / 2);
            }
            case PSYCHIC_AWAKEN_RELEASE -> {
                ring(level, feet.add(0, 0.2, 0), UP, 0.5F, 10, 35, 0, VIOLET, 0.75F);
                ring(level, chest, new Vec3(0, 0, 1), 0.3F, 4, 30, 0, GOLD, 0.3F);
                helix(level, feet, 3, 5, VIOLET, detail ? 48 : 12, 36);
                for (int i = 0; i < (detail ? 4 : 2); i++)
                    orbit(level, feet.add(0, 0.5 + i, 0), UP, 2 + i, 32, VIOLET, 0.14F + i * 0.02F, Vec3.ZERO);
            }
            case REGEN_AWAKEN_CHARGE -> {
                helix(level, feet, 0.5F, 2, size < 0.5 ? RED : GOLD, detail ? 16 : 6, 18);
                ring(level, chest, new Vec3(0, 0, 1), 0.6F, 0.1F, 12, 0, GOLD, 0.55F);
                if (detail) inward(level, chest, 1.7F, RED, density / 3);
            }
            case REGEN_AWAKEN_RELEASE -> {
                ring(level, feet.add(0, 0.15, 0), UP, 0.3F, 8, 35, 0, GOLD, 0.7F);
                ring(level, feet.add(0, 0.2, 0), UP, 0.2F, 7, 36, 5, RED, 0.5F);
                for (int i = 0; i < (detail ? 10 : 4); i++) {
                    double angle = i * Math.PI * 2 / (detail ? 10 : 4);
                    Vec3 petal = chest.add(Math.cos(angle) * 1.2, 0, Math.sin(angle) * 1.2);
                    orbit(level, petal, new Vec3(Math.sin(angle), 0.5, Math.cos(angle)),
                            1.4F, 28, GOLD, 0.14F, new Vec3(0, 0.03, 0));
                }
                helix(level, feet, 1, 4, GOLD, detail ? 36 : 10, 35);
            }
            default -> { return false; }
        }
        return true;
    }

    private static void inward(ClientLevel level, Vec3 center, float radius, int color, int count) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + level.getGameTime() * 0.13;
            Vec3 offset = new Vec3(Math.cos(a) * radius, Math.sin(a * 3) * 0.6, Math.sin(a) * radius);
            mote(level, center.add(offset), offset.scale(-0.08), 0.07F, 12, color);
        }
    }
    private static void spokes(ClientLevel level, Vec3 center, float radius, int count, int color, int ticks) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            Vec3 direction = new Vec3(Math.cos(a), 0.025, Math.sin(a));
            streak(level, center.add(direction.scale(0.5)), center.add(direction.scale(radius)), 0.035F, ticks, color);
        }
    }
    private static void bolt(ClientLevel level, Vec3 from, Vec3 to, int color) {
        // The awakening uses the same branched lightning as the Electrokinesis kit.
        ElectroEffects.bolt(level, from, to, color == WHITE ? 0.09F : 0.06F, 6, 0, 1F, true, 0.22, 1);
    }
}
