package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Paid, deliberately non-harvestable collision geometry; never has a BlockItem or block entity. */
public final class AssemblyBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HeroesEvolved.MOD_ID);
    public static final DeferredBlock<ConstructBlock> MATTER = BLOCKS.register("assembled_matter", () ->
            new ConstructBlock(BlockBehaviour.Properties.of().strength(1.1F, 5F).sound(SoundType.COPPER)
                    .lightLevel(state -> 4).noLootTable().pushReaction(PushReaction.BLOCK)
                    .isSuffocating((state, level, pos) -> false)));
    private AssemblyBlocks() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); }

    public static final class ConstructBlock extends Block {
        public ConstructBlock(Properties properties) { super(properties); }
        @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
            super.onPlace(state, level, pos, previous, moved);
            if (!level.isClientSide) level.scheduleTick(pos, this, 10);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            // Also catches orphan states resurrected by rewind or an interrupted world/chunk save.
            if (!AssemblyTerrain.get(level).valid(level, pos)) level.removeBlock(pos, false);
            else level.scheduleTick(pos, this, 10);
        }
    }
}
