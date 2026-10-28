package com.jayptucker.heroesevolved.formula;

import com.jayptucker.heroesevolved.registry.ModItems;
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

/** Furnace layout: sample above, redstone below, catalyst on the right. */
public final class CentrifugeMenu extends LabMenu {
    private final Container container;
    private final ContainerData data;

    public CentrifugeMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(3), new SimpleContainerData(3));
    }

    public CentrifugeMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.CENTRIFUGE.get(), id, 3);
        checkContainerSize(container, 3);
        this.container = container;
        this.data = data;
        addSlot(new Slot(container, CentrifugeBlockEntity.INPUT, 56, 17) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.BLOOD_SAMPLE.get()); }
        });
        addSlot(new Slot(container, CentrifugeBlockEntity.FUEL, 56, 53) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.REDSTONE); }
        });
        addSlot(new Slot(container, CentrifugeBlockEntity.OUTPUT, 116, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addPlayerInventory(inventory);
        addDataSlots(data);
    }

    public int progress() { return data.get(0); }
    public int total() { return Math.max(1, data.get(1)); }
    public boolean charged() { return data.get(2) > 0; }

    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

}
