package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The older short Matter Assembly effects ({@code MATTER_*}), which gifted mobs, the awakening rescues and the ward
 * still send, drawn in {@code AssemblyArt}'s language: each starts the matching piece here on the client, so a gifted
 * builder's construct, shard and break look like the player's. The player's own kit sends those pieces directly.
 * <ul>
 *     <li>{@code MATTER_WEAVE} (from, centre, size): a construct folding together at the centre.</li>
 *     <li>{@code MATTER_SHARD} (from, to): a crystal shard crossing, and the flash where it lands.</li>
 *     <li>{@code MATTER_COLLAPSE} (at, size): matter breaking apart, a flash, ring and chips.</li>
 *     <li>{@code MATTER_SHIELD} (at, facing, size): a pane assembling toward the facing, with a flash on its face.</li>
 * </ul>
 */
final class ConstructPowerEffects {
    private ConstructPowerEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float scale, boolean detail, int density) {
        switch (kind) {
            case MATTER_WEAVE, MATTER_SHARD, MATTER_COLLAPSE, MATTER_SHIELD -> { }
            default -> { return false; }
        }
        if (!valid(origin) || !valid(target) || !Float.isFinite(scale)) return true;
        int seed = (int) (origin.x * 37 + origin.z * 11 + level.getGameTime());
        switch (kind) {
            case MATTER_WEAVE -> SetPieceClient.play(Piece.MATTER_RISE, target, origin.subtract(0, 1.3, 0), Mth.clamp(scale * 1.2F, .6F, 4), -1, seed);
            case MATTER_SHARD -> {
                Vec3 end = origin.distanceTo(target) > 24 ? origin.add(target.subtract(origin).normalize().scale(24)) : target;
                SetPieceClient.play(Piece.MATTER_SHARD, origin, end, Mth.clamp(scale, 1, 2.5F), -1, seed);
                SetPieceClient.play(Piece.MATTER_BOUNCE, end, origin, Mth.clamp(scale, 1, 3), -1, seed + 1);
            }
            case MATTER_COLLAPSE -> SetPieceClient.play(Piece.MATTER_BOUNCE, origin, origin.add(0, 1, 0), Mth.clamp(scale, 1, 5), -1, seed);
            case MATTER_SHIELD -> {
                Vec3 facing = target.subtract(origin);
                Vec3 ahead = facing.lengthSqr() < 1e-4 ? origin.add(0, 0, 1) : origin.add(facing.normalize());
                SetPieceClient.play(Piece.MATTER_PANEL, ahead.subtract(0, 1, 0), ahead.add(facing).subtract(0, 1, 0), 0, -1, seed);
                SetPieceClient.play(Piece.MATTER_BOUNCE, ahead, ahead.add(facing), Mth.clamp(scale, 1, 4), -1, seed + 1);
            }
            default -> { }
        }
        return true;
    }

    private static boolean valid(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}
