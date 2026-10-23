package com.jayptucker.heroesevolved.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-persistent record of entities killed in someone's Past. Each one has
 * been erased from the Present, so it must never load there again, and must
 * not reappear on later visits to that snapshot.
 */
public final class TemporalErasureSavedData extends SavedData {
    private static final String DATA_NAME = "heroesevolved_temporal_erasures";

    private final Map<UUID, Set<UUID>> erasedBySnapshotOwner = new HashMap<>();
    private final Set<UUID> allErased = new HashSet<>();

    public static TemporalErasureSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(
                        TemporalErasureSavedData::new,
                        TemporalErasureSavedData::load
                ),
                DATA_NAME
        );
    }

    private static TemporalErasureSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        TemporalErasureSavedData data = new TemporalErasureSavedData();
        CompoundTag snapshots = tag.getCompound("snapshots");
        for (String ownerKey : snapshots.getAllKeys()) {
            UUID ownerId;
            try {
                ownerId = UUID.fromString(ownerKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            data.erasedBySnapshotOwner.put(
                    ownerId,
                    readUuids(snapshots.getList(ownerKey, Tag.TAG_INT_ARRAY))
            );
        }
        data.allErased.addAll(
                readUuids(tag.getList("present", Tag.TAG_INT_ARRAY))
        );
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        CompoundTag snapshots = new CompoundTag();
        erasedBySnapshotOwner.forEach((ownerId, erased) ->
                snapshots.put(ownerId.toString(), writeUuids(erased)));
        tag.put("snapshots", snapshots);
        tag.put("present", writeUuids(allErased));
        return tag;
    }

    private static Set<UUID> readUuids(ListTag list) {
        Set<UUID> uuids = new HashSet<>();
        for (Tag entry : list) {
            uuids.add(NbtUtils.loadUUID(entry));
        }
        return uuids;
    }

    private static ListTag writeUuids(Set<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            list.add(NbtUtils.createUUID(uuid));
        }
        return list;
    }

    public void erase(UUID snapshotOwnerId, UUID entityId) {
        if (erasedBySnapshotOwner
                .computeIfAbsent(snapshotOwnerId, ignored -> new HashSet<>())
                .add(entityId)) {
            allErased.add(entityId);
            setDirty();
        }
    }

    public boolean isErased(UUID snapshotOwnerId, UUID entityId) {
        Set<UUID> erased = erasedBySnapshotOwner.get(snapshotOwnerId);
        return erased != null && erased.contains(entityId);
    }

    public boolean isErasedAnywhere(UUID entityId) {
        return allErased.contains(entityId);
    }

    /** A replacement snapshot is a new Past; its old erasures no longer apply to it. */
    public void clearSnapshot(UUID snapshotOwnerId) {
        // The erased entities stay gone from the Present, so allErased keeps
        // blocking them there even after this snapshot is replaced.
        if (erasedBySnapshotOwner.remove(snapshotOwnerId) != null) {
            setDirty();
        }
    }
}
