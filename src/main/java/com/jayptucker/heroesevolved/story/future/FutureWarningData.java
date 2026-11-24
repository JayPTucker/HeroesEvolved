package com.jayptucker.heroesevolved.story.future;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The warning belongs to a person, even if they leave, die or the server restarts between encounters. */
public final class FutureWarningData extends SavedData {
    public record Due(long calendar, long game) {
        public boolean ready(long calendar, long game) { return calendar >= this.calendar || game >= this.game; }
    }
    private final Map<UUID, Due> pending = new HashMap<>();
    private final Set<UUID> heard = new HashSet<>();
    private long nextDraw = 48_000;

    public static FutureWarningData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(FutureWarningData::new, FutureWarningData::load), "heroesevolved_future_warning");
    }
    public Due due(UUID id) { return pending.get(id); }
    public boolean heard(UUID id) { return heard.contains(id); }
    public boolean available(UUID id) { return !heard(id) && due(id) == null; }
    public int pendingCount() { return pending.size(); }
    public long nextDraw() { return nextDraw; }
    public void nextDraw(long time) { nextDraw = time; setDirty(); }
    public void queue(UUID id, long calendar, long game) { pending.put(id, new Due(calendar, game)); setDirty(); }
    public void finish(UUID id) { pending.remove(id); heard.add(id); setDirty(); }
    public void reset(UUID id) { pending.remove(id); heard.remove(id); setDirty(); }
    public void cancelAll() { heard.addAll(pending.keySet()); pending.clear(); setDirty(); }

    public static FutureWarningData load(CompoundTag tag, HolderLookup.Provider registries) {
        FutureWarningData data = new FutureWarningData();
        if (tag.contains("next")) data.nextDraw = tag.getLong("next");
        ListTag entries = tag.getList("pending", 10);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("id")) data.pending.put(entry.getUUID("id"), new Due(entry.getLong("calendar"), entry.getLong("game")));
        }
        ListTag heard = tag.getList("heard", 10);
        for (int i = 0; i < heard.size(); i++) if (heard.getCompound(i).hasUUID("id")) data.heard.add(heard.getCompound(i).getUUID("id"));
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("next", nextDraw);
        ListTag entries = new ListTag(), done = new ListTag();
        pending.forEach((id, due) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("id", id);
            entry.putLong("calendar", due.calendar); entry.putLong("game", due.game); entries.add(entry);
        });
        for (UUID id : heard) { CompoundTag entry = new CompoundTag(); entry.putUUID("id", id); done.add(entry); }
        tag.put("pending", entries); tag.put("heard", done);
        return tag;
    }
}
