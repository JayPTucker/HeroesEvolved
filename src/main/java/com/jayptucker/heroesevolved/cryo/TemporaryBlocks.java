package com.jayptucker.heroesevolved.cryo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;

/**
 * Ice and snow that melt back to exactly what was there before.
 *
 * <p>Walls replace air, plants or water. Domes also frost exposed solid surfaces,
 * preserving original states and avoiding containers, occupied blocks and protected
 * terrain. Thaw restores the original state unless someone changed the ice.
 * Restoration is batched during play and completed when the server stops.</p>
 */
public final class TemporaryBlocks {
    private record Key(ServerLevel level, BlockPos pos) {}
    private static final java.util.Map<Key, Placed> PLACED = new java.util.LinkedHashMap<>();
    public static final int MAX_MELTS_PER_TICK = 96;
    private static final int MAXIMUM_PLACED = 4_000;

    private TemporaryBlocks() {
    }

    /**
     * @param player whose permissions apply, or null for scripted moments
     * @return true if the block was placed
     */
    public static boolean place(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            int durationTicks,
            ServerPlayer player
    ) {
        if (PLACED.size() >= MAXIMUM_PLACED || !level.isLoaded(pos)
                || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        if (player != null && (!player.mayBuild() || !level.mayInteract(player, pos))) {
            return false;
        }

        BlockState original = level.getBlockState(pos);
        boolean water = original.getFluidState().is(Fluids.WATER) && original.is(Blocks.WATER);
        boolean replaceable = original.isAir() || (original.canBeReplaced()
                && original.getFluidState().isEmpty());
        if ((!replaceable && !water) || level.getBlockEntity(pos) != null
                || !level.getEntities(null, new AABB(pos)).isEmpty()) {
            return false;
        }

        Key key = new Key(level, pos.immutable());
        Placed previous = PLACED.get(key);
        boolean replacingTemporary = previous != null && previous.placed.equals(original);
        remember(level, new FrostRecovery(pos.immutable(), replacingTemporary ? previous.original : original,
                state, Math.max(replacingTemporary ? previous.meltAt : 0, level.getGameTime() + durationTicks)));
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
        PLACED.put(key, new Placed(level, pos.immutable(), replacingTemporary ? previous.original : original, state,
                Math.max(replacingTemporary ? previous.meltAt : 0, level.getGameTime() + durationTicks), true));
        return true;
    }

    /**
     * Freezes still water into frosted ice, which Minecraft melts back to
     * water on its own (the same ice Frost Walker makes).
     */
    public static boolean freezeWater(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()
                || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
        level.scheduleTick(pos, Blocks.FROSTED_ICE,
                Mth.nextInt(level.getRandom(), 60, 120));
        return true;
    }

    /** Coat exposed terrain in reversible ice; containers and player changes remain intact. */
    public static boolean frostSurface(ServerLevel level, BlockPos pos, int duration, ServerPlayer owner) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !owner.mayBuild() || !level.mayInteract(owner, pos)) return false;
        Key key = new Key(level, pos.immutable());
        Placed previous = PLACED.get(key);
        BlockState original = level.getBlockState(pos);
        if (previous != null && original.equals(previous.placed)) {
            PLACED.put(key, new Placed(level, key.pos, previous.original, previous.placed,
                    Math.max(previous.meltAt, level.getGameTime() + duration), previous.effects));
            remember(level, new FrostRecovery(key.pos, previous.original, previous.placed,
                    Math.max(previous.meltAt, level.getGameTime() + duration)));
            return true;
        }
        if (PLACED.size() >= MAXIMUM_PLACED || original.isAir() || original.is(Blocks.PACKED_ICE)
                || original.is(Blocks.BLUE_ICE) || original.is(Blocks.ICE) || original.is(Blocks.FROSTED_ICE)
                || original.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null
                || (!original.getFluidState().isEmpty() && !original.is(Blocks.WATER))
                || !level.getEntities(null, new AABB(pos)).isEmpty()) return false;
        BlockState above = level.getBlockState(pos.above());
        // Converting a plant's support block would break it through neighbor updates.
        // Leave decorated surfaces intact instead of dropping or losing those plants.
        if (!above.isAir()) return false;
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, pos.immutable(), original, owner)).isCanceled()) return false;
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
        remember(level, new FrostRecovery(pos.immutable(), original, ice, level.getGameTime() + duration));
        if (!level.setBlock(pos, ice, Block.UPDATE_ALL)) return false;
        PLACED.put(key, new Placed(level, pos.immutable(), original, ice, level.getGameTime() + duration, false));
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Placed> iterator = PLACED.values().iterator();
        int melts = 0;
        long deadline = System.nanoTime() + 2_000_000;
        while (iterator.hasNext() && melts < MAX_MELTS_PER_TICK && System.nanoTime() < deadline) {
            Placed placed = iterator.next();
            if (placed.level.getServer() == server && placed.level.getGameTime() >= placed.meltAt
                    && placed.level.hasChunkAt(placed.pos)) {
                melt(placed, placed.effects); iterator.remove(); melts++;
            }
        }
    }

    /** Restores everything at once; called when the server stops. */
    public static void meltAll() {
        for (Placed placed : java.util.List.copyOf(PLACED.values())) {
            if (placed.level.hasChunkAt(placed.pos)) melt(placed, false);
        }
        PLACED.clear();
    }

    private static void melt(Placed placed, boolean effects) {
        ServerLevel level = placed.level;
        // Someone mined or replaced it already: leave their change alone.
        if (!level.getBlockState(placed.pos).equals(placed.placed)) {
            forgetRecovery(level, placed.pos);
            return;
        }
        level.setBlock(placed.pos, placed.original, Block.UPDATE_ALL);
        forgetRecovery(level, placed.pos);
        if (effects) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, placed.placed),
                    placed.pos.getX() + 0.5D, placed.pos.getY() + 0.5D,
                    placed.pos.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.05D);
            level.sendParticles(ParticleTypes.DRIPPING_WATER,
                    placed.pos.getX() + 0.5D, placed.pos.getY() + 0.2D,
                    placed.pos.getZ() + 0.5D, 2, 0.3D, 0.1D, 0.3D, 0.0D);
            if (level.getRandom().nextFloat() < 0.15F) {
                level.playSound(null, placed.pos, SoundEvents.GLASS_BREAK,
                        SoundSource.BLOCKS, 0.3F, 1.6F);
            }
        }
    }

    private record Placed(
            ServerLevel level,
            BlockPos pos,
            BlockState original,
            BlockState placed,
            long meltAt,
            boolean effects
    ) {
    }

    private static void remember(ServerLevel level, FrostRecovery recovery) {
        var chunk = level.getChunkAt(recovery.pos());
        var entries = new java.util.ArrayList<>(chunk.getData(com.jayptucker.heroesevolved.data.ModDataAttachments.FROST_RECOVERY));
        entries.removeIf(entry -> entry.pos().equals(recovery.pos())); entries.add(recovery);
        chunk.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.FROST_RECOVERY, java.util.List.copyOf(entries));
        chunk.setUnsaved(true);
    }
    private static void forgetRecovery(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkAt(pos);
        var entries = new java.util.ArrayList<>(chunk.getData(com.jayptucker.heroesevolved.data.ModDataAttachments.FROST_RECOVERY));
        if (entries.removeIf(entry -> entry.pos().equals(pos))) {
            chunk.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.FROST_RECOVERY, java.util.List.copyOf(entries));
            chunk.setUnsaved(true);
        }
    }
    public static void recover(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        for (FrostRecovery entry : chunk.getData(com.jayptucker.heroesevolved.data.ModDataAttachments.FROST_RECOVERY)) {
            if (new net.minecraft.world.level.ChunkPos(entry.pos()).equals(chunk.getPos())
                    && chunk.getBlockState(entry.pos()).equals(entry.placed()))
                PLACED.put(new Key(level, entry.pos()), new Placed(level, entry.pos(), entry.original(), entry.placed(), entry.expires(), false));
        }
    }
}
