package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.UUID;

/** Saved, one-use death-loot recovery. The old drops are invalidated before the remembered items are handed back. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TemporalRevenge extends SavedData {
    private static final String MARK = "heroesevolved_revenge_drop";
    private final CompoundTag records;
    private boolean hasRedeemed;
    public TemporalRevenge() { this(new CompoundTag()); }
    private TemporalRevenge(CompoundTag tag) {
        records = tag;
        CompoundTag deaths = tag.getCompound("deaths");
        hasRedeemed = deaths.getAllKeys().stream().anyMatch(key -> deaths.getCompound(key).getBoolean("redeemed"));
    }
    public boolean hasRedeemed() { return hasRedeemed; }
    public static TemporalRevenge get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(TemporalRevenge::new,
                (tag, registries) -> new TemporalRevenge(tag)), "heroesevolved_temporal_revenge");
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) { return records.copy(); }
    private CompoundTag section(String key) {
        if (!records.contains(key, Tag.TAG_COMPOUND)) records.put(key, new CompoundTag());
        return records.getCompound(key);
    }
    public void recorded(ServerPlayer owner, PlayerTemporalSnapshotData snapshot) {
        for (String key : section("deaths").getAllKeys())
            section("deaths").getCompound(key).getCompound("eligible").remove(owner.getUUID().toString());
        CompoundTag entry = new CompoundTag();
        entry.putString("dimension", snapshot.sourceDimension().toString());
        entry.putInt("x", snapshot.sourceMinX()); entry.putInt("z", snapshot.sourceMinZ());
        entry.putInt("width", TemporalSnapshotService.snapshotWidthBlocks());
        CompoundTag killers = new CompoundTag();
        snapshot.echoes().forEach(e -> killers.putBoolean(e.playerId().toString(), true));
        snapshot.entityEchoes().forEach(e -> killers.putBoolean(e.entityId().toString(), true));
        entry.put("killers", killers);
        section("snapshots").put(owner.getUUID().toString(), entry); setDirty();
    }
    /** Entry consumes the saved snapshot even if no revenge is earned. */
    public void entered(UUID owner) { section("snapshots").remove(owner.toString()); setDirty(); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)
                || TemporalSnapshotService.isTemporalDimension(victim.serverLevel()) || event.getDrops().isEmpty()) return;
        Entity killer = event.getSource().getEntity();
        if (killer == null || killer == victim) return;
        TemporalRevenge data = get(victim.getServer());
        CompoundTag eligible = new CompoundTag();
        CompoundTag snapshots = data.section("snapshots");
        for (String key : snapshots.getAllKeys()) {
            CompoundTag snapshot = snapshots.getCompound(key);
            int x = snapshot.getInt("x"), z = snapshot.getInt("z"), width = snapshot.getInt("width");
            if (snapshot.getString("dimension").equals(victim.level().dimension().location().toString())
                    && victim.getX() >= x && victim.getX() < x + width && victim.getZ() >= z && victim.getZ() < z + width
                    && snapshot.getCompound("killers").getBoolean(killer.getUUID().toString())) eligible.putBoolean(key, true);
        }
        if (eligible.isEmpty()) return;
        UUID death = UUID.randomUUID();
        CompoundTag record = new CompoundTag();
        record.putUUID("victim", victim.getUUID()); record.putUUID("killer", killer.getUUID()); record.put("eligible", eligible);
        ListTag items = new ListTag();
        for (ItemEntity drop : event.getDrops()) {
            items.add(drop.getItem().save(victim.registryAccess()));
            CustomData.update(DataComponents.CUSTOM_DATA, drop.getItem(), tag -> tag.putUUID(MARK, death));
        }
        record.put("items", items);
        data.section("deaths").put(death.toString(), record); data.setDirty();
    }
    public void defeated(MinecraftServer server, UUID owner, UUID originalKiller) {
        CompoundTag deaths = section("deaths");
        for (String key : deaths.getAllKeys()) {
            CompoundTag record = deaths.getCompound(key);
            if (record.getBoolean("redeemed") || !record.getCompound("eligible").getBoolean(owner.toString())
                    || !record.getUUID("killer").equals(originalKiller)) continue;
            record.putBoolean("redeemed", true);
            hasRedeemed = true;
            String victim = record.getUUID("victim").toString();
            ListTag waiting = section("pending").getList(victim, Tag.TAG_COMPOUND);
            waiting.addAll(record.getList("items", Tag.TAG_COMPOUND).copy());
            section("pending").put(victim, waiting);
            record.remove("items"); setDirty();
        }
        // Purge original stacks before any recipient receives replacements.
        for (ServerLevel level : server.getAllLevels()) for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity drop && clean(server, drop.getItem())) drop.discard();
            if (entity instanceof Container container) clean(server, container);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clean(server, player.getInventory());
            deliver(player);
        }
    }
    public static boolean clean(MinecraftServer server, ItemStack stack) {
        if (stack.isEmpty()) return false;
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return false;
        CompoundTag tag = custom.copyTag();
        if (!tag.hasUUID(MARK)) return false;
        if (!get(server).section("deaths").getCompound(tag.getUUID(MARK).toString()).getBoolean("redeemed")) return false;
        stack.setCount(0); return true;
    }
    public static void clean(MinecraftServer server, Container container) {
        boolean changed = false;
        for (int i = 0; i < container.getContainerSize(); i++) changed |= clean(server, container.getItem(i));
        if (changed) container.setChanged();
    }
    public void deliver(ServerPlayer player) {
        if (!player.isAlive() || TemporalSnapshotService.isTemporalDimension(player.serverLevel())) return;
        clean(player.getServer(), player.getInventory());
        ListTag waiting = section("pending").getList(player.getUUID().toString(), Tag.TAG_COMPOUND);
        if (waiting.isEmpty()) return;
        section("pending").remove(player.getUUID().toString()); setDirty();
        for (Tag tag : waiting) {
            ItemStack stack = ItemStack.parseOptional(player.registryAccess(), (CompoundTag) tag);
            if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
        }
        player.inventoryMenu.broadcastFullState();
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("The death you avenged gives back what you dropped."));
    }
    @SubscribeEvent public static void player(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) clean(player.getServer(), player.getInventory());
    }
    @SubscribeEvent public static void item(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof ItemEntity drop && drop.level() instanceof ServerLevel level
                && clean(level.getServer(), drop.getItem())) { drop.discard(); event.setCanceled(true); }
    }
    @SubscribeEvent public static void open(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player)
            for (var slot : event.getContainer().slots) clean(player.getServer(), slot.getItem());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { clean(player.getServer(), player.getInventory()); get(player.getServer()).deliver(player); }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) get(player.getServer()).deliver(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) get(player.getServer()).deliver(player);
    }
}
