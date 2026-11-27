package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The older short Telekinesis effects ({@code TK_*}), which gifted mobs still send, drawn in the far hand's language: each
 * one starts the matching {@code KineticArt} piece here on the client, so a telekinetic mob's throw, pin and storm look
 * like the player's. The player's own kit sends those pieces directly.
 */
final class TelekinesisEffects {
    private TelekinesisEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case TK_GATHER -> SetPieceClient.play(Piece.KINETIC_BECKON, origin, target, 2, -1, -1);
            case TK_ORBIT -> SetPieceClient.play(Piece.KINETIC_SHOT, target, target.add(0, 1, 0), 1, -1, -1);
            case TK_LAUNCH -> SetPieceClient.play(Piece.KINETIC_THROW, origin, target, Mth.clamp(size, .5F, 2.5F), -1, -1);
            case TK_IMPACT -> SetPieceClient.play(Piece.KINETIC_IMPACT, origin, target, Mth.clamp(size, .5F, 6), -1, -1);
            case TK_GRIP -> {
                SetPieceClient.play(Piece.KINETIC_SEND, origin, target, 1, -1, -1);
                SetPieceClient.play(Piece.KINETIC_IMPACT, target, origin, Mth.clamp(size, .5F, 2.5F), -1, -1);
            }
            case TK_WAVE -> SetPieceClient.play(Piece.KINETIC_IMPACT, origin, target, Mth.clamp(size, .5F, 16), -1, -1);
            case TK_BARRIER -> SetPieceClient.play(Piece.KINETIC_IMPACT, origin, target, Mth.clamp(size, 1, 6), -1, -1);
            case TK_WELL -> SetPieceClient.play(Piece.KINETIC_ROUND_UP, origin, target, Mth.clamp(size, 1.5F, 12), -1, -1);
            case TK_STEP -> SetPieceClient.play(Piece.KINETIC_IMPACT, origin, origin.add(target.subtract(origin).reverse()), 1, -1, -1);
            default -> { return false; }
        }
        return true;
    }
}
