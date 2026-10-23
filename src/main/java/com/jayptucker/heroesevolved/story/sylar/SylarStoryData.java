package com.jayptucker.heroesevolved.story.sylar;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** The one hunter's knowledge and each player's chapter survive logout and server restarts. */
public final class SylarStoryData extends SavedData {
    public static final String NAME = "heroesevolved_sylar";
    public static final class Trail {
        public int chapter, attention;
        public long nextEncounter;
    }
    private final Map<UUID, Trail> trails = new HashMap<>();
    private final Set<ResourceLocation> learned = new LinkedHashSet<>();
    private long quietUntil;
    private int defeats;
    public static SylarStoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(SylarStoryData::new, SylarStoryData::load), NAME);
    }
    public Trail trail(UUID player) { return trails.computeIfAbsent(player, ignored -> new Trail()); }
    public Set<ResourceLocation> learned() { return Collections.unmodifiableSet(learned); }
    public boolean knows(ResourceLocation power) { return learned.contains(power); }
    public boolean learn(ResourceLocation power) {
        if (!learned.add(power)) return false;
        setDirty(); return true;
    }
    public long quietUntil() { return quietUntil; }
    public int defeats() { return defeats; }
    public void defeated(long now) { defeats++; quietUntil = now + 20L * 60 * 30; setDirty(); }
    public static SylarStoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new SylarStoryData();
        data.quietUntil = Math.max(0, tag.getLong("quiet_until")); data.defeats = Math.max(0, tag.getInt("defeats"));
        for (Tag element : tag.getList("trails", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            if (!entry.hasUUID("player")) continue;
            Trail trail = data.trail(entry.getUUID("player"));
            trail.chapter = Math.clamp(entry.getInt("chapter"), 0, 3);
            trail.attention = Math.clamp(entry.getInt("attention"), 0, 12);
            trail.nextEncounter = Math.max(0, entry.getLong("next"));
        }
        for (Tag element : tag.getList("learned", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null) data.learned.add(id);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        trails.forEach((id, trail) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("player", id);
            entry.putInt("chapter", trail.chapter); entry.putInt("attention", trail.attention);
            entry.putLong("next", trail.nextEncounter); list.add(entry);
        });
        tag.put("trails", list);
        ListTag powers = new ListTag(); learned.forEach(id -> powers.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
        tag.put("learned", powers); tag.putLong("quiet_until", quietUntil); tag.putInt("defeats", defeats);
        return tag;
    }
}
