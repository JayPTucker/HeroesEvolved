package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The electromagnetic pulse. Redstone within sixteen blocks goes dead for ten seconds, as it always
 * has; through {@link GridDisruption} the same field now drains and stops Forge Energy machines and
 * seizes Create's shafts. Nothing is replaced and no block entity loses data.
 */
public final class RadiationEmp {
    public static final int RADIUS = 16, DURATION = 200;
    private RadiationEmp() {}
    public static boolean jammed(ServerLevel level, BlockPos pos) { return GridDisruption.jammed(level, pos); }
    public static GridDisruption.Pulse pulse(ServerPlayer player) {
        GridDisruption.Pulse pulse = GridDisruption.emp(player, player.serverLevel(), player.blockPosition(), RADIUS, DURATION);
        // Drawn by RadiationService's RAD_EMP set piece.
        return pulse;
    }
    public static void clear() { GridDisruption.clear(); }
}
