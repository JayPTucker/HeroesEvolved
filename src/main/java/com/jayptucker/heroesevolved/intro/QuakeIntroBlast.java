package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.entity.QuakeDebrisEntity;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A real, expanding spherical terrain fracture, bounded to twelve blocks and 768 positions per tick. */
final class QuakeIntroBlast {
    static final int RADIUS = 12, DURATION = 40;
    private static final List<BlockPos> OFFSETS = offsets();
    private final BlockPos center;
    private int cursor;
    private int broken;

    QuakeIntroBlast(BlockPos center) { this.center = center.immutable(); }
    int broken() { return broken; }
    boolean finished() { return cursor >= OFFSETS.size(); }

    private static List<BlockPos> offsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int y = -RADIUS; y <= RADIUS; y++) for (int z = -RADIUS; z <= RADIUS; z++)
            for (int x = -RADIUS; x <= RADIUS; x++) {
                if (x * x + y * y + z * z <= RADIUS * RADIUS) offsets.add(new BlockPos(x, y, z));
            }
        offsets.sort(Comparator.comparingDouble(pos -> pos.distSqr(BlockPos.ZERO)));
        return List.copyOf(offsets);
    }

    boolean advance(ServerPlayer player, int tick) {
        ServerLevel level = player.serverLevel();
        if (TimeIntroService.isCity(level) || TemporalSnapshotService.isTemporalDimension(level)
                || !player.mayBuild()) {
            cursor = OFFSETS.size();
            return true;
        }
        double radius = Math.min(RADIUS, Math.max(0, tick) * RADIUS / (double) DURATION);
        int budget = 768;
        while (cursor < OFFSETS.size() && budget-- > 0) {
            BlockPos offset = OFFSETS.get(cursor);
            if (offset.distSqr(BlockPos.ZERO) > radius * radius) break;
            cursor++;
            // Keep a small footing under the owner instead of dropping them into their own crater.
            if (offset.getY() < 0 && offset.getX() * offset.getX() + offset.getZ() * offset.getZ() <= 2) continue;
            BlockPos pos = center.offset(offset);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.mayInteract(player, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) continue;
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) continue;
            if (level.destroyBlock(pos, true, player)) {
                broken++;
                if (broken % 12 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.08);
                }
                if (broken % 64 == 0) {
                    Vec3 outward = Vec3.atCenterOf(pos).subtract(Vec3.atCenterOf(center)).normalize();
                    QuakeDebrisEntity.launch(level, state, Vec3.atCenterOf(pos),
                            outward.scale(0.3).add(0, 0.4, 0));
                }
            }
        }
        return cursor >= OFFSETS.size();
    }
}
