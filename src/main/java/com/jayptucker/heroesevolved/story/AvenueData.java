package com.jayptucker.heroesevolved.story;

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
 * What the avenue remembers between runs: each runner's way home (saved before crossing, so a crash
 * can't strand anyone in 6:14), each player's best evening, and the one change that reaches the
 * whole world: Gray never saw the light. What you save stays saved; a worse run never undoes it.
 */
public final class AvenueData extends SavedData {
    public static final String NAME = "heroesevolved_avenue";

    /** One player's best evening on the avenue. */
    public record Best(int saved, boolean grayKept, int runs) {
        static final Best NONE = new Best(0, false, 0);
    }

    public final Map<UUID, ReturnPoint> tickets = new HashMap<>();
    private final Map<UUID, Best> best = new HashMap<>();
    private boolean grayNeverSaw;
    private int worldBest;

    public static AvenueData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(AvenueData::new, AvenueData::load), NAME);
    }

    public Best best(UUID player) { return best.getOrDefault(player, Best.NONE); }
    /** Once anyone keeps Gray from the light, he never understood Sprague's heat, in this world. */
    public boolean grayNeverSaw() { return grayNeverSaw; }
    public int worldBest() { return worldBest; }

    /** Records one finished run for one player; returns their best before it. */
    public Best record(UUID player, int saved, boolean grayKept) {
        Best before = best(player);
        best.put(player, new Best(Math.max(before.saved(), saved), before.grayKept() || grayKept, before.runs() + 1));
        if (grayKept) grayNeverSaw = true;
        worldBest = Math.max(worldBest, saved);
        setDirty();
        return before;
    }

    /** For the game tests: forget every evening, so one test's change to the world can't leak into another. */
    void forgetAll() {
        best.clear();
        grayNeverSaw = false;
        worldBest = 0;
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
            t.putInt("saved", b.saved()); t.putBoolean("gray", b.grayKept()); t.putInt("runs", b.runs());
            bests.add(t);
        });
        tag.put("best", bests);
        tag.putBoolean("gray_never_saw", grayNeverSaw);
        tag.putInt("world_best", worldBest);
        return tag;
    }

    public static AvenueData load(CompoundTag tag, HolderLookup.Provider registries) {
        AvenueData data = new AvenueData();
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
            if (t.hasUUID("player")) data.best.put(t.getUUID("player"), new Best(
                    Math.clamp(t.getInt("saved"), 0, AvenueService.CITIZENS), t.getBoolean("gray"), Math.max(0, t.getInt("runs"))));
        }
        data.grayNeverSaw = tag.getBoolean("gray_never_saw");
        data.worldBest = Math.clamp(tag.getInt("world_best"), 0, AvenueService.CITIZENS);
        return data;
    }
}
