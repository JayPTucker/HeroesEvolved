package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Synchronizes single-chest inventory changes from the past to the present. */
public final class TemporalChestService {
    private static final Map<UUID, OpenTemporalChest> OPEN_CHESTS =
            new HashMap<>();
    private static final Set<UUID> PENDING_PARADOX_WARNINGS = new HashSet<>();
    private static final Set<UUID> PENDING_RESTORATION_MESSAGES =
            new HashSet<>();
    private static final Map<UUID, List<ParadoxState>> UNRESOLVED_PARADOXES =
            new HashMap<>();

    private TemporalChestService() {
    }

    public static void onOpen(ServerPlayer player, ChestMenu menu) {
        if (!TemporalSnapshotService.isTemporalDimension(player.serverLevel())
                || !(menu.getContainer() instanceof ChestBlockEntity pastChest)
                || !TemporalSnapshotService.isVisitorInsideSnapshot(
                        player, pastChest.getBlockPos())) {
            return;
        }

        ServerLevel present = TemporalSnapshotService.getPresentLevel(player);
        BlockPos presentPos = TemporalSnapshotService.toPresentPosition(
                player, pastChest.getBlockPos());
        if (present == null
                || !(present.getBlockEntity(presentPos)
                instanceof ChestBlockEntity presentChest)) {
            return;
        }

        // The Past must remain a real snapshot. Never refresh this inventory
        // from the Present on open, or later Present-day changes would leak
        // backward through time and erase possible paradox evidence.
        OPEN_CHESTS.put(player.getUUID(), new OpenTemporalChest(
                snapshotOwnerId(player),
                pastChest.getBlockPos(),
                presentPos,
                copyContents(pastChest)
        ));
    }

    public static void onClose(ServerPlayer player, ChestMenu menu) {
        OpenTemporalChest session = OPEN_CHESTS.remove(player.getUUID());
        if (session == null
                || !(menu.getContainer() instanceof ChestBlockEntity pastChest)
                || !pastChest.getBlockPos().equals(session.pastPosition())) {
            return;
        }

        ServerLevel present = TemporalSnapshotService.getPresentLevel(player);
        if (present == null
                || !(present.getBlockEntity(session.presentPosition())
                instanceof ChestBlockEntity presentChest)) {
            return;
        }

        for (int slot = 0; slot < pastChest.getContainerSize(); slot++) {
            ItemStack openedStack = session.openedContents().get(slot);
            ItemStack pastStack = pastChest.getItem(slot);
            ItemStack presentStack = presentChest.getItem(slot);

            if (applyPastChange(
                    presentChest,
                    slot,
                    openedStack,
                    pastStack,
                    presentStack
            )) {
                recordParadox(
                        session.snapshotOwnerId(),
                        session.presentPosition(),
                        slot,
                        openedStack
                );
            }
        }
        presentChest.setChanged();

        resolveRestoredTimeline(session, pastChest, presentChest);
    }

    /** A paradox belongs to the shared snapshot, not the player who caused it. */
    public static boolean hasActiveParadox(UUID snapshotOwnerId) {
        return PENDING_PARADOX_WARNINGS.contains(snapshotOwnerId)
                || UNRESOLVED_PARADOXES.containsKey(snapshotOwnerId);
    }

    public static boolean consumeRestorationMessage(UUID snapshotOwnerId) {
        return PENDING_RESTORATION_MESSAGES.remove(snapshotOwnerId);
    }

    /** A replacement snapshot starts a new timeline and clears old history. */
    public static void clearSnapshotHistory(ServerPlayer player) {
        UNRESOLVED_PARADOXES.remove(player.getUUID());
        PENDING_PARADOX_WARNINGS.remove(player.getUUID());
        PENDING_RESTORATION_MESSAGES.remove(player.getUUID());
    }

    /**
     * A Past chest break destroys its matching Present chest without item
     * drops. Before doing so, verify its saved contents still existed there;
     * otherwise the player has created a temporal contradiction.
     */
    public static void destroyPastChest(
            ServerPlayer player,
            ChestBlockEntity pastChest,
            ServerLevel present,
            BlockPos presentPosition
    ) {
        if (!(present.getBlockEntity(presentPosition)
                instanceof ChestBlockEntity presentChest)) {
            PENDING_PARADOX_WARNINGS.add(snapshotOwnerId(player));
            return;
        }

        List<ItemStack> availablePresentItems = copyContents(presentChest);
        for (int slot = 0; slot < pastChest.getContainerSize(); slot++) {
            ItemStack requiredStack = pastChest.getItem(slot);
            if (!requiredStack.isEmpty()
                    && !consumeMatchingItems(
                    availablePresentItems, requiredStack)) {
                // The player can repair this contradiction by rebuilding the
                // chest at the same Past position and restoring this stack.
                recordParadox(
                        snapshotOwnerId(player),
                        presentPosition,
                        slot,
                        requiredStack
                );
            }
        }

        // Anything left after subtracting the Past inventory was placed in
        // the Present later. Preserve it by dropping it in the Present world
        // before the chest is erased from that timeline.
        for (ItemStack futureItem : availablePresentItems) {
            if (!futureItem.isEmpty()) {
                Block.popResource(present, presentPosition, futureItem);
            }
        }

        // Clearing the inventory before removing the block prevents Minecraft
        // from spawning any dropped items in either timeline.
        for (int slot = 0; slot < presentChest.getContainerSize(); slot++) {
            presentChest.setItem(slot, ItemStack.EMPTY);
        }
        presentChest.setChanged();
        present.setBlock(presentPosition, Blocks.AIR.defaultBlockState(), 3);

    }

