package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.FrostConstructBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The frost-construct cells this client knows (from the real client blocks); {@link CryoArt#cell} draws them. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class CryoConstructArt {
    private static final Map<ChunkPos, Set<BlockPos>> CELLS = new HashMap<>();
    private static ClientLevel world;

    private CryoConstructArt() {}

    private static void useWorld(ClientLevel level) {
        if (world == level) return;
        world = level;
        CELLS.clear();
    }

    public static void changed(ClientLevel level, BlockPos pos, BlockState state) {
        useWorld(level);
        ChunkPos chunk = new ChunkPos(pos);
        if (FrostConstructBlock.isFrost(state)) CELLS.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(pos.immutable());
        else {
            var cells = CELLS.get(chunk);
            if (cells != null && cells.remove(pos) && cells.isEmpty()) CELLS.remove(chunk);
        }
    }

    public static void loaded(ClientLevel level, LevelChunk chunk) {
        useWorld(level);
        Set<BlockPos> cells = new HashSet<>();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        var sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            if (!section.maybeHas(FrostConstructBlock::isFrost)) continue;
            int baseY = level.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (FrostConstructBlock.isFrost(section.getBlockState(x, y, z)))
                    cells.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
            }
        }
        if (cells.isEmpty()) CELLS.remove(chunk.getPos());
        else CELLS.put(chunk.getPos(), cells);
    }

    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if (event.getLevel() == world) CELLS.remove(event.getChunk().getPos());
    }

    static boolean live(ClientLevel level) {
        useWorld(level);
        return !CELLS.isEmpty();
    }

    static void draw(Canvas c) {
        if (c.glow()) return;
        useWorld(c.level);
        for (var entry : CELLS.entrySet()) {
            var chunk = entry.getKey();
            if (Math.abs(chunk.getMiddleBlockX() - c.cx) > 96 || Math.abs(chunk.getMiddleBlockZ() - c.cz) > 96) continue;
            for (BlockPos pos : entry.getValue()) {
                var form = form(pos);
                if (form != null && pos.distToCenterSqr(c.cx, c.cy, c.cz) < 96 * 96) CryoArt.cell(c, pos, form);
            }
        }
    }

    static FrostConstructBlock.Form form(BlockPos pos) {
        var state = world.getBlockState(pos);
        return FrostConstructBlock.isFrost(state) ? state.getValue(FrostConstructBlock.FORM) : null;
    }
}
