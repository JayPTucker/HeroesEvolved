package com.jayptucker.heroesevolved.story.bunker;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A two-block stasis pod: a metal back and base, glass sides, and a glass front that opens.
 * Use an open pod to step in and seal it (it also becomes your respawn point); sneak to step out.
 * Sneak-use as a Cryokinetic to supercharge it. The bunker's sleepers wake inside these.
 */
public final class CryoChamberBlock extends Block {
    public static final MapCodec<CryoChamberBlock> CODEC = simpleCodec(CryoChamberBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");

    /** Shapes for a pod whose front faces north; rotated for the other facings. */
    private static final Map<Direction, VoxelShape[]> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape lowerFrame = Shapes.or(box(facing, 0, 0, 0, 16, 2, 16), box(facing, 0, 2, 14, 16, 16, 16),
                    box(facing, 0, 2, 0, 1, 16, 14), box(facing, 15, 2, 0, 16, 16, 14));
            VoxelShape upperFrame = Shapes.or(box(facing, 0, 14, 0, 16, 16, 16), box(facing, 0, 0, 14, 16, 14, 16),
                    box(facing, 0, 0, 0, 1, 14, 14), box(facing, 15, 0, 0, 16, 14, 14));
            VoxelShape lowerDoor = box(facing, 1, 2, 0, 15, 16, 1), upperDoor = box(facing, 1, 0, 0, 15, 14, 1);
            SHAPES.put(facing, new VoxelShape[] {
                    lowerFrame, Shapes.or(lowerFrame, lowerDoor), upperFrame, Shapes.or(upperFrame, upperDoor)});
        }
    }

    /** A box given for a north-facing pod, rotated clockwise to {@code facing}. */
    private static VoxelShape box(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public CryoChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OPEN, true).setValue(CHARGED, false));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OPEN, CHARGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int index = (state.getValue(HALF) == DoubleBlockHalf.UPPER ? 2 : 0) + (state.getValue(OPEN) ? 0 : 1);
        return SHAPES.get(state.getValue(FACING))[index];
    }

    /** The pod's base position, whichever half is given. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** Where a sleeper stands: on the base plate, facing out of the glass. */
    public static Vec3 standing(BlockPos base) { return Vec3.atBottomCenterOf(base).add(0, 2 / 16.0, 0); }

    public static float yaw(Direction facing) { return facing.toYRot(); }

    /** Opens or closes both halves together. */
    public static void setOpen(Level level, BlockPos base, boolean open) {
        for (BlockPos pos : new BlockPos[] {base, base.above()}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CryoChamberBlock && state.getValue(OPEN) != open)
                level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        }
    }

    public static void setCharged(Level level, BlockPos base, boolean charged) {
        for (BlockPos pos : new BlockPos[] {base, base.above()}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CryoChamberBlock) level.setBlock(pos, state.setValue(CHARGED, charged), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // The pod drops from its base. Breaking the top half breaks the base too, dropping it in survival.
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState base = level.getBlockState(below);
            if (base.is(this)) {
                if (!player.isCreative() && player.hasCorrectToolForDrops(base, level, below))
                    Block.dropResources(base, level, below, null, player, player.getMainHandItem());
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(base));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return CryoChamberService.use((ServerPlayer) player, base(state, pos)) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }

    /** Respawning inside your pod, the way a bed works but at any hour. */
    @Override
    public Optional<net.minecraft.server.level.ServerPlayer.RespawnPosAngle> getRespawnPosition(BlockState state, EntityType<?> type,
            LevelReader level, BlockPos pos, float orientation) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return Optional.empty();
        return Optional.of(new net.minecraft.server.level.ServerPlayer.RespawnPosAngle(standing(pos), yaw(state.getValue(FACING))));
    }
}
