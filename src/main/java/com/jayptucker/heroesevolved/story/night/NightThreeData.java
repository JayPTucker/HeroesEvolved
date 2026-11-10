package com.jayptucker.heroesevolved.story.night;

import com.jayptucker.heroesevolved.expansion.FacilityData.ReturnPoint;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What Night Three remembers between runs: each runner's way home (saved before crossing), each
 * player's best night, whether the bunker in the past has been built, and the one change that reaches
 * the whole world: Pod 14 stayed shut. Once it has, it stays that way; a worse night never undoes it.
 */
public final class NightThreeData extends SavedData {
    public static final String NAME = "heroesevolved_night_three";
    /** Bumped when the past bunker's layout changes, so it is built again. */
    public static final int LAYOUT = 1;

    /** One player's record of the third night. */
    public record Best(boolean kept, int runs) {
        static final Best NONE = new Best(false, 0);
    }

    public final Map<UUID, ReturnPoint> tickets = new HashMap<>();
    private final Map<UUID, Best> best = new HashMap<>();
    private boolean podKept;
    int layout;

    public static NightThreeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(NightThreeData::new, NightThreeData::load), NAME);
    }

    public Best best(UUID player) { return best.getOrDefault(player, Best.NONE); }
    /** Someone kept Gray from Pod 14: he never took Claire's regeneration, in this world. */
    public boolean podKept() { return podKept; }

    /** Records one finished night for one player; returns their best before it. */
    public Best record(UUID player, boolean kept) {
        Best before = best(player);
        best.put(player, new Best(before.kept() || kept, before.runs() + 1));
        if (kept) podKept = true;
        setDirty();
        return before;
    }

    /** For the game tests: forget every night, so one test's change to the world can't leak into another. */
    public void forgetAll() {
        best.clear();
        podKept = false;
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        tickets.forEach((id, p) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id); t.putString("dimension", p.dimension().toString());
            t.putDouble("x", p.x()); t.putDouble("y", p.y()); t.putDouble("z", p.z());
            t.putFloat("yaw", p.yaw()); t.putFloat("pitch", p.pitch());
            list.add(t);
        });
        tag.put("tickets", list);
        ListTag bests = new ListTag();
        best.forEach((id, b) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id);
            t.putBoolean("kept", b.kept()); t.putInt("runs", b.runs());
            bests.add(t);
        });
        tag.put("best", bests);
        tag.putBoolean("pod_kept", podKept);
        tag.putInt("layout", layout);
        return tag;
    }

    public static NightThreeData load(CompoundTag tag, HolderLookup.Provider registries) {
        NightThreeData data = new NightThreeData();
        for (Tag value : tag.getList("tickets", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) value;
            ResourceLocation dimension = ResourceLocation.tryParse(t.getString("dimension"));
            if (dimension != null && t.hasUUID("player") && Double.isFinite(t.getDouble("x"))
                    && Double.isFinite(t.getDouble("y")) && Double.isFinite(t.getDouble("z")))
                data.tickets.put(t.getUUID("player"), new ReturnPoint(dimension, t.getDouble("x"), t.getDouble("y"),
                        t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch")));
        }
        for (Tag value : tag.getList("best", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) value;
            if (t.hasUUID("player")) data.best.put(t.getUUID("player"), new Best(t.getBoolean("kept"), Math.max(0, t.getInt("runs"))));
        }
        data.podKept = tag.getBoolean("pod_kept");
        data.layout = tag.getInt("layout");
        return data;
    }
}
