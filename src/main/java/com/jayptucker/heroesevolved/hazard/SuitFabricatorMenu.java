package com.jayptucker.heroesevolved.hazard;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Suit Fabricator's screen, worked like a stonecutter: pick a piece from the patterns along the top, put its
 * materials in the six slots in any order, and take the piece from the result slot.
 */
public class SuitFabricatorMenu extends AbstractContainerMenu {
    public static final int INPUTS = 6, RESULT = INPUTS, INVENTORY = RESULT + 1;
    /** Layout, shared with the screen. */
    public static final int WIDTH = 176, HEIGHT = 204, BUTTON_X = 16, BUTTON_Y = 18, BUTTON_STEP = 18,
            INPUT_X = 26, INPUT_Y = 46, RESULT_X = 134, RESULT_Y = 55, NEEDS_Y = 88, INVENTORY_Y = 122;

    private final ContainerLevelAccess access;
    private final Player player;
    final SimpleContainer input = new SimpleContainer(INPUTS) {
        @Override public void setChanged() { super.setChanged(); slotsChanged(this); }
    };
    private final ResultContainer result = new ResultContainer();
    private final DataSlot selected = DataSlot.standalone();

    public SuitFabricatorMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }

    public SuitFabricatorMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(SuitItems.FABRICATOR_MENU.get(), id);
        this.access = access;
        this.player = inventory.player;
        selected.set(-1);
        addDataSlot(selected);
        for (int i = 0; i < INPUTS; i++) addSlot(new Slot(input, i, INPUT_X + (i % 3) * 18, INPUT_Y + (i / 3) * 18));
        addSlot(new Slot(result, 0, RESULT_X, RESULT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public void onTake(Player taker, ItemStack stack) {
                SuitPatterns.Pattern pattern = pattern();
                if (pattern != null) pattern.consume(input);
                stack.onCraftedBy(taker.level(), taker, stack.getCount());
                access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1, 0.8F));
                setupResult();
                super.onTake(taker, stack);
            }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
    }

    public int selected() { return selected.get(); }

    public SuitPatterns.Pattern pattern() {
        int i = selected.get();
        return i >= 0 && i < SuitPatterns.all().size() ? SuitPatterns.all().get(i) : null;
    }

    /** True if this pattern's materials are all in the slots (for the screen's highlight). */
    public boolean ready(int index) { return SuitPatterns.all().get(index).matches(input); }

    @Override
    public boolean clickMenuButton(Player who, int id) {
        if (id < 0 || id >= SuitPatterns.all().size()) return false;
        selected.set(id);
        setupResult();
        return true;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == input) setupResult();
    }

    private void setupResult() {
        SuitPatterns.Pattern pattern = pattern();
        result.setItem(0, pattern != null && pattern.matches(input) ? pattern.make() : ItemStack.EMPTY);
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int end = slots.size();
        if (index == RESULT) {
            if (!moveItemStackTo(stack, INVENTORY, end, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        } else if (index < INPUTS) {
            if (!moveItemStackTo(stack, INVENTORY, end, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, INPUTS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(who, stack);
        return copy;
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        result.removeItemNoUpdate(0);
        access.execute((level, pos) -> clearContainer(who, input));
    }

    @Override
    public boolean stillValid(Player who) { return stillValid(access, who, SuitItems.SUIT_FABRICATOR.get()); }

    /** For tests: the input slots. */
    public SimpleContainer input() { return input; }
    public ItemStack result() { return result.getItem(0); }
}
