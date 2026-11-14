package com.jayptucker.heroesevolved.dampener;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * The Ability Dampener: a Primatech-grey pylon that runs on Forge Energy (RF) and holds a sphere round itself in which
 * no power answers. Feed it from any FE mod's cables, generators or batteries (or an Electrokinetic's lightning).
 *
 * <ul>
 *   <li>Use with an empty hand: read its charge and step the radius (4, 8, 12, 16 blocks; bigger costs more).</li>
 *   <li>Sneak and use with an empty hand: switch it off or on.</li>
 *   <li>A redstone signal switches it off while it lasts.</li>
 *   <li>Use it with a Nullifier (TaCZ's Interact key, O by default): top the gun's battery up from it.</li>
 * </ul>
 * A comparator reads how full it is. Broken, it keeps its charge in the item.
 */
public final class AbilityDampenerBlock extends BaseEntityBlock {
    public static final MapCodec<AbilityDampenerBlock> CODEC = simpleCodec(AbilityDampenerBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public AbilityDampenerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ACTIVE); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AbilityDampenerBlockEntity(pos, state); }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.ABILITY_DAMPENER_ENTITY.get(), AbilityDampenerBlockEntity::serverTick);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AbilityDampenerBlockEntity field)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            boolean on = field.toggle();
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, on ? 0.9F : 0.6F);
        } else {
            field.cycleRadius();
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.7F, 0.6F + field.radius() / 32F);
        }
        player.displayClientMessage(status(field), true);
        return InteractionResult.CONSUME;
    }

    /** Used with a Nullifier: tops the gun's battery up from this one. Anything else falls through to the empty-hand use. */
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                        InteractionHand hand, BlockHitResult hit) {
        if (!Nullifier.is(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer user && level.mayInteract(user, pos)) Nullifier.charge(user, stack, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** "Ability Dampener: on, 8 blocks · 312,000 / 500,000 FE (48 FE/t)". */
    static Component status(AbilityDampenerBlockEntity field) {
        NumberFormat n = NumberFormat.getIntegerInstance(Locale.ROOT);
        return Component.translatable(field.enabled() ? "block.heroesevolved.ability_dampener.on" : "block.heroesevolved.ability_dampener.off",
                field.radius(), n.format(field.stored()), n.format(DampenerGeometry.CAPACITY), DampenerGeometry.upkeep(field.radius()))
                .withStyle(field.enabled() ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }

    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbilityDampenerBlockEntity field) || field.stored() <= 0) return 0;
        return 1 + (int) (14L * field.stored() / DampenerGeometry.CAPACITY);
    }
}
