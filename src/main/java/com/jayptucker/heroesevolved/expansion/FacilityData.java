package com.jayptucker.heroesevolved.expansion;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** One shared incident, durable return tickets, and idempotent discoveries/rewards. */
public final class FacilityData extends SavedData {
    public enum Phase { PLANNED, BUILDING, READY, ACTIVE, SAVED, SCARRED }
    public record ReturnPoint(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {}
    public Phase phase = Phase.PLANNED;
    public int run = 1, cursor, elapsed, rescued, casualties, manual, mapId = -1;
    public boolean gate, stable, spawned;
    public UUID first, second;
    public final Map<UUID, ReturnPoint> visitors = new HashMap<>();
    private final Map<UUID, Set<String>> discoveries = new HashMap<>();
    public static FacilityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(FacilityData::new, FacilityData::load), "heroesevolved_unwritten_annex");
    }
    public boolean discover(UUID player, String name) {
        var found = discoveries.computeIfAbsent(player, id -> new HashSet<>());
        if (found.size() >= 64 || !found.add(name)) return false;
        setDirty(); return true;
    }
    public boolean discovered(UUID player, String name) { return discoveries.getOrDefault(player, Set.of()).contains(name); }
    public void reset() {
        run++; cursor = elapsed = rescued = casualties = manual = 0;
        gate = stable = spawned = false; first = second = null; phase = Phase.BUILDING; setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("phase", phase.name()); tag.putInt("run", run); tag.putInt("cursor", cursor); tag.putInt("elapsed", elapsed);
        tag.putInt("rescued", rescued); tag.putInt("casualties", casualties); tag.putInt("manual", manual); tag.putInt("map", mapId);
        tag.putBoolean("gate", gate); tag.putBoolean("stable", stable); tag.putBoolean("spawned", spawned);
        if (first != null) tag.putUUID("first", first); if (second != null) tag.putUUID("second", second);
        ListTag tickets = new ListTag(); visitors.forEach((id, p) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id); t.putString("dimension", p.dimension.toString());
            t.putDouble("x", p.x); t.putDouble("y", p.y); t.putDouble("z", p.z); t.putFloat("yaw", p.yaw); t.putFloat("pitch", p.pitch); tickets.add(t);
        }); tag.put("visitors", tickets);
        ListTag evidence = new ListTag(); discoveries.forEach((id, names) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id); ListTag list = new ListTag();
            names.stream().sorted().forEach(s -> list.add(StringTag.valueOf(s))); t.put("names", list); evidence.add(t);
        }); tag.put("discoveries", evidence); return tag;
    }
    public static FacilityData load(CompoundTag tag, HolderLookup.Provider registries) {
        var d = new FacilityData();
        try { d.phase = Phase.valueOf(tag.getString("phase")); } catch (IllegalArgumentException ignored) {}
        d.run = Math.max(1, tag.getInt("run")); d.cursor = Math.clamp(tag.getInt("cursor"), 0, FacilityService.BUILD_VOLUME);
        d.elapsed = Math.clamp(tag.getInt("elapsed"), 0, 2400); d.rescued = tag.getInt("rescued") & 3; d.casualties = tag.getInt("casualties") & 3;
        d.manual = Math.clamp(tag.getInt("manual"), 0, 2); d.mapId = tag.contains("map") ? tag.getInt("map") : -1;
        d.gate = tag.getBoolean("gate"); d.stable = tag.getBoolean("stable"); d.spawned = tag.getBoolean("spawned");
        if (tag.hasUUID("first")) d.first = tag.getUUID("first"); if (tag.hasUUID("second")) d.second = tag.getUUID("second");
        for (Tag value : tag.getList("visitors", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) value; var dimension = ResourceLocation.tryParse(t.getString("dimension"));
            if (dimension != null && t.hasUUID("player") && Double.isFinite(t.getDouble("x")) && Double.isFinite(t.getDouble("y")) && Double.isFinite(t.getDouble("z")))
                d.visitors.put(t.getUUID("player"), new ReturnPoint(dimension, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch")));
        }
        for (Tag value : tag.getList("discoveries", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) value; if (!t.hasUUID("player")) continue;
            for (Tag name : t.getList("names", Tag.TAG_STRING)) d.discover(t.getUUID("player"), name.getAsString());
        }
        return d;
    }
}
