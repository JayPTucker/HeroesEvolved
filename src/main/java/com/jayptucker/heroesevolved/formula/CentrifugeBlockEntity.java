package com.jayptucker.heroesevolved.formula;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Spins a Regenerative Blood Sample into an Isolated Catalyst. Each spin burns one redstone dust
 * and takes twenty seconds. Hoppers feed samples from above, redstone from the sides, and pull catalyst from below.
 */
public final class CentrifugeBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int INPUT = 0, FUEL = 1, OUTPUT = 2, SPIN_TICKS = 400;
    private static final int[] TOP = {INPUT}, BOTTOM = {OUTPUT}, SIDES = {FUEL};
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int progress, charge;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch (index) { case 0 -> progress; case 1 -> SPIN_TICKS; default -> charge; }; }
        @Override public void set(int index, int value) { if (index == 0) progress = value; else if (index == 2) charge = value; }
        @Override public int getCount() { return 3; }
    };

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CENTRIFUGE_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
        boolean ready = centrifuge.items.get(INPUT).is(ModItems.BLOOD_SAMPLE.get()) && centrifuge.items.get(OUTPUT).isEmpty();
        if (ready && centrifuge.charge == 0 && centrifuge.items.get(FUEL).is(Items.REDSTONE)) {
            centrifuge.items.get(FUEL).shrink(1);
            centrifuge.charge = 1;
            centrifuge.setChanged();
        }
        boolean spinning = ready && centrifuge.charge > 0;
        if (spinning) {
            if (++centrifuge.progress >= SPIN_TICKS) {
                ItemStack sample = centrifuge.items.get(INPUT);
                ItemStack catalyst = new ItemStack(ModItems.ISOLATED_CATALYST.get());
                catalyst.set(ModItems.BLOOD_PURITY.get(), sample.get(ModItems.BLOOD_PURITY.get()));
                sample.shrink(1);
                centrifuge.items.set(OUTPUT, catalyst);
                centrifuge.progress = 0;
                centrifuge.charge = 0;
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1, 0.7F);
                LabBlock.attractAttention(level, pos);
            } else if (centrifuge.progress % 40 == 1) {
                level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.6F);
            }
            centrifuge.setChanged();
        } else if (centrifuge.progress != 0) {
            centrifuge.progress = 0;
            centrifuge.setChanged();
        }
        LabBlock.setLit(level, pos, state, spinning);
    }

    @Override protected Component getDefaultName() { return Component.translatable("block.heroesevolved.centrifuge"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return items.size(); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new CentrifugeMenu(id, inventory, this, data); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT ? stack.is(ModItems.BLOOD_SAMPLE.get()) : slot == FUEL && stack.is(Items.REDSTONE);
    }

    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDES; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == OUTPUT; }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getInt("Progress");
        charge = tag.getInt("Charge");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putInt("Charge", charge);
    }
}
