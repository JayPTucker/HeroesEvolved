package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The older nuclear effects ({@code NUCLEAR_CHARGE}, {@code NUCLEAR_SHOCKWAVE}, {@code NUCLEAR_FALLOUT}), drawn in
 * {@code RadiationArt}'s language: a charge is the swelling of light before a blast, a shockwave is the blast's shape at
 * that size, fallout is ash drifting down.
 */
final class NuclearEffects {
    private NuclearEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 feet, float size, boolean detail) {
        int seed = (int) (feet.x * 13 + feet.z * 29 + level.getGameTime());
        switch (kind) {
            case NUCLEAR_CHARGE -> SetPieceClient.play(Piece.RAD_PULSE, feet.add(0, .6, 0), feet.add(0, 1.6, 0), Mth.clamp(.8F + size * .6F, .5F, 4), -1, seed);
            case NUCLEAR_SHOCKWAVE -> SetPieceClient.play(Piece.RAD_LEAP, feet, feet.add(0, 4, 0), Mth.clamp(size, 2, 24), -1, seed);
            case NUCLEAR_FALLOUT -> SetPieceClient.play(Piece.RAD_FALLOUT, feet, feet.add(0, 1, 0), Mth.clamp(size, 2, 24), -1, seed);
            default -> { return false; }
        }
        return true;
    }
}
