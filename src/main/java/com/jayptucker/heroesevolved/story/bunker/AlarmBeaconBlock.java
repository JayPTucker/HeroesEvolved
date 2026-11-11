package com.jayptucker.heroesevolved.story.bunker;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A rotating alarm beacon: a black base under a red dome, with a lamp and a mirror turning inside it
 * that sweeps two beams of red light around the room (drawn by AlarmBeaconRenderer). It is a fixture:
 * it hangs from a ceiling ({@code facing=down}) or stands on a bracket bolted to a wall, facing out
 * from it. A right-click switches it off or on. The stasis bunker's run on emergency power, so they
 * keep turning after its alarm has stopped.
 */
public class AlarmBeaconBlock extends BaseEntityBlock {
    public static final MapCodec<AlarmBeaconBlock> CODEC = simpleCodec(AlarmBeaconBlock::new);
    /** Away from what it is mounted on: down from a ceiling, or out from a wall. */
    public static final DirectionProperty FACING = DirectionProperty.create("facing", direction -> direction != Direction.UP);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Light from the lamp itself. Low, so the beams carry the room. */
    public static final int LIGHT = 6;
    private static final VoxelShape HUNG = Shapes.or(Block.box(3, 11, 3, 13, 16, 13), Block.box(4, 2.5, 4, 12, 11, 12));
    private static final Map<Direction, VoxelShape> ON_WALL = new EnumMap<>(Direction.class);

    static {
        // Drawn facing north, so the wall is on the south side.
        double[][] boxes = {{5, 0, 15, 11, 8, 16}, {5, 0, 4, 11, 1, 16}, {3, 1, 3, 13, 6, 13}, {4, 6, 4, 12, 14.5, 12}};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double[] r = switch (facing) {
                    case SOUTH -> new double[] {16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]};
                    case EAST -> new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
                    case WEST -> new double[] {b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]};
                    default -> b;
                };
                shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
            }
            ON_WALL.put(facing, shape);
        }
    }

    public AlarmBeaconBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LIT, true));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, LIT); }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AlarmBeaconBlockEntity(pos, state); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.DOWN ? HUNG : ON_WALL.get(facing);
    }

    /** Where the lamp sits inside the dome, as a fraction of the block's height. */
    public static double lampHeight(BlockState state) { return state.getValue(FACING) == Direction.DOWN ? 7 / 16.0 : 10 / 16.0; }

    /** How far below level the beams point, in degrees: a hung beacon looks down into the room. */
    public static double tilt(BlockState state) { return state.getValue(FACING) == Direction.DOWN ? 14 : 7; }

    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction == Direction.DOWN) continue;  // never on a floor or a table
            BlockState state = defaultBlockState().setValue(FACING, direction.getOpposite());
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) return state;
        }
        return null;
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos mount = pos.relative(facing.getOpposite());
        return facing == Direction.DOWN ? Block.canSupportCenter(level, mount, Direction.DOWN)
                : level.getBlockState(mount).isFaceSturdy(level, mount, facing);
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.DOWN ? state : state.setValue(FACING, rotation.rotate(facing));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.DOWN ? state : state.setValue(FACING, mirror.mirror(facing));
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean lit = !state.getValue(LIT);
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
            level.playSound(null, pos, lit ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, 0.8F, lit ? 0.9F : 0.75F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
}
