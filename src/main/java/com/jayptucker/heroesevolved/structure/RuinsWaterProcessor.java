package com.jayptucker.heroesevolved.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.*;

/** The same template becomes a flooded ruin on the seabed, with only loose timber and barrels afloat. */
public final class RuinsWaterProcessor extends StructureProcessor {
    public static final RuinsWaterProcessor INSTANCE = new RuinsWaterProcessor();
    public static final MapCodec<RuinsWaterProcessor> CODEC = MapCodec.unit(INSTANCE);

    @Override protected StructureProcessorType<?> getType() { return RuinsWorldgen.WATER.get(); }

    @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pivot,
            StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings settings) {
        if (!RuinWater.water(level, placed.pos())) return placed;
        return new StructureTemplate.StructureBlockInfo(placed.pos(), RuinWater.flooded(placed.state()), placed.nbt());
    }

    @Override public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pivot,
            List<StructureTemplate.StructureBlockInfo> originals, List<StructureTemplate.StructureBlockInfo> processed, StructurePlaceSettings settings) {
        var result = new ArrayList<StructureTemplate.StructureBlockInfo>();
        var afloat = new LinkedHashMap<BlockPos, StructureTemplate.StructureBlockInfo>();
        Set<BlockPos> solid = new HashSet<>();
        for (var cell : processed) if (!cell.state().isAir() && cell.state().getFluidState().isEmpty()) solid.add(cell.pos());
        // A loose board above a cupboard must not claim the barrel's only surface cell first.
        var ordered = processed.stream().sorted(Comparator.comparing((StructureTemplate.StructureBlockInfo cell) -> !cell.state().is(Blocks.BARREL))).toList();
        for (var cell : ordered) {
            // Only inspect columns belonging to this generation chunk. Never place into a neighbouring chunk.
            var box = settings.getBoundingBox();
            if (box != null && !box.isInside(cell.pos())) { result.add(cell); continue; }
            boolean salvage = cell.state().is(Blocks.BARREL) || RuinWater.looseBoard(cell.state(), cell.pos().subtract(offset));
            BlockPos top = salvage ? RuinWater.surface(level, cell.pos()) : cell.pos();
            if (!top.equals(cell.pos()) && (box == null || box.isInside(top)) && !solid.contains(top) && !afloat.containsKey(top)) {
                result.add(new StructureTemplate.StructureBlockInfo(cell.pos(), Blocks.WATER.defaultBlockState(), null));
                afloat.put(top, new StructureTemplate.StructureBlockInfo(top, RuinWater.floating(cell.state()), cell.nbt()));
            } else result.add(cell);
        }
        // In shallow water, a room's air cells must not overwrite the floating salvage afterwards.
        result.removeIf(cell -> afloat.containsKey(cell.pos()));
        result.addAll(afloat.values());
        return result;
    }
}
