package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.service.PowerRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Seeing what another player carries, through their eyes: a five-row chest screen that mirrors their inventory (three rows
 * of pack, the hotbar, then armour and the off hand) and refreshes every tick. It holds copies, never their items, and
 * every click is refused, so nothing can be taken, moved or duplicated. It closes when they leave, die, change dimension
 * or move out of reach, or when the telepath can no longer use their power.
 */
final class MindsEyeMenu extends ChestMenu {
    static final int ROWS = 5;
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
    private final ServerPlayer viewer, target;
    private final SimpleContainer view;

    private MindsEyeMenu(int id, ServerPlayer viewer, ServerPlayer target, SimpleContainer view) {
        super(MenuType.GENERIC_9x5, id, viewer.getInventory(), view, ROWS);
        this.viewer = viewer;
        this.target = target;
        this.view = view;
        mirror();
    }

    static MindsEyeMenu open(int id, Inventory inventory, ServerPlayer viewer, ServerPlayer target) {
        return new MindsEyeMenu(id, viewer, target, new SimpleContainer(9 * ROWS));
    }

    ServerPlayer target() { return target; }

    /** Copies what they carry into the view: pack (rows 1–3), hotbar (row 4), armour and off hand (row 5). */
    private void mirror() {
        Inventory theirs = target.getInventory();
        for (int i = 0; i < 27; i++) set(i, theirs.getItem(9 + i));
        for (int i = 0; i < 9; i++) set(27 + i, theirs.getItem(i));
        for (int i = 0; i < WORN.length; i++) set(36 + i, target.getItemBySlot(WORN[i]));
    }

    private void set(int slot, ItemStack stack) {
        if (!ItemStack.matches(view.getItem(slot), stack)) view.setItem(slot, stack.copy());
    }

    @Override public void broadcastChanges() {
        mirror();
        super.broadcastChanges();
    }

    /** Look, don't touch: every click is refused, and the client is told so at once. */
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        setCarried(ItemStack.EMPTY);
        sendAllDataToRemote();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean canDragTo(net.minecraft.world.inventory.Slot slot) { return false; }

    @Override public boolean stillValid(Player player) {
        return target.isAlive() && !target.hasDisconnected() && target.level() == viewer.level()
                && target.distanceToSqr(viewer) <= TelepathySkills.EYE_RANGE * TelepathySkills.EYE_RANGE && PowerRules.canAct(viewer);
    }
}
