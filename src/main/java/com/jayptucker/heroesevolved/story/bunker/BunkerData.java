package com.jayptucker.heroesevolved.story.bunker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Where the stasis bunker was built, and which sleeper woke in which pod. Built once per world. */
public final class BunkerData extends SavedData {
    public static final String NAME = "heroesevolved_stasis_bunker";
    /**
     * Bumped when the layout changes. Older bunkers receive targeted updates ({@link BunkerBuilder#upgrade}).
     * 2 and 3: Exhibit 1. 4: alarm beacons. 5: the signal receiver and recovery rooms. 6: the receiver is a Simple Radio
     * console. Bunkers built since the Sublevel 4 rebuild ({@link #design} 2) start at the latest layout and take no updates.
     */
    public static final int LAYOUT = 6;
    public boolean built;
    public BlockPos origin = BlockPos.ZERO;
    public int layout;
    /** The main power: off in a new bunker until its first sleeper steps out of a pod (see {@link BunkerPower}). */
    public boolean powered;
    public final Map<UUID, Integer> pods = new HashMap<>();
    /** Which bunker was built: 1 the original rooms ({@link BunkerLayout#LEGACY}), 2 the Sublevel 4 rebuild ({@link BunkerLayout#STASIS}). */
    public int design = BunkerLayout.STASIS_DESIGN;
    /** The skulls outside the vault and on the stair have been laid ({@link BunkerStair#remains}), once per bunker. */
    public boolean remains;
    /** The rooms (by blueprint) whose skulls have been laid ({@link BunkerBones}), each once. */
    public final java.util.Set<String> strewn = new java.util.HashSet<>();
    /** The one sleeper sealed in the private pod in 4-R, once someone has been; nobody else ever is. */
    public @org.jetbrains.annotations.Nullable UUID lab;
    /** Everyone told of the bunker or woken in it ({@link BunkerRadios} counts these with {@link #pods}). */
    public final java.util.Set<UUID> counted = new java.util.HashSet<>();
    /** Radios put in the Records supply chest so far ({@link BunkerRadios}). */
    public int radios;

    public static BunkerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(BunkerData::new, BunkerData::load), NAME);
    }

    public BunkerLayout layout() { return BunkerLayout.of(this); }

    /** Whether the private pod in 4-R can still take its one sleeper: this bunker has it, and nobody has slept in it. */
    public boolean labOpen() { return (!built || design >= BunkerLayout.STASIS_DESIGN) && lab == null; }

    /** Seals {@code player} in the private pod for good: it is theirs, and no one else's. */
    public void claimLab(UUID player) {
        lab = player;
        pods.put(player, BunkerLayout.LAB_POD);
        setDirty();
    }

    /** The pod this sleeper wakes in: their own if they have one, otherwise the first unused, then round-robin. */
    public int pod(UUID player, int count) {
        return pod(player, count, -1);
    }

    /** As {@link #pod(UUID, int)}, but a new sleeper never gets pod {@code taken} (someone still asleep in it). */
    public int pod(UUID player, int count, int taken) {
        Integer existing = pods.get(player);
        if (existing != null) return existing;
        int chosen = pods.size() % count;
        if (chosen == taken) chosen = (chosen + 1) % count;
        for (int i = 0; i < count; i++) if (i != taken && !pods.containsValue(i)) { chosen = i; break; }
        pods.put(player, chosen);
        setDirty();
        return chosen;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("built", built);
        tag.putLong("origin", origin.asLong());
        tag.putInt("layout", layout);
        tag.putBoolean("powered", powered);
        tag.putInt("design", design);
        tag.putBoolean("remains", remains);
        net.minecraft.nbt.ListTag bones = new net.minecraft.nbt.ListTag();
        for (String room : strewn) bones.add(net.minecraft.nbt.StringTag.valueOf(room));
        tag.put("strewn", bones);
        if (lab != null) tag.putUUID("lab", lab);
        CompoundTag assigned = new CompoundTag();
        pods.forEach((id, pod) -> assigned.putInt(id.toString(), pod));
        tag.put("pods", assigned);
        net.minecraft.nbt.ListTag told = new net.minecraft.nbt.ListTag();
        for (UUID id : counted) told.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        tag.put("counted", told);
        tag.putInt("radios", radios);
        return tag;
    }

    public static BunkerData load(CompoundTag tag, HolderLookup.Provider registries) {
        BunkerData data = new BunkerData();
        data.built = tag.getBoolean("built");
        data.origin = BlockPos.of(tag.getLong("origin"));
        data.layout = tag.getInt("layout");
        // Bunkers saved before the power could be out were built with their lights on.
        data.powered = !tag.contains("powered") || tag.getBoolean("powered");
        // A bunker saved before the rebuild was built with the original rooms.
        data.design = tag.contains("design") ? tag.getInt("design") : 1;
        if (tag.hasUUID("lab")) data.lab = tag.getUUID("lab");
        data.remains = tag.getBoolean("remains");
        for (net.minecraft.nbt.Tag room : tag.getList("strewn", net.minecraft.nbt.Tag.TAG_STRING)) data.strewn.add(room.getAsString());
        CompoundTag assigned = tag.getCompound("pods");
        for (String key : assigned.getAllKeys()) data.pods.put(UUID.fromString(key), assigned.getInt(key));
        for (net.minecraft.nbt.Tag t : tag.getList("counted", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) data.counted.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        data.radios = tag.getInt("radios");
        return data;
    }
}
