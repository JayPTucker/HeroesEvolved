package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.story.AvenueData;
import com.jayptucker.heroesevolved.story.night.NightThreeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * What Sylar's story remembers, through restarts: how far the prisoner and the list have got ({@link #stage}, the list's
 * {@link Entry entries}, the hatch into Level 5), each player's visits to his cell, who has beaten him (and how often),
 * where each fighter came from (so a restart mid-fight sends them back there) and what anyone dropped in the Collection
 * (handed back when they respawn: he keeps nothing of yours).
 */
public final class SylarData extends SavedData {
    public static final String NAME = "heroesevolved_sylar_collection";

    public record Home(ResourceKey<Level> dimension, Vec3 at, float yaw, float pitch) {}

    /** The story's stages: asleep below, awake (the list is being crossed out), out of his cell, beaten. */
    public static final int DORMANT = 0, AWAKE = 1, OUT = 2, BEATEN = 3;

    /**
     * A living name on the list: who (a name and a power), where (the village's middle; null if the world had no village
     * for it), the villager given that name once someone came near ({@code villager}), and whether the name is crossed out
     * and the villager emptied.
     */
    public static final class Entry {
        public final String name;
        public final ResourceLocation power;
        public final BlockPos village;
        public UUID villager;
        public boolean crossed, emptied;
        /** The night it was crossed out, or -1. */
        public long night = -1;
        /** Who has met the emptied villager, and whom he has told about this name. */
        public final Set<UUID> heard = new HashSet<>(), told = new HashSet<>();

        public Entry(String name, ResourceLocation power, BlockPos village) { this.name = name; this.power = power; this.village = village; }
    }

    private final Map<UUID, Integer> wins = new HashMap<>();
    private final Map<UUID, Home> homes = new HashMap<>();
    private final Map<UUID, List<CompoundTag>> stash = new HashMap<>();
    private int defeats;
    private int stage;
    private final List<Entry> list = new ArrayList<>();
    private final Map<UUID, String> listed = new LinkedHashMap<>();
    private final Map<UUID, Integer> visits = new HashMap<>();
    private final Map<UUID, Integer> heardCrossed = new HashMap<>();
    private BlockPos hatch;
    private long lastCrossDay = -1, awokeDay = -1;
    private int levelFiveBuilt;

