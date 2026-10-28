package com.jayptucker.heroesevolved.formula;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A brewing-stand-style lab bench with three bottle slots, an ingredient, and glowstone fuel.
 * <ol>
 *   <li>Water Bottle + Bone Meal = Buffered Saline</li>
 *   <li>Buffered Saline + Helixite Shard = Unstable Compound</li>
 *   <li>Unstable Compound + Isolated Catalyst = Formula Vial (keeps the blood's purity)</li>
 * </ol>
 * Each stage takes twenty seconds and uses one ingredient for up to three bottles.
 */
public final class SynthesisStationBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int INGREDIENT = 3, FUEL = 4, BREW_TICKS = 400, FUEL_PER_GLOWSTONE = 8;
    private static final int[] TOP = {INGREDIENT}, BOTTOM = {0, 1, 2}, SIDES = {0, 1, 2, FUEL};
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    private int brewTime, fuel;
    private Item brewing = Items.AIR;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? brewTime : fuel; }
        @Override public void set(int index, int value) { if (index == 0) brewTime = value; else fuel = value; }
        @Override public int getCount() { return 2; }
    };

    public SynthesisStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SYNTHESIS_STATION_ENTITY.get(), pos, state);
    }

    public static boolean isWater(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(Potions.WATER);
    }

    public static boolean isBottle(ItemStack stack) {
        return isWater(stack) || stack.is(ModItems.BUFFERED_SALINE.get()) || stack.is(ModItems.UNSTABLE_COMPOUND.get())
                || stack.is(ModItems.FORMULA_VIAL.get());
    }

    public static boolean isIngredient(ItemStack stack) {
        return stack.is(Items.BONE_MEAL) || stack.is(ModItems.HELIXITE_SHARD.get()) || stack.is(ModItems.ISOLATED_CATALYST.get());
    }

    /** The product of one bottle and one ingredient, or empty when they do not react. */
    public static ItemStack result(ItemStack bottle, ItemStack ingredient) {
        if (isWater(bottle) && ingredient.is(Items.BONE_MEAL)) return new ItemStack(ModItems.BUFFERED_SALINE.get());
        if (bottle.is(ModItems.BUFFERED_SALINE.get()) && ingredient.is(ModItems.HELIXITE_SHARD.get()))
            return new ItemStack(ModItems.UNSTABLE_COMPOUND.get());
        if (bottle.is(ModItems.UNSTABLE_COMPOUND.get()) && ingredient.is(ModItems.ISOLATED_CATALYST.get())) {
            ItemStack vial = new ItemStack(ModItems.FORMULA_VIAL.get());
            vial.set(ModItems.BLOOD_PURITY.get(), ingredient.get(ModItems.BLOOD_PURITY.get()));
            return vial;
        }
        return ItemStack.EMPTY;
    }

    private boolean brewable() {
        ItemStack ingredient = items.get(INGREDIENT);
        if (ingredient.isEmpty()) return false;
        for (int slot = 0; slot < 3; slot++) if (!result(items.get(slot), ingredient).isEmpty()) return true;
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SynthesisStationBlockEntity station) {
        if (station.fuel <= 0 && station.items.get(FUEL).is(Items.GLOWSTONE_DUST)) {
            station.items.get(FUEL).shrink(1);
            station.fuel = FUEL_PER_GLOWSTONE;
            station.setChanged();
        }
        boolean brewable = station.brewable();
        if (station.brewTime > 0) {
            if (!brewable || !station.items.get(INGREDIENT).is(station.brewing)) {
                station.brewTime = 0;
            } else if (--station.brewTime == 0) {
                station.finish(level, pos);
            }
            station.setChanged();
        } else if (brewable && station.fuel > 0) {
            station.fuel--;
            station.brewTime = BREW_TICKS;
            station.brewing = station.items.get(INGREDIENT).getItem();
            station.setChanged();
        }
        LabBlock.setLit(level, pos, state, station.brewTime > 0);
    }

    private void finish(Level level, BlockPos pos) {
        ItemStack ingredient = items.get(INGREDIENT);
        boolean formula = ingredient.is(ModItems.ISOLATED_CATALYST.get());
        for (int slot = 0; slot < 3; slot++) {
            ItemStack product = result(items.get(slot), ingredient);
            if (!product.isEmpty()) items.set(slot, product);
        }
        ingredient.shrink(1);
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1, formula ? 0.6F : 1.2F);
        if (formula) LabBlock.attractAttention(level, pos);
    }

    @Override protected Component getDefaultName() { return Component.translatable("block.heroesevolved.synthesis_station"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return items.size(); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new SynthesisStationMenu(id, inventory, this, data); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == INGREDIENT) return isIngredient(stack);
        if (slot == FUEL) return stack.is(Items.GLOWSTONE_DUST);
        return isBottle(stack) && items.get(slot).isEmpty();
    }

    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDES; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }

    /** Hoppers only pull bottles that are finished with the current ingredient. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot < 3 && brewTime == 0 && result(stack, items.get(INGREDIENT)).isEmpty();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        brewTime = tag.getInt("BrewTime");
        fuel = tag.getInt("Fuel");
        brewing = items.get(INGREDIENT).getItem();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("BrewTime", brewTime);
        tag.putInt("Fuel", fuel);
    }
}
