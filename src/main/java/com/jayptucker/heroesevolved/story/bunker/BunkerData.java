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
     * Bumped when the layout changes. An older bunker is never rebuilt over a player's changes; it only
     * gets the pieces a later layout added ({@link BunkerBuilder#upgrade}). 2: Exhibit 1, Isaac's explosion.
     */
    public static final int LAYOUT = 2;
    public boolean built;
    public BlockPos origin = BlockPos.ZERO;
    public int layout;
    public final Map<UUID, Integer> pods = new HashMap<>();

    public static BunkerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(BunkerData::new, BunkerData::load), NAME);
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
        CompoundTag assigned = new CompoundTag();
        pods.forEach((id, pod) -> assigned.putInt(id.toString(), pod));
        tag.put("pods", assigned);
        return tag;
    }

    public static BunkerData load(CompoundTag tag, HolderLookup.Provider registries) {
        BunkerData data = new BunkerData();
        data.built = tag.getBoolean("built");
        data.origin = BlockPos.of(tag.getLong("origin"));
        data.layout = tag.getInt("layout");
        CompoundTag assigned = tag.getCompound("pods");
        for (String key : assigned.getAllKeys()) data.pods.put(UUID.fromString(key), assigned.getInt(key));
        return data;
    }
}
