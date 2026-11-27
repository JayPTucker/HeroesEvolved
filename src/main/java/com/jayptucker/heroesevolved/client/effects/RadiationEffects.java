package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The older short Induced Radioactivity effects ({@code RADIATION_*} and {@code NUCLEAR_EMP}), which gifted mobs, gifted
 * villagers, the facility and the story still send, drawn in {@code RadiationArt}'s language: each starts the matching
 * piece here on the client, so a radioactive mob's heat and beam look like the player's.
 */
final class RadiationEffects {
    private RadiationEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        int seed = (int) (origin.x * 31 + origin.z * 17 + level.getGameTime());
        switch (kind) {
            case RADIATION_CONE -> SetPieceClient.play(Piece.RAD_HEAT, origin, target, Mth.clamp(size, .6F, 1.6F), -1, seed);
            case RADIATION_BEAM, NUCLEAR_RAY -> SetPieceClient.play(Piece.RAD_BEAM, origin, target,
                    kind == Kind.NUCLEAR_RAY ? 1.2F : Mth.clamp(size * .6F, .5F, 1.6F), -1, seed);
            case RADIATION_PULSE -> SetPieceClient.play(Piece.RAD_PULSE, origin, target, Mth.clamp(size, .4F, 6), -1, seed);
            case RADIATION_FIELD -> SetPieceClient.play(Piece.RAD_ZONE, origin, origin.add(0, 1, 0), Mth.clamp(size, 1, 12), -1, seed);
            case RADIATION_DOSE -> SetPieceClient.play(Piece.RAD_DOSE, origin, target, Mth.clamp(size, .4F, 4), -1, seed);
            case RADIATION_CHAIN -> SetPieceClient.play(Piece.RAD_CHAIN, origin, target, Mth.clamp(size, 1, 16), -1, seed);
            case NUCLEAR_EMP -> SetPieceClient.play(Piece.RAD_EMP, origin, origin.add(0, 1, 0), Mth.clamp(size, 1, 24), -1, seed);
            default -> { return false; }
        }
        return true;
    }
}
