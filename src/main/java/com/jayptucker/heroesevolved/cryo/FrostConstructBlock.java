package com.jayptucker.heroesevolved.cryo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Collision and saved shape of a frost construct. The client draws its faceted surface. */
public final class FrostConstructBlock extends Block {
    public enum Form implements StringRepresentable {
        WALL("wall", 0, 1), CROWN("crown", 0, .5), SURFACE("surface", 0, 1),
        LOW("low", 0, .5), DECK("deck", .5, 1);

        private final String name;
        public final double bottom, top;
        private final VoxelShape shape;

        Form(String name, double bottom, double top) {
            this.name = name;
            this.bottom = bottom;
            this.top = top;
            this.shape = Shapes.box(0, bottom, 0, 1, top, 1);
        }

        @Override public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Form> FORM = EnumProperty.create("form", Form.class);

    public FrostConstructBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORM, Form.WALL));
    }

    public static boolean isFrost(BlockState state) { return state.getBlock() instanceof FrostConstructBlock; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORM);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORM).shape;
    }
}
