package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

/** Local cracks become crumbling blocks as heat escapes during a nuclear charge. */
public final class RadiationDecay {
    private record Crumbling(ServerLevel level, BlockPos pos, BlockState original, int animation, int stage) {}
    private static final Map<UUID, LinkedHashMap<BlockPos, Crumbling>> ACTIVE = new HashMap<>();
    private static int nextAnimation = Integer.MIN_VALUE;
    private RadiationDecay() {}
    public static void tick(ServerPlayer player, float progress, boolean awakening) {
        ServerLevel level = player.serverLevel();
        if (!player.mayBuild() || TimeIntroService.isCity(level) || TemporalSnapshotService.isTemporalDimension(level)) {
            clear(player); return;
        }
        var blocks = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>());
        int radius = (int) (2 + Math.clamp(progress, 0, 1) * (awakening ? 6 : 4));
        // Sampling and active cracks are bounded, regardless of the final blast's radius.
        for (int sample = 0; sample < 16 && blocks.size() < 24; sample++) {
            BlockPos pos = player.blockPosition().offset(level.random.nextInt(radius * 2 + 1) - radius,
                    level.random.nextInt(5) - 3, level.random.nextInt(radius * 2 + 1) - radius);
            if (pos.distSqr(player.blockPosition()) > radius * radius || !level.hasChunkAt(pos)
                    || blocks.containsKey(pos) || !allowed(player, level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0
                    || level.getBlockEntity(pos) != null) continue;
            blocks.put(pos, new Crumbling(level, pos, state, nextAnimation++, 0));
        }
        Iterator<Crumbling> iterator = blocks.values().iterator();
        List<Crumbling> advance = new ArrayList<>();
        while (iterator.hasNext()) {
            Crumbling entry = iterator.next();
            iterator.remove();
            if (entry.level != level || entry.pos.distSqr(player.blockPosition()) > 100
                    || !level.getBlockState(entry.pos).equals(entry.original) || !allowed(player, level, entry.pos)) {
                entry.level.destroyBlockProgress(entry.animation, entry.pos, -1); continue;
            }
            int stage = entry.stage + 2;
            level.sendParticles(ParticleTypes.SMOKE, entry.pos.getX() + .5, entry.pos.getY() + .8,
                    entry.pos.getZ() + .5, 1, .15, .1, .15, .015);
            if (stage >= 10) {
                level.destroyBlockProgress(entry.animation, entry.pos, -1);
                if (!NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, entry.pos, entry.original, player)).isCanceled())
                    level.destroyBlock(entry.pos, false, player);
            } else {
                level.destroyBlockProgress(entry.animation, entry.pos, stage);
                advance.add(new Crumbling(level, entry.pos, entry.original, entry.animation, stage));
            }
        }
        for (Crumbling entry : advance) blocks.put(entry.pos, entry);
    }
    private static boolean allowed(ServerPlayer owner, ServerLevel level, BlockPos pos) {
        // Retain the block directly under the owner, matching the final blast's footing.
        BlockPos delta = pos.subtract(owner.blockPosition());
        return !(delta.getY() < 0 && delta.getX() * delta.getX() + delta.getZ() * delta.getZ() <= 2)
                && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(owner, pos);
    }
    public static void clear(ServerPlayer player) { clear(player.getUUID()); }
    private static void clear(UUID id) {
        var blocks = ACTIVE.remove(id);
        if (blocks != null) blocks.values().forEach(entry -> entry.level.destroyBlockProgress(entry.animation, entry.pos, -1));
    }
    public static void clearAll() { for (UUID id : List.copyOf(ACTIVE.keySet())) clear(id); }
}
