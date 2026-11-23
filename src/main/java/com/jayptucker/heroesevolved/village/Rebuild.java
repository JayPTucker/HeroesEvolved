package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A village putting back what a great meteor broke, kept per level. Before the crater is carved, what stood there is
 * noted ({@link #note}); if the village survives, from the next morning ({@link #begin}) its people walk to the site by
 * day and it goes back up from the ground over two days, block by block, lowest first. Only empty space is filled, so
 * anything built there meanwhile is kept. Block entities' contents (a chest's) aren't kept: what comes back is the build.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Rebuild extends SavedData {
    static final String NAME = HeroesEvolved.MOD_ID + "_rebuild";
    /** Rebuilding takes about two days of daylight; work runs from this time of day to that, checked this often. */
    static final int WORK_FROM = 1_000, WORK_TO = 12_000, EVERY = 20, DAYS = 2;

    /** One site: the village's bell, where it was hit, what stood there (lowest first), how far along, when it may start. */
    public static final class Site {
        final BlockPos bell, center;
        int radius;
        final List<BlockPos> positions = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        int next;
        long startAt = Long.MAX_VALUE;
        /** A new house the village is putting up (village/Growth), not damage it is mending; its lowest layer's height. */
        boolean growth;
        int base = Integer.MIN_VALUE;
        /** For a home being rebuilt bigger: how many of the first entries take the old house down (top first). */
        int clear;
        /** Putting back what was broken in one of the village's buildings (village/Repairs). */
        boolean mend;
        /** Paving the connection from a finished home to its village (village/Paths). */
        boolean paving;
        /** What a growth site builds, so the village can rebuild it bigger one day (village/Projects.Home), and the old
         * home it replaces, if any. */
        Projects.Home home, replaces;
        Site(BlockPos bell, BlockPos center) { this.bell = bell; this.center = center; }
        public boolean growth() { return growth; }
        public int clearing() { return clear; }
        public boolean mending() { return mend; }
        public boolean paving() { return paving; }
        public Projects.Home home() { return home; }
        public Projects.Home replaces() { return replaces; }
        public BlockPos bell() { return bell; }
        public BlockPos center() { return center; }
        public int size() { return positions.size(); }
        public int done() { return next; }
        public boolean started() { return startAt != Long.MAX_VALUE; }
        /** Blocks to put back each working pass, so it takes {@link #DAYS} days. */
        int perPass() { return Math.max(1, (int) Math.ceil(positions.size() / (double) (DAYS * (WORK_TO - WORK_FROM) / EVERY))); }
    }

    private final List<Site> sites = new ArrayList<>();

    public static Rebuild get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(Rebuild::new, Rebuild::load), NAME);
    }

    public static List<Site> all(ServerLevel level) { return List.copyOf(get(level).sites); }

    /**
     * Notes what stands within {@code radius} of {@code center} (everything that isn't air), before it is broken. The
     * site waits until {@link #begin} says the village survived.
     */
    public static Site note(ServerLevel level, BlockPos bell, BlockPos center, int radius) {
        Site s = new Site(bell.immutable(), center.immutable());
        s.radius = radius;
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (p.distSqr(center) > (double) radius * radius || !level.hasChunkAt(p)) continue;
            BlockState state = level.getBlockState(p);
            if (state.isAir() || state.getDestroySpeed(level, p) < 0) continue;
            found.add(p.immutable());
        }
        // Lowest first, and from the middle out, so it goes up from the ground as it was built.
        found.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> p.distSqr(center)));
        for (BlockPos p : found) { s.positions.add(p); s.states.add(level.getBlockState(p)); }
        Rebuild data = get(level);
        data.sites.add(s);
        data.setDirty();
        return s;
    }

    /**
     * A new house to put up at {@code center} (village/Growth): {@code blocks} in the order they go up, its lowest layer at
     * {@code base} (which may replace the soil it stands on). It starts the next morning ({@link #begin}).
     */
    public static Site plan(ServerLevel level, BlockPos bell, BlockPos center, int base, List<BlockPos> positions, List<BlockState> states) {
        Site s = new Site(bell.immutable(), center.immutable());
        s.growth = true;
        s.base = base;
        s.positions.addAll(positions);
        s.states.addAll(states);
        Rebuild data = get(level);
        data.sites.add(s);
        data.setDirty();
        return s;
    }

    /**
     * A home rebuilt bigger: {@code clear} (the old house, top first, each to the state given: air, or ground where its
     * floor was) comes down, then {@code positions} go up as {@link #plan} builds them.
     */
    public static Site replan(ServerLevel level, BlockPos bell, BlockPos center, int base, List<BlockPos> clear, List<BlockState> cleared,
                              List<BlockPos> positions, List<BlockState> states) {
        Site s = new Site(bell.immutable(), center.immutable());
        s.growth = true;
        s.base = base;
        s.clear = clear.size();
        s.positions.addAll(clear);
        s.states.addAll(cleared);
        s.positions.addAll(positions);
        s.states.addAll(states);
        Rebuild data = get(level);
        data.sites.add(s);
        data.setDirty();
        return s;
    }

    /**
     * Putting back what's missing from one of the village's buildings (village/Repairs): {@code positions} with their
     * {@code states}, lowest first, into empty space only. It starts now if it's working hours, else in the morning.
     */
    public static Site mend(ServerLevel level, BlockPos bell, BlockPos center, List<BlockPos> positions, List<BlockState> states) {
        Site s = new Site(bell.immutable(), center.immutable());
        s.mend = true;
        s.positions.addAll(positions);
        s.states.addAll(states);
        Rebuild data = get(level);
        data.sites.add(s);
        long time = level.getDayTime() % 24_000;
        if (time >= WORK_FROM && time < WORK_TO) s.startAt = Days.clock(level);
        else begin(level, s);
        data.setDirty();
        return s;
    }

    static Site path(ServerLevel level, BlockPos bell, Projects.Home home, List<BlockPos> positions, List<BlockState> states) {
        Site s = new Site(bell.immutable(), positions.getFirst());
        s.paving = true;
        s.home = home;
        s.positions.addAll(positions);
        s.states.addAll(states);
        get(level).sites.add(s);
        begin(level, s);
        long time = Math.floorMod(level.getDayTime(), 24_000);
        if (time >= WORK_FROM && time < WORK_TO) s.startAt = Days.clock(level);
        get(level).setDirty();
        return s;
    }

    /** The village survived: it starts rebuilding at the next morning. */
    public static void begin(ServerLevel level, Site s) {
        // The next morning on the calendar: a night slept through brings it straight on.
        long time = level.getDayTime() % 24_000;
        s.startAt = Days.clock(level) + Math.floorMod(WORK_FROM - time, 24_000);
        get(level).setDirty();
    }

    /** The village fell, or the site is no longer wanted: nothing is rebuilt. */
    public static void drop(ServerLevel level, Site s) {
        get(level).sites.remove(s);
        get(level).setDirty();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % EVERY != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            Rebuild data = level.getDataStorage().get(new Factory<>(Rebuild::new, Rebuild::load), NAME);
            if (data == null || data.sites.isEmpty()) continue;
            for (Site s : List.copyOf(data.sites)) work(level, data, s);
        }
    }

    /** One working pass on a site, in daylight once it may start: its people come to it, and a few blocks go back. */
    static void work(ServerLevel level, Rebuild data, Site s) {
        long now = level.getGameTime(), time = level.getDayTime() % 24_000;
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) return;
        if (!s.started() || Days.clock(level) < s.startAt || time < WORK_FROM || time > WORK_TO || !level.hasChunkAt(s.center)) return;
        if (s.next < s.size() && !level.hasChunkAt(s.positions.get(s.next))) return;
        // Nobody works through trouble, and with nobody left in the village nobody builds at all.
        if (com.jayptucker.heroesevolved.radio.EmergencyBand.troubled(level, s.bell) || !Workers.direct(level, s)) return;
        step(level, data, s, s.perPass());
    }

    /** Puts back up to {@code count} blocks, into empty space only. */
    static void step(ServerLevel level, Rebuild data, Site s, int count) {
        int placed = 0;
        while (s.next < s.positions.size() && placed < count) {
            BlockPos p = s.positions.get(s.next);
            BlockState state = s.states.get(s.next);
            if (!level.hasChunkAt(p)) break; // Keep pending work for when its chunk loads again.
            s.next++;
            BlockState now = level.getBlockState(p);
            BlockState was = now;
            if (s.paving) {
                if (!Paths.canPave(level, p, state)) continue;
                level.setBlock(p, state, Block.UPDATE_ALL);
                placed++;
                if (placed == 1) Workers.placed(level, s, p, state, was);
                continue;
            }
            if (s.next <= s.clear) {
                // Taking the old house down: no neighbour updates, so nothing pops off as items while it comes apart;
                // what was in its chests is left on the ground.
                if (now.isAir() && state.isAir() || now == state || now.getDestroySpeed(level, p) < 0 || PlayerBuilt.is(level, p)) continue;
                if (level.getBlockEntity(p) instanceof net.minecraft.world.Container box) net.minecraft.world.Containers.dropContents(level, p, box);
                level.removeBlockEntity(p);
                level.setBlock(p, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                placed++;
                if (placed == 1) Workers.placed(level, s, p, state, was);
                if (placed == 1 && level.random.nextInt(3) == 0)
                    level.playSound(null, p, now.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
                continue;
            }
            // Never over anything a player put down, even a flower or a carpet.
            if (PlayerBuilt.is(level, p)) continue;
            if (s.growth) {
                // A new house goes over grass and flowers, and its lowest layer over the soil; never over anything built.
                boolean soil = p.getY() == s.base && Growth.natural(now);
                if (!now.isAir() && !now.canBeReplaced() && !soil) continue;
                if (state.isAir() && now.isAir()) continue;
                // A template's air in its floor layer (yards, gaps round the walls) would dig trenches in the ground.
                if (state.isAir() && p.getY() <= s.base) continue;
            } else if (!now.isAir() && !(now.canBeReplaced() && !now.getFluidState().isEmpty()) && !scorched(now)) continue;
            level.setBlock(p, state, Block.UPDATE_ALL);
            // Panes, fences, walls and stairs join to what is already there; what comes later joins them in turn.
            if (s.growth && settles(state)) {
                BlockState joined = Block.updateFromNeighbourShapes(state, level, p);
                if (joined != state) level.setBlock(p, joined, Block.UPDATE_CLIENTS);
            }
            placed++;
            if (placed == 1) Workers.placed(level, s, p, state, was);
            if (placed == 1 && level.random.nextInt(3) == 0)
                level.playSound(null, p, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
        }
        data.setDirty();
        if (s.next >= s.positions.size()) finish(level, data, s);
    }

    /**
     * What the meteor's crater left (its scorched floor and rubble): the village puts its own ground back over it. The
     * meteorite itself is left where it lies, a glowing stone in the rebuilt ground.
     */
    static boolean scorched(BlockState s) {
        return s.is(net.minecraft.world.level.block.Blocks.BLACKSTONE) || s.is(net.minecraft.world.level.block.Blocks.BASALT)
                || s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || s.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
                || s.is(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE);
    }

    /** Done: the rubble thrown out round the crater's rim is cleared away too. */
    private static void clearRubble(ServerLevel level, Site s) {
        java.util.Set<BlockPos> built = new java.util.HashSet<>(s.positions);
        int r = s.radius + 7;
        // Twice, so rubble that landed on rubble goes too.
        for (int pass = 0; pass < 2; pass++)
        for (BlockPos p : BlockPos.betweenClosed(s.center.offset(-r, -r, -r), s.center.offset(r, r / 2, r))) {
            if (!level.hasChunkAt(p) || built.contains(p) || PlayerBuilt.is(level, p)) continue;
            BlockState state = level.getBlockState(p);
            if (scorched(state) && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir())
                level.setBlock(p.immutable(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The village's people go to the site to work while it is light. */
    private static void builders(ServerLevel level, Site s) {
        for (Villager v : Villages.members(level, s.bell)) {
            if (v.isSleeping() || v.isBaby()) continue;
            BlockPos near = s.center.offset(level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4);
            v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(near, 0.55F, 3));
        }
    }

    /** Blocks whose shape comes from their neighbours, and safe to re-shape alone (not doors, beds or tall plants). */
    public static boolean settles(BlockState s) {
        var b = s.getBlock();
        return b instanceof net.minecraft.world.level.block.CrossCollisionBlock || b instanceof net.minecraft.world.level.block.WallBlock
                || b instanceof net.minecraft.world.level.block.StairBlock || b instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    /** Vanilla's settling pass over a finished building: every pane, fence, wall and stair joins what is round it. */
    public static int settle(ServerLevel level, List<BlockPos> positions) {
        int changed = 0;
        for (BlockPos p : positions) {
            if (!level.hasChunkAt(p)) continue;
            BlockState now = level.getBlockState(p);
            if (!settles(now) || PlayerBuilt.is(level, p)) continue;
            BlockState joined = Block.updateFromNeighbourShapes(now, level, p);
            if (joined != now) { level.setBlock(p, joined, Block.UPDATE_CLIENTS); changed++; }
        }
        return changed;
    }

    private static void finish(ServerLevel level, Rebuild data, Site s) {
        settle(level, s.positions);
        Workers.done(level, s);
        if (s.paving) {
            data.sites.remove(s);
            data.setDirty();
            VillageMemory.Village v = VillageMemory.of(level, s.bell);
            boolean whole = true;
            for (int i = 0; i < s.size(); i++)
                if (!level.hasChunkAt(s.positions.get(i)) || !s.states.get(i).isAir()
                        && !level.getBlockState(s.positions.get(i)).is(s.states.get(i).getBlock())) whole = false;
            if (whole) Paths.connected(level, v, s.home);
            Projects.signs(level, v);
            return;
        }
        if (s.mend) {
            // Anything that hangs on something put back after it (a torch, a lantern) gets a second try.
            for (int i = 0; i < s.positions.size(); i++) {
                BlockPos p = s.positions.get(i);
                BlockState want = s.states.get(i);
                if (level.hasChunkAt(p) && level.getBlockState(p).isAir() && want.canSurvive(level, p) && !PlayerBuilt.cleared(level, p))
                    level.setBlock(p, want, Block.UPDATE_ALL);
            }
            data.sites.remove(s);
            data.setDirty();
            VillageMemory.Village v = VillageMemory.of(level, s.bell);
            level.playSound(null, s.center, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2F, 1);
            if (!Repairs.mending(level, v)) {
                for (ServerPlayer p : level.players())
                    if (p.blockPosition().distSqr(s.center) <= 96 * 96)
                        p.sendSystemMessage(Component.literal(v.name() + " has mended what was broken.").withStyle(ChatFormatting.GREEN));
                Projects.mended(level, v);
            }
            return;
        }
        if (s.growth) {
            data.sites.remove(s);
            data.setDirty();
            Growth.built(level, s);
            return;
        }
        clearRubble(level, s);
        data.sites.remove(s);
        data.setDirty();
        String name = VillageMemory.of(level, s.bell).name();
        level.playSound(null, s.center, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5F, 1);
        for (ServerPlayer p : level.players())
            if (p.blockPosition().distSqr(s.center) <= 128 * 128)
                p.sendSystemMessage(Component.literal(name + " has rebuilt what the meteor broke.").withStyle(ChatFormatting.GREEN));
    }

    /** Lets a site start now and runs {@code passes} working passes, whatever the time (tests, previews). */
    public static void hurry(ServerLevel level, Site s, int passes) {
        s.startAt = Days.clock(level);
        for (int i = 0; i < passes && get(level).sites.contains(s); i++) step(level, get(level), s, s.perPass());
    }

    /** Lets a site start now and puts it back as far as {@code fraction} of the way, whatever the time (previews). */
    public static void hurryTo(ServerLevel level, Site s, float fraction) {
        s.startAt = Days.clock(level);
        int target = Math.round(s.positions.size() * Math.max(0, Math.min(1, fraction)));
        if (target > s.next) step(level, get(level), s, Integer.MAX_VALUE == target ? target : target - s.next);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Site s : sites) {
            CompoundTag t = new CompoundTag();
            t.putLong("bell", s.bell.asLong());
            t.putLong("center", s.center.asLong());
            t.putInt("radius", s.radius);
            t.putBoolean("growth", s.growth);
            t.putInt("base", s.base);
            t.putInt("next", s.next);
            t.putLong("startAt", s.startAt);
            t.putInt("clear", s.clear);
            t.putBoolean("mend", s.mend);
            t.putBoolean("paving", s.paving);
            if (s.home != null) t.put("home", s.home.save());
            if (s.replaces != null) t.put("replaces", s.replaces.save());
            // A palette of states and, for each block, its position and palette index.
            List<BlockState> palette = new ArrayList<>();
            long[] positions = new long[s.positions.size()];
            int[] indices = new int[s.positions.size()];
            for (int i = 0; i < s.positions.size(); i++) {
                positions[i] = s.positions.get(i).asLong();
                int at = palette.indexOf(s.states.get(i));
                if (at < 0) { at = palette.size(); palette.add(s.states.get(i)); }
                indices[i] = at;
            }
            ListTag pal = new ListTag();
            for (BlockState state : palette) pal.add(NbtUtils.writeBlockState(state));
            t.put("palette", pal);
            t.putLongArray("positions", positions);
            t.putIntArray("indices", indices);
            list.add(t);
        }
        tag.put("sites", list);
        return tag;
    }

    static Rebuild load(CompoundTag tag, HolderLookup.Provider registries) {
        Rebuild data = new Rebuild();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag raw : tag.getList("sites", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Site s = new Site(BlockPos.of(t.getLong("bell")), BlockPos.of(t.getLong("center")));
            s.next = t.getInt("next");
            s.radius = t.getInt("radius");
            s.growth = t.getBoolean("growth");
            s.base = t.contains("base") ? t.getInt("base") : Integer.MIN_VALUE;
            s.startAt = t.getLong("startAt");
            s.clear = t.getInt("clear");
            s.mend = t.getBoolean("mend");
            s.paving = t.getBoolean("paving");
            if (t.contains("home")) s.home = Projects.Home.load(t.getCompound("home"));
            if (t.contains("replaces")) s.replaces = Projects.Home.load(t.getCompound("replaces"));
            List<BlockState> palette = new ArrayList<>();
            for (Tag p : t.getList("palette", Tag.TAG_COMPOUND)) palette.add(NbtUtils.readBlockState(blocks, (CompoundTag) p));
            long[] positions = t.getLongArray("positions");
            int[] indices = t.getIntArray("indices");
            for (int i = 0; i < Math.min(positions.length, indices.length); i++) {
                if (indices[i] < 0 || indices[i] >= palette.size()) continue;
                s.positions.add(BlockPos.of(positions[i]));
                s.states.add(palette.get(indices[i]));
            }
            data.sites.add(s);
        }
        return data;
    }
}
