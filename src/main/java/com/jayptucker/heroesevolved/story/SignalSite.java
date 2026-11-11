package com.jayptucker.heroesevolved.story;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves blueprint markers against the actual world-generated piece, including its rotation. */
record SignalSite(ServerLevel level, PoolElementStructurePiece piece) {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "signal_site");
    static final TagKey<Structure> TAG = TagKey.create(Registries.STRUCTURE, ID);

    private static final class Markers {
        static final Map<String, List<BlockPos>> OFFSETS = List.of(
                "arrival", "relay", "isolator", "coolant", "gate", "rear_gate", "release", "hot", "pod", "gray", "exit", "extract")
                .stream().collect(Collectors.toUnmodifiableMap(Function.identity(),
                        marker -> Blueprint.load("signal_site").markerOffsets("signal:" + marker)));
    }

    BlockPos origin() {
        return piece.getPosition();
    }

    boolean contains(BlockPos pos) {
        return piece.getBoundingBox().isInside(pos);
    }

    List<BlockPos> all(String marker) {
        return Markers.OFFSETS.get(marker).stream()
                .map(offset -> origin().offset(offset.rotate(piece.getRotation()))).toList();
    }

    BlockPos at(String marker) {
        return all(marker).getFirst();
    }

    static SignalSite at(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(Level.OVERWORLD) || !level.hasChunkAt(pos)) return null;
        return from(level, level.structureManager().getStructureWithPieceAt(pos, TAG));
    }

    static BlockPos locate(ServerLevel level, BlockPos search) {
        BlockPos found = level.findNearestMapStructure(TAG, search, 100, false);
        if (found == null) return null;
        ChunkPos chunk = new ChunkPos(found);
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ID);
        if (structure == null) return null;
        var start = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
        SignalSite site = from(level, start);
        return site == null ? found : site.at("arrival");
    }

    private static SignalSite from(ServerLevel level, StructureStart start) {
        if (start == null || !start.isValid() || !(start.getPieces().getFirst() instanceof PoolElementStructurePiece piece))
            return null;
        return new SignalSite(level, piece);
    }
}
