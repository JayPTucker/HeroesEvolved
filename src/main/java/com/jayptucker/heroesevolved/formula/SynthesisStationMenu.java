package com.jayptucker.heroesevolved.formula;

import com.jayptucker.heroesevolved.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Brewing stand layout: three bottles, the ingredient on top, glowstone on the left. */
public final class SynthesisStationMenu extends LabMenu {
    private final Container container;
    private final ContainerData data;

    public SynthesisStationMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(5), new SimpleContainerData(2));
    }

    public SynthesisStationMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.SYNTHESIS_STATION.get(), id, 5);
        checkContainerSize(container, 5);
        this.container = container;
        this.data = data;
        int[][] bottles = {{56, 51}, {79, 58}, {102, 51}};
        for (int slot = 0; slot < 3; slot++) {
            addSlot(new Slot(container, slot, bottles[slot][0], bottles[slot][1]) {
                @Override public boolean mayPlace(ItemStack stack) { return SynthesisStationBlockEntity.isBottle(stack); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        addSlot(new Slot(container, SynthesisStationBlockEntity.INGREDIENT, 79, 17) {
            @Override public boolean mayPlace(ItemStack stack) { return SynthesisStationBlockEntity.isIngredient(stack); }
        });
        addSlot(new Slot(container, SynthesisStationBlockEntity.FUEL, 17, 17) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.GLOWSTONE_DUST); }
        });
        addPlayerInventory(inventory);
        addDataSlots(data);
    }

    public int brewTime() { return data.get(0); }
    public int fuel() { return data.get(1); }

    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

}
