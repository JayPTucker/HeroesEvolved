package com.jayptucker.heroesevolved.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Receiver coordinates and the permanent world outcome. The structure itself stores its control states. */
final class LastSignalData extends SavedData {
    final Map<UUID, BlockPos> leads = new HashMap<>();
    boolean rescued;
    int infirmary;

    static LastSignalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(LastSignalData::new, LastSignalData::load), "heroesevolved_last_signal");
    }

    private static LastSignalData load(CompoundTag tag, HolderLookup.Provider registries) {
        LastSignalData data = new LastSignalData();
        data.rescued = tag.getBoolean("rescued");
        data.infirmary = tag.getInt("infirmary") & 3;
        for (Tag value : tag.getList("leads", Tag.TAG_COMPOUND)) {
            CompoundTag lead = (CompoundTag) value;
            if (lead.hasUUID("player") && lead.contains("location"))
                data.leads.put(lead.getUUID("player"), BlockPos.of(lead.getLong("location")));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("rescued", rescued);
        tag.putInt("infirmary", infirmary);
        ListTag locations = new ListTag();
        leads.forEach((player, pos) -> {
            CompoundTag lead = new CompoundTag();
            lead.putUUID("player", player);
            lead.putLong("location", pos.asLong());
            locations.add(lead);
        });
        tag.put("leads", locations);
        return tag;
    }
}