    public static SylarData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(SylarData::new, SylarData::load), NAME);
    }

    /**
     * Whether his case for {@code power} is full: Claire Bennet's Regeneration unless a player kept Pod 14 shut on Night
     * Three, Ted Sprague's light unless a player kept him from it on the avenue at 6:14. Every other case is always full.
     * One question, one place: the past's own records answer it.
     */
    public static boolean holds(MinecraftServer server, ResourceLocation power) {
        if (ModAbilities.REGENERATION_ID.equals(power)) return !NightThreeData.get(server).podKept();
        if (ModAbilities.RADIATION_ID.equals(power)) return !AvenueData.get(server).grayNeverSaw();
        return true;
    }

    public int stage() { return stage; }
    public void stage(int stage) { this.stage = stage; setDirty(); }
    public List<Entry> list() { return list; }
    public void add(Entry entry) { list.add(entry); setDirty(); }
    /** The players whose names are at the bottom of the list, in the order they were written, with the name written. */
    public Map<UUID, String> listed() { return Collections.unmodifiableMap(listed); }
    public boolean list(UUID player, String name) { boolean added = listed.putIfAbsent(player, name) == null; if (added) setDirty(); return added; }
    public int visits(UUID player) { return visits.getOrDefault(player, 0); }
    public void visited(UUID player) { visits.merge(player, 1, Integer::sum); setDirty(); }
    /** How many crossed-out names this player has heard him talk about. */
    public int heardCrossed(UUID player) { return heardCrossed.getOrDefault(player, 0); }
    public void heardCrossed(UUID player, int count) { heardCrossed.put(player, count); setDirty(); }
    public BlockPos hatch() { return hatch; }
    public void hatch(BlockPos pos) { hatch = pos; setDirty(); }
    /** The last night (see {@link SylarStory#nightOf}) the story has dealt with: a crossing, or his coming for you. */
    public long lastNight() { return lastCrossDay; }
    public void lastNight(long night) { lastCrossDay = night; setDirty(); }
    /** The night he woke. */
    public long awokeNight() { return awokeDay; }
    public void awokeNight(long night) { awokeDay = night; setDirty(); }
    public int levelFiveBuilt() { return levelFiveBuilt; }
    public void levelFiveBuilt(int version) { levelFiveBuilt = version; setDirty(); }
    public long crossedCount() { return list.stream().filter(e -> e.crossed).count(); }
    /** The story back to before he woke (admin and tests); wins, homes and stashes are kept. */
    public void resetStory() {
        stage = DORMANT; list.clear(); listed.clear(); visits.clear(); heardCrossed.clear();
        hatch = null; lastCrossDay = -1; awokeDay = -1;
        setDirty();
    }
    public void changed() { setDirty(); }

    public int wins(UUID player) { return wins.getOrDefault(player, 0); }
    public void won(UUID player) { wins.merge(player, 1, Integer::sum); setDirty(); }
    public int defeats() { return defeats; }
    public void defeated() { defeats++; setDirty(); }

    public Home home(UUID player) { return homes.get(player); }
    public void home(UUID player, Home home) { homes.put(player, home); setDirty(); }
    public void clearHome(UUID player) { if (homes.remove(player) != null) setDirty(); }

    /** Keeps what a fighter dropped in the Collection until they respawn. */
    public void stash(UUID player, ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return;
        stash.computeIfAbsent(player, k -> new ArrayList<>()).add((CompoundTag) stack.save(registries));
        setDirty();
    }

    /** Takes back everything kept for {@code player}. */
    public List<ItemStack> unstash(UUID player, HolderLookup.Provider registries) {
        List<CompoundTag> tags = stash.remove(player);
        if (tags == null) return List.of();
        setDirty();
        List<ItemStack> out = new ArrayList<>();
        for (CompoundTag tag : tags) ItemStack.parse(registries, tag).ifPresent(out::add);
        return out;
    }

    public boolean hasStash(UUID player) { return stash.containsKey(player); }

    public static SylarData load(CompoundTag tag, HolderLookup.Provider registries) {
        SylarData data = new SylarData();
        data.defeats = Math.max(0, tag.getInt("defeats"));
        data.stage = Math.clamp(tag.getInt("stage"), DORMANT, BEATEN);
        data.lastCrossDay = tag.contains("last_cross_day") ? tag.getLong("last_cross_day") : -1;
        data.awokeDay = tag.contains("awoke_day") ? tag.getLong("awoke_day") : -1;
        data.levelFiveBuilt = tag.getInt("level_five_built");
        if (tag.contains("hatch")) data.hatch = BlockPos.of(tag.getLong("hatch"));
        for (Tag t : tag.getList("list", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation power = ResourceLocation.tryParse(e.getString("power"));
            if (power == null) continue;
            Entry entry = new Entry(e.getString("name"), power, e.contains("village") ? BlockPos.of(e.getLong("village")) : null);
            if (e.hasUUID("villager")) entry.villager = e.getUUID("villager");
            entry.crossed = e.getBoolean("crossed");
            entry.emptied = e.getBoolean("emptied");
            entry.night = e.contains("night") ? e.getLong("night") : -1;
            for (Tag h : e.getList("heard", Tag.TAG_COMPOUND)) if (((CompoundTag) h).hasUUID("p")) entry.heard.add(((CompoundTag) h).getUUID("p"));
            for (Tag h : e.getList("told", Tag.TAG_COMPOUND)) if (((CompoundTag) h).hasUUID("p")) entry.told.add(((CompoundTag) h).getUUID("p"));
            data.list.add(entry);
        }
        for (Tag t : tag.getList("listed", Tag.TAG_COMPOUND)) if (((CompoundTag) t).hasUUID("p")) data.listed.put(((CompoundTag) t).getUUID("p"), ((CompoundTag) t).getString("name"));
        for (Tag t : tag.getList("visits", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            if (!e.hasUUID("p")) continue;
            data.visits.put(e.getUUID("p"), e.getInt("n"));
            data.heardCrossed.put(e.getUUID("p"), e.getInt("crossed"));
        }
        for (Tag t : tag.getList("wins", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            if (e.hasUUID("player")) data.wins.put(e.getUUID("player"), Math.max(0, e.getInt("wins")));
        }
        for (Tag t : tag.getList("homes", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("dimension"));
            if (!e.hasUUID("player") || dim == null) continue;
            data.homes.put(e.getUUID("player"), new Home(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim),
                    new Vec3(e.getDouble("x"), e.getDouble("y"), e.getDouble("z")), e.getFloat("yaw"), e.getFloat("pitch")));
        }
        for (Tag t : tag.getList("stash", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            if (!e.hasUUID("player")) continue;
            List<CompoundTag> items = new ArrayList<>();
            for (Tag item : e.getList("items", Tag.TAG_COMPOUND)) items.add((CompoundTag) item);
            if (!items.isEmpty()) data.stash.put(e.getUUID("player"), items);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("defeats", defeats);
        tag.putInt("stage", stage);
        tag.putLong("last_cross_day", lastCrossDay);
        tag.putLong("awoke_day", awokeDay);
        tag.putInt("level_five_built", levelFiveBuilt);
        if (hatch != null) tag.putLong("hatch", hatch.asLong());
        ListTag entries = new ListTag();
        for (Entry entry : list) {
            CompoundTag e = new CompoundTag();
            e.putString("name", entry.name);
            e.putString("power", entry.power.toString());
            if (entry.village != null) e.putLong("village", entry.village.asLong());
            if (entry.villager != null) e.putUUID("villager", entry.villager);
            e.putBoolean("crossed", entry.crossed);
            e.putBoolean("emptied", entry.emptied);
            ListTag heard = new ListTag();
            for (UUID id : entry.heard) { CompoundTag h = new CompoundTag(); h.putUUID("p", id); heard.add(h); }
            e.put("heard", heard);
            ListTag told = new ListTag();
            for (UUID id : entry.told) { CompoundTag h = new CompoundTag(); h.putUUID("p", id); told.add(h); }
            e.put("told", told);
            e.putLong("night", entry.night);
            entries.add(e);
        }
        tag.put("list", entries);
        ListTag listedTag = new ListTag();
        listed.forEach((id, name) -> { CompoundTag e = new CompoundTag(); e.putUUID("p", id); e.putString("name", name); listedTag.add(e); });
        tag.put("listed", listedTag);
        ListTag visitTag = new ListTag();
        Set<UUID> people = new HashSet<>(visits.keySet());
        people.addAll(heardCrossed.keySet());
        for (UUID id : people) {
            CompoundTag e = new CompoundTag();
            e.putUUID("p", id); e.putInt("n", visits(id)); e.putInt("crossed", heardCrossed(id));
            visitTag.add(e);
        }
        tag.put("visits", visitTag);
        ListTag winList = new ListTag();
        wins.forEach((id, n) -> { CompoundTag e = new CompoundTag(); e.putUUID("player", id); e.putInt("wins", n); winList.add(e); });
        tag.put("wins", winList);
        ListTag homeList = new ListTag();
        homes.forEach((id, h) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("player", id); e.putString("dimension", h.dimension().location().toString());
            e.putDouble("x", h.at().x); e.putDouble("y", h.at().y); e.putDouble("z", h.at().z);
            e.putFloat("yaw", h.yaw()); e.putFloat("pitch", h.pitch());
            homeList.add(e);
        });
        tag.put("homes", homeList);
        ListTag stashList = new ListTag();
        stash.forEach((id, items) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("player", id);
            ListTag l = new ListTag(); l.addAll(items);
            e.put("items", l);
            stashList.add(e);
        });
        tag.put("stash", stashList);
        return tag;
    }
}
