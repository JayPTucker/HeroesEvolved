package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.DetailedAbilityEffects.orbit;

final class NuclearEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int ORANGE = 0xFF771C, GOLD = 0xFFB543, HOT = 0xFFF1C1;
    private NuclearEffects() {}
    static boolean play(ClientLevel level, Kind kind, Vec3 feet, float size, boolean detail) {
        switch (kind) {
            case NUCLEAR_EMP -> {
                for (int i = 0; i < (detail ? 3 : 1); i++)
                    ring(level, feet.add(0, 0.2 + i * 0.6, 0), UP, 0.3F, size, 14, i * 2, i == 1 ? HOT : GOLD, 0.65F);
                orbit(level, feet.add(0, 1, 0), new Vec3(1, 0.2, 0), 2.5F, 18, HOT, 0.16F, Vec3.ZERO);
            }
            case NUCLEAR_CHARGE -> {
                Vec3 chest = feet.add(0, 1, 0);
                orbit(level, chest, UP, 0.6F + size * 0.7F, 12, ORANGE, 0.11F, Vec3.ZERO);
                for (int i = 0; i < (detail ? 12 : 4); i++) {
                    double angle = i * 2.4 + level.getGameTime() * 0.08;
                    Vec3 origin = feet.add(Math.cos(angle) * 0.5, i * 0.14, Math.sin(angle) * 0.5);
                    mote(level, origin, new Vec3(0, 0.025 + size * 0.04, 0), 0.07F + size * 0.06F,
                            16, i % 3 == 0 ? HOT : ORANGE);
                }
                if (detail) ring(level, feet.add(0, 0.06, 0), UP, 0.3F, 2 + size * 2, 16, 0, GOLD, 0.3F);
            }
            case NUCLEAR_SHOCKWAVE -> {
                // Eight ticks to reach twenty-four blocks: much faster than the Quake awakening.
                for (int i = 0; i < (detail ? 3 : 1); i++)
                    ring(level, feet.add(0, 0.2 + i * 0.35, 0), UP, 0.4F, size, 8, i, i == 1 ? HOT : ORANGE, 0.85F);
                // Broken condensation fronts follow the fast blast; no stationary laser spokes.
                for (int i = 0; i < (detail ? 6 : 2); i++)
                    add(new PowerParticle(level, PowerParticle.Shape.ARC, feet.add(0, .5 + i * .08, 0), UP,
                            .4F, size, 8, 0, i % 2 == 0 ? 0xE8D7B2 : GOLD, .55F)
                            .arc(i * 1.05F, .02F, .8F, .15F));
                burst(level, feet.add(0, 1, 0), UP, HOT, detail ? 16 : 6, 0.45F);
            }
            case NUCLEAR_FALLOUT -> {
                for (int i = 0; i < (detail ? 28 : 8); i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double r = Math.sqrt(level.random.nextDouble()) * size;
                    mote(level, feet.add(Math.cos(a) * r, level.random.nextDouble() * 5, Math.sin(a) * r),
                            new Vec3(0.015, -0.025, 0.01), 0.055F, 35, i % 4 == 0 ? 0xC4BE78 : 0x696158);
                }
            }
            default -> { return false; }
        }
        return true;
    }
}
