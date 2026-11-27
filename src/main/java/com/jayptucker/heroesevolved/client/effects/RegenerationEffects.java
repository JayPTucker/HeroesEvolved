package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The short Regeneration effects ({@code REGEN_*}, {@code BLOOD_THREAD}, {@code HEALING_AURA}): each starts a
 * {@code REGEN_FX} piece here, drawn by {@code setpiece/RegenArt} in blood that moves the wrong way, a heartbeat for
 * every save, and gold where life comes back. No vanilla hearts, crits or dust.
 */
final class RegenerationEffects {
    private RegenerationEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        switch (kind) {
            case REGENERATION, REGEN_MEND, BLOOD_THREAD, REGEN_BOND, HEALING_AURA, REGEN_EJECT, REGEN_RECKLESS,
                    REGEN_SECOND_LIFE, REGEN_REVIVE, REGEN_SEVER -> {
                int hash = (int) (Double.doubleToLongBits(origin.x * 29 + origin.z * 13 + target.y) ^ kind.ordinal() * 2654435761L) & 0xFFFF;
                SetPieceClient.play(Piece.REGEN_FX, origin, target, Mth.clamp(size, .05F, 64), -1, kind.ordinal() << 16 | hash);
                return true;
            }
            default -> { return false; }
        }
    }
}
