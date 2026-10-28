package com.jayptucker.heroesevolved.formula;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Shared player-inventory layout and shift-click handling for the lab menus. Machine slots come first. */
abstract class LabMenu extends AbstractContainerMenu {
    private final int machineSlots;

    LabMenu(MenuType<?> type, int id, int machineSlots) {
        super(type, id);
        this.machineSlots = machineSlots;
    }

    void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
    }

    /** Shift-click moves into the first machine slot that accepts the item, or back to the player. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int target = 0; target < machineSlots && !stack.isEmpty(); target++)
                if (slots.get(target).mayPlace(stack) && moveItemStackTo(stack, target, target + 1, false)) moved = true;
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
