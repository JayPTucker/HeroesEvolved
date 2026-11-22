package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Collection;

/**
 * Which blocks players put down, so a village never builds over, wears, tears down or tidies them: a set of positions
 * kept on each chunk (saved with it). Every placement by a player counts (other mods' machines placing as fake players
 * too). A mark only counts while something still stands there: a broken block's mark goes the next time it is asked
 * about, so this never listens to break events (the mod posts those as probes too).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PlayerBuilt {
    private PlayerBuilt() {}

    /** The marked positions in one chunk. */
    public static final class Marks {
        final LongOpenHashSet cells = new LongOpenHashSet();
    }

    public static final IAttachmentSerializer<LongArrayTag, Marks> SERIALIZER = new IAttachmentSerializer<>() {
        @Override
        public Marks read(IAttachmentHolder holder, LongArrayTag tag, HolderLookup.Provider provider) {
            Marks m = new Marks();
            for (long cell : tag.getAsLongArray()) m.cells.add(cell);
            return m;
        }

        @Override
        public LongArrayTag write(Marks marks, HolderLookup.Provider provider) {
            return marks.cells.isEmpty() ? null : new LongArrayTag(marks.cells.toLongArray());
        }
    };

    /** Whether a player put down what stands at {@code pos} (false where the chunk isn't loaded: nothing acts there). */
    public static boolean is(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        LevelChunk chunk = level.getChunkAt(pos);
        Marks marks = chunk.getExistingDataOrNull(ModDataAttachments.PLAYER_BUILT);
        if (marks == null || !marks.cells.contains(pos.asLong())) return false;
        if (level.getBlockState(pos).isAir()) {
            marks.cells.remove(pos.asLong());
            chunk.setUnsaved(true);
            return false;
        }
        return true;
    }

    /** Whether any of {@code positions} holds something a player put down. */
    public static boolean any(Level level, Collection<BlockPos> positions) {
        for (BlockPos p : positions) if (is(level, p)) return true;
        return false;
    }

    /** Marks {@code pos} as a player's (a home built for one counts as theirs). */
    public static void mark(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        LevelChunk chunk = level.getChunkAt(pos);
        if (chunk.getData(ModDataAttachments.PLAYER_BUILT).cells.add(pos.asLong())) chunk.setUnsaved(true);
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player) || !(level(event.getLevel()) instanceof Level level) || level.isClientSide) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi)
            for (var snapshot : multi.getReplacedBlockSnapshots()) mark(level, snapshot.getPos());
        else mark(level, event.getPos());
    }

    /**
     * A block changed and told its neighbours (a player breaking it, an explosion, water): if it's gone, so is its mark,
     * or whatever stands there next (a village's wall) would count as the player's. Probing break events never get here.
     */
    @SubscribeEvent
    public static void changed(BlockEvent.NeighborNotifyEvent event) {
        if (!(level(event.getLevel()) instanceof Level level) || level.isClientSide || !event.getState().isAir()) return;
        BlockPos pos = event.getPos();
        if (!level.hasChunkAt(pos)) return;
        LevelChunk chunk = level.getChunkAt(pos);
        Marks marks = chunk.getExistingDataOrNull(ModDataAttachments.PLAYER_BUILT);
        if (marks != null && marks.cells.remove(pos.asLong())) chunk.setUnsaved(true);
    }

    /** Forgets every mark between {@code a} and {@code b} (tests that reuse ground, admin). */
    public static void clear(Level level, BlockPos a, BlockPos b) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                Marks marks = chunk.getExistingDataOrNull(ModDataAttachments.PLAYER_BUILT);
                if (marks == null) continue;
                if (marks.cells.removeIf(l -> {
                    int x = BlockPos.getX(l), y = BlockPos.getY(l), z = BlockPos.getZ(l);
                    return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
                })) chunk.setUnsaved(true);
            }
    }

    private static Level level(LevelAccessor accessor) { return accessor instanceof Level l ? l : null; }
}
