package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;
import static com.jayptucker.heroesevolved.client.effects.DetailedAbilityEffects.orbit;

/** Cold has its own silhouette: prismatic lances, rime, crystal fragments and wind-driven snow. */
final class CryoEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int ICE = 0x9DDEFF, WHITE = 0xEDF9FF, BLUE = 0x5AA8E8;
    private CryoEffects() {}
    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case CRYO_BOLT -> {
                Vec3 direction = target.subtract(origin).normalize();
                streak(level, origin, target, 0.055F, 12, WHITE);
                Vec3 across = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : UP).normalize().scale(0.08);
                if (detail) {
                    streak(level, origin.add(across), target, 0.035F, 15, ICE);
                    streak(level, origin.subtract(across), target, 0.035F, 15, BLUE);
                }
                int rings = Math.clamp((int) origin.distanceTo(target) / 3, 1, 14);
                for (int i = 0; i < rings; i++) {
                    Vec3 point = origin.lerp(target, (i + 1.0) / (rings + 1));
                    ring(level, point, direction, 0.16F, 0.4F, 10, i / 3, ICE, 0.6F);
                    if (detail) mote(level, point, new Vec3(0, -0.04, 0), 0.07F, 20, WHITE);
                }
                crystals(level, target, 0.5F, detail ? 10 : 4, 14);
            }
            case CRYO_CHILL -> {
                float height = (float) Math.clamp(target.y - origin.y, 0.5, 6);
                ring(level, origin.add(0, 0.08, 0), UP, size * 0.5F, size * 0.8F, 18, 0, ICE, 0.35F);
                for (int i = 0; i < (detail ? 8 : 3); i++) {
                    double a = i * 2.4 + level.getGameTime() * 0.1;
                    Vec3 p = origin.add(Math.cos(a) * size * 0.6, height * i / 8, Math.sin(a) * size * 0.6);
                    mote(level, p, new Vec3(0.01 * Math.cos(a), -0.025, 0.01 * Math.sin(a)), 0.045F, 18, WHITE);
                }
            }
            case CRYO_FREEZE -> {
                float height = (float) Math.clamp(target.y - origin.y, 0.5, 8);
                for (int i = 0; i < (detail ? 5 : 2); i++)
                    ring(level, origin.add(0, height * i / (detail ? 4 : 1), 0), UP,
                            size * 1.7F, size * 0.65F, 18, i * 2, i % 2 == 0 ? ICE : WHITE, 0.7F);
                crystals(level, origin.add(0, height * 0.4, 0), size, detail ? 16 : 6, 16);
            }
            case CRYO_SHATTER -> {
                Vec3 center = origin.lerp(target, 0.5);
                crystals(level, center, Math.max(1.5F, size * 2), detail ? 28 : 10, 22);
                burst(level, center, UP, WHITE, detail ? 24 : 8, 0.23F);
                ring(level, center, UP, 0.2F, size * 2.2F, 12, 0, ICE, 0.7F);
            }
            case CRYO_ICE_GROWTH -> {
                float height = (float) Math.max(1, target.y - origin.y);
                ring(level, origin.add(0, 0.05, 0), UP, 0.2F, 1.1F, 16, 0, ICE, 0.6F);
                for (int i = 0; i < (detail ? 6 : 3); i++) {
                    double a = i * Math.PI / 3;
                    Vec3 base = origin.add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5);
                    streak(level, base, base.add(-Math.cos(a) * 0.2, height + 0.3, -Math.sin(a) * 0.2),
                            0.055F, 16, i % 2 == 0 ? WHITE : BLUE);
                }
            }
            case CRYO_BLIZZARD -> {
                float phase = level.getGameTime() * 0.05F;
                for (int layer = 0; layer < (detail ? 4 : 2); layer++)
                    orbit(level, origin.add(0, 0.5 + layer * 1.4, 0), new Vec3(0.05, 1, 0.1),
                            size * (0.45F + layer * 0.15F), 20, ICE, 0.08F, target.subtract(origin));
                for (int i = 0; i < (detail ? 75 : 18); i++) {
                    double a = i * 2.39996 + phase, radius = Math.sqrt((i + 0.5) / (detail ? 75 : 18)) * size;
                    Vec3 p = origin.add(Math.cos(a) * radius, level.random.nextDouble() * 7, Math.sin(a) * radius);
                    mote(level, p, new Vec3(-Math.sin(a) * 0.20, -0.09, Math.cos(a) * 0.20),
                            0.045F + level.random.nextFloat() * 0.04F, 24, i % 4 == 0 ? BLUE : WHITE);
                }
            }
            case CRYO_AWAKEN_CHARGE -> {
                ring(level, origin.add(0, 0.08, 0), UP, 1 + size * 3, 0.5F, 14, 0, ICE, 0.6F);
                helix(level, origin, 0.8F + size, 2.3F, WHITE, detail ? 18 : 6, 16);
                crystals(level, origin.add(0, 0.8, 0), 0.4F + size * 0.3F, detail ? 8 : 3, 12);
            }
            case CRYO_AWAKEN_RELEASE -> {
                for (int i = 0; i < (detail ? 3 : 1); i++)
                    ring(level, origin.add(0, 0.12 + i * 0.35, 0), UP, 0.4F, size + i,
                            30, i * 3, i == 1 ? WHITE : ICE, 0.7F);
                crystals(level, origin.add(0, 1, 0), size, detail ? 36 : 12, 30);
                helix(level, origin, 3, 6, WHITE, detail ? 48 : 12, 35);
            }
            default -> { return false; }
        }
        return true;
    }
    private static void crystals(ClientLevel level, Vec3 center, float radius, int count, int ticks) {
        for (int i = 0; i < count; i++) {
            double a = i * 2.39996;
            Vec3 radial = new Vec3(Math.cos(a), Math.sin(i * 1.7) * 0.7, Math.sin(a)).normalize();
            Vec3 end = center.add(radial.scale(radius)), base = end.subtract(radial.scale(0.25));
            add(new PowerParticle(level, PowerParticle.Shape.CRYSTAL, end, radial,
                    .10F, .025F, ticks, i % 3, i % 2 == 0 ? ICE : WHITE, .85F)
                    .moving(radial.scale(.07).add(0, -.015, 0)));
            mote(level, end, radial.scale(0.08), 0.055F, ticks, WHITE);
        }
    }
}
