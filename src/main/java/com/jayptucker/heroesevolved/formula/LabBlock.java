package com.jayptucker.heroesevolved.formula;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The Centrifuge and the Synthesis Station: facing, lit while working, and a container that drops its contents. */
public final class LabBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<LabBlock> CENTRIFUGE_CODEC = simpleCodec(properties -> new LabBlock(properties, true));
    public static final MapCodec<LabBlock> STATION_CODEC = simpleCodec(properties -> new LabBlock(properties, false));
    private final boolean centrifuge;

    public LabBlock(Properties properties, boolean centrifuge) {
        super(properties);
        this.centrifuge = centrifuge;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return centrifuge ? CENTRIFUGE_CODEC : STATION_CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, LIT); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return centrifuge ? new CentrifugeBlockEntity(pos, state) : new SynthesisStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return centrifuge
                ? createTickerHelper(type, ModBlocks.CENTRIFUGE_ENTITY.get(), CentrifugeBlockEntity::serverTick)
                : createTickerHelper(type, ModBlocks.SYNTHESIS_STATION_ENTITY.get(), SynthesisStationBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MenuProvider provider) player.openMenu(provider);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Lab work draws the watchmaker's attention to anyone nearby who already has a power. */
    static void attractAttention(Level level, BlockPos pos) {
        if (level instanceof net.minecraft.server.level.ServerLevel server)
            for (var player : server.getPlayers(p -> p.blockPosition().closerThan(pos, 16)))
                com.jayptucker.heroesevolved.story.sylar.SylarStoryService.notice(player);
    }

    static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(LIT) != lit) level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
    }
}
