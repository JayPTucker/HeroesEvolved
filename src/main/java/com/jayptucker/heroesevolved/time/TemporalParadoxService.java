package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the consequences of changing history: stealing from a slain Past
 * echo, and the far more dangerous act of meeting your own past self.
 */
public final class TemporalParadoxService {
    public static final ResourceKey<DamageType> TEMPORAL_PARADOX_DAMAGE =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(
                            HeroesEvolved.MOD_ID,
                            "temporal_paradox"
                    )
            );

    private static final String TEMPORAL_LOOT_KEY = "heroes_evolved_temporal_loot";
    // A pulsing violet edge: the world itself seems to be coming apart.
    private static final int PARADOX_TINT = 0x60602090;
    private static final int COLLAPSE_DURATION_TICKS = 20 * 60 * 5;
    // A five-minute rescue window should create pressure without killing a
    // full-health player long before the timeline can be repaired.
    private static final int DAMAGE_INTERVAL_TICKS = 20 * 30;
    private static final int FLICKER_INTERVAL_TICKS = 6;
    // Two versions of one person cannot share a moment for long. The
    // traveller must leave the Past before this collapse completes.
    private static final int SELF_PARADOX_DURATION_TICKS = 20 * 90;
    private static final int SELF_PARADOX_DAMAGE_INTERVAL_TICKS = 20 * 15;
    private static final Map<UUID, ParadoxState> ACTIVE_PARADOXES = new HashMap<>();
    private static final Map<UUID, SelfParadoxState> SELF_PARADOXES =
            new HashMap<>();

    private TemporalParadoxService() {
    }

    /**
     * A traveller has made contact with their own past self. History has
     * diverged around them, and they begin to unravel until they leave.
     */
    public static void onSelfEncounter(ServerPlayer traveller) {
        if (SELF_PARADOXES.containsKey(traveller.getUUID())) {
            return;
        }

        SELF_PARADOXES.put(traveller.getUUID(), new SelfParadoxState(
                SELF_PARADOX_DURATION_TICKS,
                traveller.isInvisible()
        ));
        traveller.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION, 20 * 8, 0
        ));
        traveller.serverLevel().playSound(null,
                traveller.getX(), traveller.getY(), traveller.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                1.0F, 0.6F);
        ScreenTintPayload.send(traveller, PARADOX_TINT,
                SELF_PARADOX_DURATION_TICKS, ScreenTintPayload.Style.PULSE);
        traveller.sendSystemMessage(Component.literal(
                "Your past self sees you. The timeline begins to tear - "
                        + "return to the Present before it collapses!"
        ).withStyle(ChatFormatting.DARK_PURPLE));
    }

    public static boolean hasSelfParadox(ServerPlayer player) {
        return SELF_PARADOXES.containsKey(player.getUUID());
    }

    /** Leaving the Past separates the two versions and heals the tear. */
    public static void resolveSelfParadox(ServerPlayer player) {
        SelfParadoxState state = SELF_PARADOXES.remove(player.getUUID());
        if (state == null) {
            return;
        }

        player.setInvisible(state.wasInvisible());
        ScreenTintPayload.send(player, PARADOX_TINT, 0,
                ScreenTintPayload.Style.PULSE);
        ScreenTintPayload.send(player, 0x60FFFFFF, 6, ScreenTintPayload.Style.FLASH);
        player.sendSystemMessage(Component.literal(
                "Time snaps back into place."
        ).withStyle(ChatFormatting.GRAY));
    }

    /** Damage dealt to your own past self is felt by your present self. */
    public static void mirrorSelfDamage(ServerPlayer attacker, float amount) {
        onSelfEncounter(attacker);
        attacker.hurt(paradoxDamage(attacker), amount);
    }

    /** The grandfather paradox: without a past, the present self cannot exist. */
    public static void eraseFromTimeline(ServerPlayer player) {
        SelfParadoxState state = SELF_PARADOXES.remove(player.getUUID());
        if (state != null) {
            player.setInvisible(state.wasInvisible());
        }
        player.serverLevel().sendParticles(
                net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                80, 0.4D, 0.9D, 0.4D, 0.2D
        );
        player.hurt(paradoxDamage(player), Float.MAX_VALUE);
    }

    private static DamageSource paradoxDamage(ServerPlayer player) {
        return player.damageSources().source(TEMPORAL_PARADOX_DAMAGE);
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
        persist(victim, state);
        ScreenTintPayload.send(victim, PARADOX_TINT, state.remainingTicks,
                ScreenTintPayload.Style.PULSE);
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
        if (victim == null) {
            restorer.displayClientMessage(Component.literal("The person whose timeline needs repair must be online."), true);
            return false;
        }
        if (victim != null) {
            for (ItemStack stack : state.removedItems()) {
                ItemStack remaining = stack.copy();
                victim.getInventory().add(remaining);
                if (!remaining.isEmpty()) {
                    victim.drop(remaining, false);
                }
            }
            victim.setInvisible(state.wasInvisible());
            ScreenTintPayload.send(victim, PARADOX_TINT, 0,
                    ScreenTintPayload.Style.PULSE);
            victim.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Your breath returns as the timeline stabilizes."
            ));
        }
        ACTIVE_PARADOXES.remove(victimId);
        victim.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.TEMPORAL_RECOVERY, TemporalRecoveryData.empty());
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
            if (state.remainingTicks % 20 == 0) persist(victim, state);
            applyPhaseFlicker(victim, state.remainingTicks, state.wasInvisible());
            if (state.remainingTicks % DAMAGE_INTERVAL_TICKS == 0) {
                victim.hurt(paradoxDamage(victim), 2.0F);
            }
            if (state.remainingTicks <= 0) {
                victim.setInvisible(state.wasInvisible());
                victim.hurt(paradoxDamage(victim), Float.MAX_VALUE);
                victim.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.TEMPORAL_RECOVERY, TemporalRecoveryData.empty());
                iterator.remove();
            }
        }

        var selfIterator = SELF_PARADOXES.entrySet().iterator();
        while (selfIterator.hasNext()) {
            Map.Entry<UUID, SelfParadoxState> entry = selfIterator.next();
            ServerPlayer traveller = server.getPlayerList().getPlayer(
                    entry.getKey()
            );
            if (traveller == null) {
                continue;
            }

            SelfParadoxState state = entry.getValue();
            state.remainingTicks--;
            applyPhaseFlicker(traveller, state.remainingTicks,
                    state.wasInvisible());
            if (state.remainingTicks % SELF_PARADOX_DAMAGE_INTERVAL_TICKS == 0) {
                traveller.hurt(paradoxDamage(traveller), 2.0F);
            }
            if (state.remainingTicks == 20 * 15) {
                traveller.sendSystemMessage(Component.literal(
                        "You are fading from existence..."
                ).withStyle(ChatFormatting.DARK_RED));
            }
            if (state.remainingTicks <= 0) {
                selfIterator.remove();
                traveller.setInvisible(state.wasInvisible());
                traveller.hurt(paradoxDamage(traveller), Float.MAX_VALUE);
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

    private static void persist(ServerPlayer victim, ParadoxState state) {
        victim.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.TEMPORAL_RECOVERY,
                new TemporalRecoveryData(state.snapshotOwnerId, state.removedItems.stream().map(ItemStack::copy).toList(),
                        state.remainingTicks, state.wasInvisible));
    }
    public static void login(ServerPlayer player) {
        TemporalRecoveryData data = player.getData(com.jayptucker.heroesevolved.data.ModDataAttachments.TEMPORAL_RECOVERY);
        if (data.remaining() > 0 && !data.removed().isEmpty()) {
            ACTIVE_PARADOXES.put(player.getUUID(), new ParadoxState(data.owner(), player.getUUID(),
                    new ArrayList<>(data.removed().stream().map(ItemStack::copy).toList()), data.remaining(),
                    player.level().getGameTime(), data.invisible()));
            player.sendSystemMessage(Component.literal("Your timeline still needs repair. Your missing possessions remain recoverable."));
        }
    }
    public static void logout(ServerPlayer player) {
        ParadoxState state = ACTIVE_PARADOXES.remove(player.getUUID());
        if (state != null) { persist(player, state); player.setInvisible(state.wasInvisible); }
        resolveSelfParadox(player);
    }
    public static void clearRuntime() { ACTIVE_PARADOXES.clear(); SELF_PARADOXES.clear(); }

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
            int remainingTicks,
            boolean wasInvisible
    ) {
        boolean fadingOut = (remainingTicks / FLICKER_INTERVAL_TICKS) % 2 == 0;
        victim.setInvisible(wasInvisible || fadingOut);

        if (!fadingOut && remainingTicks % 10 == 0) {
            victim.serverLevel().sendParticles(
                    net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    victim.getX(), victim.getY() + 1.0D, victim.getZ(),
                    10, 0.25D, 0.55D, 0.25D, 0.04D
            );
        }
    }

    private record LootIdentity(UUID snapshotOwnerId, UUID victimId) {
    }

    private static final class SelfParadoxState {
        private int remainingTicks;
        private final boolean wasInvisible;

        private SelfParadoxState(int remainingTicks, boolean wasInvisible) {
            this.remainingTicks = remainingTicks;
            this.wasInvisible = wasInvisible;
        }

        private boolean wasInvisible() { return wasInvisible; }
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