    private static boolean consumeMatchingItems(
            List<ItemStack> availableItems,
            ItemStack requiredStack
    ) {
        int remaining = requiredStack.getCount();
        for (ItemStack availableStack : availableItems) {
            if (!ItemStack.isSameItemSameComponents(
                    requiredStack, availableStack
            )) {
                continue;
            }

            int consumed = Math.min(remaining, availableStack.getCount());
            availableStack.shrink(consumed);
            remaining -= consumed;
            if (remaining == 0) {
                return true;
            }
        }
        return false;
    }

    private static void resolveRestoredTimeline(
            OpenTemporalChest session,
            ChestBlockEntity pastChest,
            ChestBlockEntity presentChest
    ) {
        UUID snapshotOwnerId = session.snapshotOwnerId();
        List<ParadoxState> paradoxes = UNRESOLVED_PARADOXES.get(snapshotOwnerId);
        if (paradoxes == null) {
            return;
        }

        List<ParadoxState> restoredStates = paradoxes.stream()
                .filter(paradox -> paradox.presentPosition().equals(
                        session.presentPosition()
                ) && ItemStack.isSameItemSameComponents(
                        pastChest.getItem(paradox.slot()),
                        paradox.requiredStack()
                ) && pastChest.getItem(paradox.slot()).getCount()
                        >= paradox.requiredStack().getCount()
                        && ItemStack.isSameItemSameComponents(
                        presentChest.getItem(paradox.slot()),
                        paradox.requiredStack()
                ) && presentChest.getItem(paradox.slot()).getCount()
                        >= paradox.requiredStack().getCount())
                .toList();
        boolean restored = !restoredStates.isEmpty();
        paradoxes.removeAll(restoredStates);

        if (paradoxes.isEmpty()) {
            UNRESOLVED_PARADOXES.remove(snapshotOwnerId);
            if (restored) {
                // All repairable contradictions are fixed.
                PENDING_PARADOX_WARNINGS.remove(snapshotOwnerId);
                PENDING_RESTORATION_MESSAGES.add(snapshotOwnerId);
            }
        }
    }

    /**
     * Applies only the known Past change. If the Present diverged while the
     * chest was open, never overwrite its unrelated contents or recreate a
     * missing item; report a paradox instead.
     */
    private static boolean applyPastChange(
            ChestBlockEntity presentChest,
            int slot,
            ItemStack openedStack,
            ItemStack pastStack,
            ItemStack presentStack
    ) {
        if (sameStack(openedStack, presentStack)) {
            // The Present was unchanged, so the Past's final slot is safe to
            // use as the complete new state.
            presentChest.setItem(slot, pastStack.copy());
            return false;
        }

        if (isRemoval(openedStack, pastStack)) {
            int amountRemoved = openedStack.getCount() - pastStack.getCount();
            if (ItemStack.isSameItemSameComponents(openedStack, presentStack)) {
                int removable = Math.min(amountRemoved, presentStack.getCount());
                presentStack.shrink(removable);
                presentChest.setItem(slot, presentStack);
                return removable < amountRemoved;
            }

            return true;
        }

        if (openedStack.isEmpty() && !pastStack.isEmpty()
                && presentStack.isEmpty()) {
            // A newly placed Past item can safely fill an empty Present slot.
            presentChest.setItem(slot, pastStack.copy());
        }

        // A divergent Present slot is deliberately left untouched. This keeps
        // the feature from deleting or replacing another player's later item.
        return false;
    }

    private static boolean isRemoval(ItemStack openedStack, ItemStack pastStack) {
        return !openedStack.isEmpty()
                && (pastStack.isEmpty()
                || (ItemStack.isSameItemSameComponents(openedStack, pastStack)
                && pastStack.getCount() < openedStack.getCount()));
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return (first.isEmpty() && second.isEmpty())
                || (!first.isEmpty() && !second.isEmpty()
                && ItemStack.isSameItemSameComponents(first, second)
                && first.getCount() == second.getCount());
    }

    private static void recordParadox(
            UUID snapshotOwnerId,
            BlockPos presentPosition,
            int slot,
            ItemStack requiredStack
    ) {
        List<ParadoxState> paradoxes = UNRESOLVED_PARADOXES.computeIfAbsent(
                snapshotOwnerId, ignored -> new ArrayList<>()
        );
        boolean alreadyRecorded = paradoxes.stream().anyMatch(paradox ->
                paradox.presentPosition().equals(presentPosition)
                        && paradox.slot() == slot
        );
        if (!alreadyRecorded) {
            paradoxes.add(new ParadoxState(
                    presentPosition.immutable(), slot, requiredStack.copy()
            ));
        }
    }

    private static void copyContents(ChestBlockEntity source,
            ChestBlockEntity destination) {
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            destination.setItem(slot, source.getItem(slot).copy());
        }
        destination.setChanged();
    }

    private static List<ItemStack> copyContents(ChestBlockEntity chest) {
        List<ItemStack> contents = new ArrayList<>(chest.getContainerSize());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            contents.add(chest.getItem(slot).copy());
        }
        return List.copyOf(contents);
    }

    private record OpenTemporalChest(
            UUID snapshotOwnerId,
            BlockPos pastPosition,
            BlockPos presentPosition,
            List<ItemStack> openedContents
    ) {
    }

    private static UUID snapshotOwnerId(ServerPlayer player) {
        PlayerTemporalVisitData visit = player.getData(
                ModDataAttachments.PLAYER_TEMPORAL_VISIT.get()
        );
        return visit.visiting() ? visit.ownerId() : player.getUUID();
    }

    private record ParadoxState(
            BlockPos presentPosition,
            int slot,
            ItemStack requiredStack
    ) {
    }
}
