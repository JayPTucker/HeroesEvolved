package com.jayptucker.heroesevolved.story.elle;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When the Company comes for each player: the day of omens before Elle arrives, and how earlier visits went. Survives
 * logouts and restarts.
 */
public final class CompanyVisitData extends SavedData {
    public static final String NAME = "heroesevolved_company_visits";

    public static final class Visit {
        /** When the next day of omens begins (0: not scheduled yet). */
        public long next;
        /** The day of omens running now: when it began and when she arrives (0 when none is running). */
        public long omensAt, arriveAt;
        /** Fights so far, and how many of them she lost. */
        public int visits, beaten;
        /** She has already spoken on the radio this time. */
        public boolean taunted;
        /** Omen kinds already narrated this time (a bit each), so each is remarked on once. */
        public int noticed;

        public boolean omens(long now) { return omensAt > 0 && now >= omensAt && now < arriveAt; }
        /** How far through the day of omens (0 to 1). */
        public float progress(long now) { return arriveAt <= omensAt ? 1 : Math.clamp((now - omensAt) / (float) (arriveAt - omensAt), 0, 1); }
    }

    private final Map<UUID, Visit> visits = new HashMap<>();

    public static CompanyVisitData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CompanyVisitData::new, CompanyVisitData::load), NAME);
    }

    public Visit visit(UUID player) { return visits.computeIfAbsent(player, ignored -> new Visit()); }

    public static CompanyVisitData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new CompanyVisitData();
        for (Tag element : tag.getList("visits", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            if (!entry.hasUUID("player")) continue;
            Visit v = data.visit(entry.getUUID("player"));
            v.next = Math.max(0, entry.getLong("next"));
            v.omensAt = Math.max(0, entry.getLong("omens"));
            v.arriveAt = Math.max(0, entry.getLong("arrive"));
            v.visits = Math.max(0, entry.getInt("count"));
            v.beaten = Math.max(0, entry.getInt("beaten"));
            v.taunted = entry.getBoolean("taunted");
            v.noticed = entry.getInt("noticed");
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        visits.forEach((id, v) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", id);
            entry.putLong("next", v.next); entry.putLong("omens", v.omensAt); entry.putLong("arrive", v.arriveAt);
            entry.putInt("count", v.visits); entry.putInt("beaten", v.beaten);
            entry.putBoolean("taunted", v.taunted); entry.putInt("noticed", v.noticed);
            list.add(entry);
        });
        tag.put("visits", list);
        return tag;
    }
}
