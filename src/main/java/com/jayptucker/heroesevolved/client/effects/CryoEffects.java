package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The short Cryokinesis effects ({@code CRYO_*}). Solid ice that stands in the world (a lance, armour, a cocoon, growing
 * spires, a shattering shell) is drawn as real ice meshes ({@link CryoSkillVisuals}, {@link CryoMeshVisuals}); the cold
 * itself (frost rims, feathered veins, chips, snow) is a {@code CRYO_FX} piece drawn by {@code setpiece/CryoArt} in the
 * power's one palette. Nothing here is a particle.
 */
final class CryoEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private CryoEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case CRYO_LANCE_FLIGHT, CRYO_ARMOR, CRYO_COCOON -> CryoSkillVisuals.play(level, kind, origin, target, size);
            case CRYO_LANCE_FORGE, CRYO_SNAP, CRYO_CONSTELLATION -> {
                CryoSkillVisuals.play(level, kind, origin, target, size);
                fx(kind, origin, target, size);
            }
            case CRYO_REBOUND -> {
                CryoSkillVisuals.play(level, kind, origin, target, size);
                CryoMeshVisuals.shatter(level, origin, 1, detail ? 8 : 3);
            }
            case CRYO_LOOM -> {
                CryoSkillVisuals.play(level, kind, origin, target, size);
                CryoMeshVisuals.ribbon(level, origin, target, Mth.clamp(size, .5F, 3));
                CryoMeshVisuals.sculptCell(level, target.add(0, -.05, 0));
                fx(kind, origin.add(0, .03, 0), target.add(0, .03, 0), size);
            }
            case CRYO_RIBBON -> {
                CryoMeshVisuals.ribbon(level, origin, target, size);
                fx(kind, origin.add(0, .035, 0), target.add(0, .035, 0), size);
            }
            case CRYO_RIME_STREAM -> {
                if (detail) CryoMeshVisuals.grow(level, target, target.add(target.subtract(origin).normalize().scale(.25)).add(0, .12, 0), .07F);
                fx(kind, origin, target, size);
            }
            case CRYO_SCULPT -> {
                CryoMeshVisuals.sculptCell(level, origin);
                float radius = .28F + Mth.clamp(size, 0, 1) * .24F;
                Vec3 axis = target.subtract(origin);
                if (axis.lengthSqr() < .1) axis = UP;
                CryoMeshVisuals.grow(level, origin, origin.add(axis), radius);
                fx(kind, origin, target, size);
            }
            case CRYO_RIDE -> {
                Vec3 back = target.subtract(origin).lengthSqr() > .01 ? target.subtract(origin).normalize().reverse() : new Vec3(0, 0, -1);
                Vec3 lateral = new Vec3(-back.z, 0, back.x);
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 skate = origin.add(lateral.scale(s * .2));
                    CryoMeshVisuals.grow(level, skate.add(back.scale(.25)).add(0, -.05, 0), skate.add(back.scale(.5)).add(0, .18, 0), .075F);
                }
                fx(kind, origin, target, size);
            }
            case CRYO_PRISON -> {
                float amount = Mth.clamp(size, 0, 1), height = (float) Mth.clamp(target.y - origin.y, .6, 6);
                for (int i = 0; i < (detail ? 6 : 3); i++) {
                    double a = i * Math.PI * 2 / (detail ? 6 : 3);
                    Vec3 base = origin.add(Math.cos(a) * .42, .02, Math.sin(a) * .42);
                    CryoMeshVisuals.grow(level, base, base.add(-Math.cos(a) * .12, height * (.25 + amount * .75), -Math.sin(a) * .12), .13F);
                }
                fx(kind, origin, target, size);
            }
            case CRYO_FRACTURE -> {
                CryoMeshVisuals.shatter(level, origin.add(0, .8, 0), size, detail ? 24 : 8);
                fx(kind, origin, target, size);
            }
            case CRYO_WHITEOUT -> {
                // Spires of real ice at the edge; the frost and the snow are drawn.
                int spires = detail ? 5 : 3;
                for (int i = 0; i < spires; i++) {
                    double a = i * Math.PI * 2 / spires + level.getGameTime() * .008;
                    Vec3 edge = origin.add(Math.cos(a) * size, .06, Math.sin(a) * size);
                    CryoMeshVisuals.grow(level, edge, edge.add(0, .8 + (i * 37 % 10) * .08, 0), .22F);
                }
                fx(kind, origin, target, size);
            }
            case CRYO_SHATTER -> {
                CryoMeshVisuals.shatter(level, origin.lerp(target, .5), Math.max(1, size), detail ? 18 : 7);
                fx(kind, origin, target, size);
            }
            case CRYO_ICE_GROWTH -> {
                CryoMeshVisuals.grow(level, origin, target.add(0, .15, 0), Math.max(.15F, size * .38F));
                fx(kind, origin, target, size);
            }
            case CRYO_BOLT, CRYO_CHILL, CRYO_FREEZE, CRYO_BRANCH, CRYO_BLIZZARD, CRYO_AWAKEN_CHARGE, CRYO_AWAKEN_RELEASE ->
                    fx(kind, origin, target, size);
            default -> { return false; }
        }
        return true;
    }

    /** Starts CryoArt's drawing of this effect here (the kind in the seed's high bits, a stable hash in its low bits). */
    private static void fx(Kind kind, Vec3 origin, Vec3 target, float size) {
        int hash = (int) (Double.doubleToLongBits(origin.x * 31 + origin.z * 17 + target.y) ^ kind.ordinal() * 2654435761L) & 0xFFFF;
        SetPieceClient.play(Piece.CRYO_FX, origin, target, Mth.clamp(size, .05F, 64), -1, kind.ordinal() << 16 | hash);
    }
}
