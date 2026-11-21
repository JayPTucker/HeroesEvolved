package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ground that heals over the days after something hurt it, kept per level: a meteor's crater (its magma cools, its
 * scorched floor turns back to soil, grass and then wildflowers grow in the bowl, its rubble rim mosses over; the
 * meteorite stays), a wildfire's burn (it blooms, thicker with flowers than the land round it), a supercell's track (the
 * grass and flowers it tore up come back). Healing goes on only while the place is loaded, and catches up when it is: each
 * pass works out how many days have gone by and does that much.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Scars extends SavedData {
    static final String NAME = HeroesEvolved.MOD_ID + "_scars";
    public enum Kind { CRATER, BURN, TRACK }
    /** How many days each kind takes to heal. */
    static final int CRATER_DAYS = 5, BURN_DAYS = 4, TRACK_DAYS = 3;
    /** Checked this often; this many spots of each loaded scar are looked at each time. */
    static final int EVERY = 100, SPOTS = 48;
    static final long DAY = 24_000;

    /** One scar: where, how big, when it was made (on the calendar, village/Days), and, for a track, the points it ran through. */
    public static final class Scar {
        final Kind kind;
        final BlockPos center;
        final int radius;
        long made;
        final List<BlockPos> points = new ArrayList<>();
        Scar(Kind kind, BlockPos center, int radius, long made) { this.kind = kind; this.center = center; this.radius = radius; this.made = made; }
        public Kind kind() { return kind; }
        public BlockPos center() { return center; }
        public int radius() { return radius; }
        public List<BlockPos> points() { return List.copyOf(points); }
        /** How many days have gone by since it was made. */
        public float days(long now) { return Math.max(0, now - made) / (float) DAY; }
        int healed() { return kind == Kind.CRATER ? CRATER_DAYS : kind == Kind.BURN ? BURN_DAYS : TRACK_DAYS; }
    }

    private final List<Scar> scars = new ArrayList<>();
    /** Tracks still being made, by who is making them (an overload's id). */
    private static final Map<String, Scar> MAKING = new HashMap<>();

    public static Scars get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(Scars::new, Scars::load), NAME);
    }

    public static List<Scar> all(ServerLevel level) { return List.copyOf(get(level).scars); }

    /** A crater {@code radius} across round {@code center}, healing from today. */
    public static Scar crater(ServerLevel level, BlockPos center, int radius) { return add(level, Kind.CRATER, center, radius); }

    /** A burn {@code radius} across round {@code center}. */
    public static Scar burn(ServerLevel level, BlockPos center, int radius) { return add(level, Kind.BURN, center, radius); }

    private static Scar add(ServerLevel level, Kind kind, BlockPos center, int radius) {
        Scar s = new Scar(kind, center.immutable(), radius, Days.clock(level));
        Scars data = get(level);
        data.scars.add(s);
        data.setDirty();
        return s;
    }

    /** One more point on the track {@code key} is making (a supercell's funnel), {@code radius} across. */
    public static void track(ServerLevel level, String key, BlockPos at, int radius) {
        Scar s = MAKING.get(key);
        if (s == null || !get(level).scars.contains(s)) {
            s = add(level, Kind.TRACK, at, radius);
            MAKING.put(key, s);
        }
        if (s.points.isEmpty() || s.points.getLast().distSqr(at) >= 16) {
            s.points.add(at.immutable());
            get(level).setDirty();
        }
    }

    /** The track {@code key} is finished: it heals from now. */
    public static void endTrack(String key) { MAKING.remove(key); }

    /** Forgets {@code s} (it heals no further; tests). */
    public static void forget(ServerLevel level, Scar s) {
        get(level).scars.remove(s);
        get(level).setDirty();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % EVERY != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            Scars data = level.getDataStorage().get(new Factory<>(Scars::new, Scars::load), NAME);
            if (data == null || data.scars.isEmpty()) continue;
            // Days on the calendar, slept nights included (village/Days).
            long now = Days.clock(level);
            for (Scar s : List.copyOf(data.scars)) {
                if (!level.hasChunkAt(s.center)) continue;
                heal(level, s, now);
                if (s.days(now) >= s.healed() + 0.5F) { data.scars.remove(s); data.setDirty(); }
            }
        }
    }

    /** One pass of healing: {@link #SPOTS} spots of the scar, each brought on as far as the days allow. */
    static void heal(ServerLevel level, Scar s, long now) {
        float days = s.days(now);
        RandomSource random = level.random;
        for (int i = 0; i < SPOTS; i++) {
            BlockPos around = s.kind == Kind.TRACK && !s.points.isEmpty() ? s.points.get(random.nextInt(s.points.size())) : s.center;
            int r = s.radius;
            int x = around.getX() + random.nextInt(2 * r + 1) - r, z = around.getZ() + random.nextInt(2 * r + 1) - r;
            if ((x - around.getX()) * (x - around.getX()) + (z - around.getZ()) * (z - around.getZ()) > r * r) continue;
            BlockPos column = new BlockPos(x, around.getY(), z);
            if (!level.hasChunkAt(column)) continue;
            switch (s.kind) {
                case CRATER -> crater(level, s, column, days, random);
                case BURN -> bloom(level, column, days, random, 0.35F);
                case TRACK -> bloom(level, column, days, random, 0.18F);
            }
        }
    }

    /**
     * A crater heals: day one, its magma cools to blackstone; day two, its scorched floor (blackstone, basalt) turns to
     * coarse dirt where the sky reaches it; day three, coarse dirt turns to soil, which grass creeps over; from day four,
     * grass and wildflowers grow in the bowl, and its rubble rim mosses over. The meteorite is never touched.
     */
    static void crater(ServerLevel level, Scar s, BlockPos column, float days, RandomSource random) {
        // Look down the column from a little above the crater's mouth for its first solid block.
        BlockPos.MutableBlockPos p = column.above(4).mutable();
        BlockState state = null;
        for (int i = 0; i < s.radius + 10; i++, p.move(Direction.DOWN)) {
            state = level.getBlockState(p);
            if (!state.isAir() && state.getFluidState().isEmpty()) break;
        }
        if (state == null || state.isAir()) return;
        BlockPos at = p.immutable();
        boolean open = level.getBlockState(at.above()).isAir();
        BlockState to = null;
        if (state.is(Blocks.MAGMA_BLOCK) && days >= 1) to = Blocks.BLACKSTONE.defaultBlockState();
        else if ((state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT)) && days >= 2 && open) to = Blocks.COARSE_DIRT.defaultBlockState();
        else if (state.is(Blocks.COARSE_DIRT) && days >= 3 && open) to = Blocks.DIRT.defaultBlockState();
        else if (state.is(Blocks.DIRT) && days >= 3.5F && open && level.canSeeSky(at.above())) to = Blocks.GRASS_BLOCK.defaultBlockState();
        else if (state.is(Blocks.COBBLED_DEEPSLATE) && days >= 4) to = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        else if (state.is(Blocks.GRASS_BLOCK) && days >= 4 && open) { flower(level, at.above(), random, 0.45F); return; }
        if (to != null) level.setBlock(at, to, Block.UPDATE_ALL);
    }

    /** Burnt or torn-up ground blooms: grass and flowers come up on the open grass there, thicker than they were. */
    static void bloom(ServerLevel level, BlockPos column, float days, RandomSource random, float chance) {
        if (days < 1) return;
        // Looked for from just above the scar downward, never from the heightmap: under a tree (or a roof) its top is the
        // overhang, not the ground.
        BlockPos.MutableBlockPos p = column.above(4).mutable();
        for (int i = 0; i < 12; i++, p.move(Direction.DOWN)) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (s.is(Blocks.GRASS_BLOCK)) flower(level, p.above(), random, chance * Math.min(1, days / 2));
            return;
        }
    }

    /** Something growing at {@code at}, if it is empty: mostly grass, sometimes a flower. */
    private static void flower(ServerLevel level, BlockPos at, RandomSource random, float chance) {
        if (!level.getBlockState(at).isAir() || random.nextFloat() > chance) return;
        float roll = random.nextFloat();
        BlockState plant = roll < 0.55F ? Blocks.SHORT_GRASS.defaultBlockState() : roll < 0.68F ? Blocks.POPPY.defaultBlockState()
                : roll < 0.8F ? Blocks.CORNFLOWER.defaultBlockState() : roll < 0.9F ? Blocks.OXEYE_DAISY.defaultBlockState()
                : roll < 0.96F ? Blocks.DANDELION.defaultBlockState() : Blocks.FERN.defaultBlockState();
        if (plant.canSurvive(level, at)) level.setBlock(at, plant, Block.UPDATE_ALL);
    }

    /** Pretends the scar was made {@code days} earlier (tests, previews). */
    public static void age(ServerLevel level, Scar s, float days) {
        s.made -= (long) (days * DAY);
        get(level).setDirty();
    }

    /** Runs {@code passes} passes of healing on {@code s} now (tests, previews). */
    public static void heal(ServerLevel level, Scar s, int passes) {
        for (int i = 0; i < passes; i++) heal(level, s, Days.clock(level));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Scar s : scars) {
            CompoundTag t = new CompoundTag();
            t.putString("kind", s.kind.name());
            t.putLong("center", s.center.asLong());
            t.putInt("radius", s.radius);
            t.putLong("made", s.made);
            ListTag points = new ListTag();
            for (BlockPos p : s.points) points.add(LongTag.valueOf(p.asLong()));
            t.put("points", points);
            list.add(t);
        }
        tag.put("scars", list);
        return tag;
    }

    static Scars load(CompoundTag tag, HolderLookup.Provider registries) {
        Scars data = new Scars();
        for (Tag raw : tag.getList("scars", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Kind kind;
            try { kind = Kind.valueOf(t.getString("kind")); } catch (IllegalArgumentException e) { continue; }
            Scar s = new Scar(kind, BlockPos.of(t.getLong("center")), t.getInt("radius"), t.getLong("made"));
            for (Tag p : t.getList("points", Tag.TAG_LONG)) s.points.add(BlockPos.of(((LongTag) p).getAsLong()));
            data.scars.add(s);
        }
        return data;
    }
}
