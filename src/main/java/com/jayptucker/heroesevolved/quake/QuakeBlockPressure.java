package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Focused beam pressure: one block at a time, hardness-scaled cracks, no bulk terrain loop. */
public final class QuakeBlockPressure {
    private record Pressure(ServerLevel level, BlockPos pos, BlockState state, int ticks, int animation, long updated) {}
    private static final Map<UUID, Pressure> ACTIVE = new HashMap<>();
    private static int nextAnimation = 1_000_000;
    private QuakeBlockPressure() {}
    public static void tick(ServerPlayer player, BlockPos target) {
        ServerLevel level = player.serverLevel();
        Pressure old = ACTIVE.get(player.getUUID());
        if (target == null || !player.mayBuild() || !level.hasChunkAt(target)) { clear(player); return; }
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);
        if (hardness < 0 || state.isAir() || !state.getFluidState().isEmpty()) { clear(player); return; }
        boolean same = old != null && old.level == level && old.pos.equals(target) && old.state.equals(state);
        if (!same) {
            clear(player);
            if (!WorldMutationPolicy.canEdit(player, level, target)) return;
            old = new Pressure(level, target.immutable(), state, 0, nextAnimation++, Long.MIN_VALUE);
        }
        long now = level.getGameTime();
        if (old.updated == now) return;
        int ticks = old.ticks + 1;
        com.jayptucker.heroesevolved.expansion.FacilityService.technique(player, target, "fracture");
        int required = Math.clamp((int) Math.ceil(16 + hardness * 12), 20, 240);
        if (com.jayptucker.heroesevolved.expansion.PowerReactions.resonating(level, target)) {
            required = Math.max(10, required / 3);
            com.jayptucker.heroesevolved.expansion.FacilityService.discovery(player, "seam_chorus");
        }
        level.destroyBlockProgress(old.animation, old.pos, Math.min(9, ticks * 10 / required));
        ACTIVE.put(player.getUUID(), new Pressure(level, old.pos, state, ticks, old.animation, now));
        if (ticks >= required) {
            if (WorldMutationPolicy.canEdit(player, level, target)) level.destroyBlock(target, true, player);
            clear(player);
        }
    }
    public static void clear(ServerPlayer player) {
        Pressure old = ACTIVE.remove(player.getUUID());
        if (old != null) old.level.destroyBlockProgress(old.animation, old.pos, -1);
    }
}
