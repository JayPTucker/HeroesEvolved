package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns the reversible consequences of stealing from a slain Past echo. */
public final class TemporalParadoxService {
    private static final String TEMPORAL_LOOT_KEY = "heroes_evolved_temporal_loot";
    private static final int COLLAPSE_DURATION_TICKS = 20 * 60 * 5;
    // A five-minute rescue window should create pressure without killing a
    // full-health player long before the timeline can be repaired.
    private static final int DAMAGE_INTERVAL_TICKS = 20 * 30;
    private static final int FLICKER_INTERVAL_TICKS = 6;
    private static final Map<UUID, ParadoxState> ACTIVE_PARADOXES = new HashMap<>();

    private TemporalParadoxService() {
    }

    public static void onEchoKilled(
            UUID snapshotOwnerId,
            TemporalEchoData echo,
            ServerLevel level,
            net.minecraft.world.phys.Vec3 position
    ) {
        for (TemporalEchoInventoryEntry entry : echo.inventory()) {
            ItemStack loot = entry.stack().copy();
            markTemporalLoot(loot, snapshotOwnerId, echo.playerId());
            ItemEntity item = new ItemEntity(level, position.x, position.y + 0.4D,
                    position.z, loot);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    public static void onTemporalLootPickedUp(
            ServerPlayer traveller,
            ItemStack originalStack
    ) {
        LootIdentity identity = readTemporalLoot(originalStack);
        if (identity == null || !TemporalSnapshotService.isTemporalDimension(
                traveller.serverLevel())) {
            return;
        }

        ServerPlayer victim = traveller.getServer().getPlayerList().getPlayer(
                identity.victimId());
        if (victim == null) {
            return;
        }

        ItemStack normalStack = originalStack.copy();
        removeTemporalLootMarker(normalStack);
        int removed = removeMatchingItems(victim, normalStack);
        if (removed <= 0) {
            return;
        }

        ParadoxState state = ACTIVE_PARADOXES.computeIfAbsent(
                identity.victimId(), ignored -> new ParadoxState(
                identity.snapshotOwnerId(), identity.victimId(),
                        new ArrayList<>(), COLLAPSE_DURATION_TICKS,
                        traveller.serverLevel().getGameTime(),
                        victim.isInvisible()
                )
        );
        state.removedItems().add(normalStack.copyWithCount(removed));
        victim.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "You suddenly feel short of breath..."
        ));
    }

    public static void eraseTemporalLoot(ServerPlayer traveller) {
        for (int slot = 0; slot < traveller.getInventory().getContainerSize(); slot++) {
            ItemStack stack = traveller.getInventory().getItem(slot);
            if (readTemporalLoot(stack) != null) {
                traveller.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    public static boolean restoreByEcho(ServerPlayer restorer, UUID victimId) {
        ParadoxState state = ACTIVE_PARADOXES.get(victimId);
        if (state == null || !state.snapshotOwnerId().equals(
                restorer.getData(com.jayptucker.heroesevolved.data.ModDataAttachments
                        .PLAYER_TEMPORAL_VISIT.get()).ownerId())) {
            return false;
        }

        ServerPlayer victim = restorer.getServer().getPlayerList().getPlayer(victimId);
        if (victim != null) {
            for (ItemStack stack : state.removedItems()) {
                ItemStack remaining = stack.copy();
                victim.getInventory().add(remaining);
                if (!remaining.isEmpty()) {
                    victim.drop(remaining, false);
                }
            }
            victim.setInvisible(state.wasInvisible());
            victim.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Your breath returns as the timeline stabilizes."
            ));
        }
        ACTIVE_PARADOXES.remove(victimId);
        restorer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Timeline restored."
        ));
        return true;
    }

    public static void tick(MinecraftServer server) {
        var iterator = ACTIVE_PARADOXES.values().iterator();
        while (iterator.hasNext()) {
            ParadoxState state = iterator.next();
            ServerPlayer victim = server.getPlayerList().getPlayer(state.victimId());
            if (victim == null) {
                continue;
            }
            state.remainingTicks--;
            applyPhaseFlicker(victim, state);
            if (state.remainingTicks % DAMAGE_INTERVAL_TICKS == 0) {
                victim.hurt(victim.damageSources().magic(), 2.0F);
            }
            if (state.remainingTicks <= 0) {
                victim.setInvisible(state.wasInvisible());
                victim.kill();
                iterator.remove();
            }
        }
    }

    private static void markTemporalLoot(
            ItemStack stack, UUID snapshotOwnerId, UUID victimId
    ) {
        // Preserve any existing custom item data; the temporal marker must
        // not strip data from modded or specially configured items.
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
                tag.putString(TEMPORAL_LOOT_KEY,
                        snapshotOwnerId + ":" + victimId)
        );
    }

    private static LootIdentity readTemporalLoot(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TEMPORAL_LOOT_KEY)) {
            return null;
        }
        String[] parts = data.copyTag().getString(TEMPORAL_LOOT_KEY).split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new LootIdentity(UUID.fromString(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void removeTemporalLootMarker(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }

        CompoundTag tag = data.copyTag();
        tag.remove(TEMPORAL_LOOT_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static int removeMatchingItems(ServerPlayer player, ItemStack expected) {
        int wanted = expected.getCount();
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && wanted > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, expected)) {
                continue;
            }
            int taken = Math.min(wanted, stack.getCount());
            stack.shrink(taken);
            wanted -= taken;
            removed += taken;
        }
        return removed;
    }

    /**
     * Vanilla synchronizes invisibility to every client, which makes this a
     * reliable multiplayer phase effect without replacing the player renderer.
     */
    private static void applyPhaseFlicker(
            ServerPlayer victim,
            ParadoxState state
    ) {
        boolean fadingOut = (state.remainingTicks / FLICKER_INTERVAL_TICKS) % 2 == 0;
        victim.setInvisible(state.wasInvisible() || fadingOut);

        if (!fadingOut && state.remainingTicks % 10 == 0) {
            victim.serverLevel().sendParticles(
                    net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    victim.getX(), victim.getY() + 1.0D, victim.getZ(),
                    10, 0.25D, 0.55D, 0.25D, 0.04D
            );
        }
    }

    private record LootIdentity(UUID snapshotOwnerId, UUID victimId) {
    }

    private static final class ParadoxState {
        private final UUID snapshotOwnerId;
        private final UUID victimId;
        private final List<ItemStack> removedItems;
        private int remainingTicks;
        @SuppressWarnings("unused")
        private final long createdAt;
        private final boolean wasInvisible;

        private ParadoxState(UUID snapshotOwnerId, UUID victimId,
                             List<ItemStack> removedItems, int remainingTicks,
                             long createdAt, boolean wasInvisible) {
            this.snapshotOwnerId = snapshotOwnerId;
            this.victimId = victimId;
            this.removedItems = removedItems;
            this.remainingTicks = remainingTicks;
            this.createdAt = createdAt;
            this.wasInvisible = wasInvisible;
        }

        private UUID snapshotOwnerId() { return snapshotOwnerId; }
        private UUID victimId() { return victimId; }
        private List<ItemStack> removedItems() { return removedItems; }
        private boolean wasInvisible() { return wasInvisible; }
    }
}
