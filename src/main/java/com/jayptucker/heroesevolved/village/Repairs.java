package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.radio.BandChatter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Villages put back what was broken. A village knows its buildings as they were built: its own pieces (houses, wells,
 * lamps, its meeting point) and the ones it built itself. Whatever goes missing from one later (an explosion, a fire,
 * a storm, a power, a renegade) its people rebuild, a building at a time, as long as anyone is left. What was missing
 * when the village was first known is how it was, not damage; what a player put down or broke by hand ({@link PlayerBuilt})
 * and a player's own home are never touched. What the player sees: {@code docs/village-growth.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Repairs {
    /** How often a village looks itself over (ticks), and how much must be missing from a building before it's mended. */
    static final int CHECK = 600, FROM = 3, STRUGGLING_FROM = 8, MOST_SITES = 2;

    private static final Map<Long, Long> LOOKED = new HashMap<>();
    private static final Set<Long> SOON = new HashSet<>();

    private Repairs() {}

    /** Each pass for a loaded, untroubled village with people (from {@link Growth}): mends a damaged building, if any. */
    public static void consider(ServerLevel level, VillageMemory.Village v) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        long now = level.getGameTime();
        Long looked = LOOKED.get(v.bell.asLong());
        if (!SOON.remove(v.bell.asLong()) && looked != null && now - looked < CHECK && now >= looked) return;
        LOOKED.put(v.bell.asLong(), now);
        if (Villages.members(level, v.bell).isEmpty()) return;
        check(level, v);
    }

    /**
     * Looks {@code v} over now: if anything was broken, it asks for what it needs to put it back on its notice board (a
     * repair project, {@link Projects#repair}). The project, or null. Tests call it directly.
     */
    public static Projects.Project check(ServerLevel level, VillageMemory.Village v) {
        List<Projects.Home> pieces = Projects.pieces(level, v);
        if (!v.baselined) {
            for (Projects.Home h : pieces) for (BlockPos p : missing(level, v, h)) v.unbuilt.add(p.asLong());
            v.baselined = true;
            VillageMemory.get(level).setDirty();
            return null;
        }
        List<Rebuild.Site> mending = new ArrayList<>();
        for (Rebuild.Site s : Rebuild.all(level)) if (s.mend && s.bell().equals(v.bell)) mending.add(s);
        int from = Growth.mood(level, v) == Growth.Mood.STRUGGLING ? STRUGGLING_FROM : FROM;
        Map<Projects.Home, List<BlockPos>> damage = new java.util.LinkedHashMap<>();
        java.util.EnumMap<Projects.Material, Float> needs = new java.util.EnumMap<>(Projects.Material.class);
        for (Projects.Home h : pieces) {
            if (mending.stream().anyMatch(s -> h.equals(s.home()))) continue;
            List<BlockPos> missing = missing(level, v, h);
            if (missing.size() < from || burning(level, h)) continue;
            damage.put(h, missing);
            Map<BlockPos, BlockState> want = wanted(level, v, h);
            for (BlockPos p : missing) if (want.get(p) != null) Projects.add(needs, want.get(p));
        }
        if (damage.isEmpty()) return null;
        return Projects.repair(level, v, List.copyOf(damage.keySet()), needs);
    }

    /** What {@code h} was built of, block by block, in its village's colours. */
    static Map<BlockPos, BlockState> wanted(ServerLevel level, VillageMemory.Village v, Projects.Home h) {
        Map<BlockPos, BlockState> want = new HashMap<>();
        StructureTemplate t = level.getStructureManager().get(h.template()).orElse(null);
        if (t == null) return want;
        var blocks = Growth.blocks(level, t, h.origin(), h.rotation());
        if (blocks == null) return want;
        Growth.restyle(level, v, h.template(), blocks);
        for (var b : blocks) want.put(b.pos(), b.state());
        return want;
    }

    /**
     * Plans putting back what's missing from {@code h} now (its repair project has everything): a mend site its people
     * build. Null if nothing's missing any more.
     */
    public static Rebuild.Site plan(ServerLevel level, VillageMemory.Village v, Projects.Home h) {
        List<BlockPos> positions = new ArrayList<>(missing(level, v, h));
        if (positions.isEmpty()) return null;
        StructureTemplate t = level.getStructureManager().get(h.template()).orElse(null);
        if (t == null) return null;
        Map<BlockPos, BlockState> want = wanted(level, v, h);
        positions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        List<BlockState> states = new ArrayList<>();
        for (BlockPos p : positions) states.add(want.get(p));
        var box = t.getBoundingBox(new StructurePlaceSettings().setRotation(h.rotation()), h.origin());
        Rebuild.Site site = Rebuild.mend(level, v.bell, box.getCenter(), positions, states);
        site.home = h;
        HeroesEvolved.LOGGER.info("Village {} will mend {} blocks of {} at {}", v.name, positions.size(), h.template(), h.origin());
        return site;
    }

    /** Whether {@code v} has mending under way. */
    public static boolean mending(ServerLevel level, VillageMemory.Village v) {
        for (Rebuild.Site s : Rebuild.all(level)) if (s.mend && s.bell().equals(v.bell)) return true;
        return false;
    }

    /** What's gone from {@code h} since it was built: where it had a block and now there's nothing (or fire). */
    public static List<BlockPos> missing(ServerLevel level, VillageMemory.Village v, Projects.Home h) {
        List<BlockPos> out = new ArrayList<>();
        StructureTemplate t = level.getStructureManager().get(h.template()).orElse(null);
        if (t == null) return out;
        var blocks = Growth.blocks(level, t, h.origin(), h.rotation());
        if (blocks == null) return out;
        Growth.restyle(level, v, h.template(), blocks);
        for (var b : blocks) {
            BlockState want = b.state();
            // Its walls, roof and furniture, not its plants: crops, grass and flowers come and go on their own.
            if (want.isAir() || want.is(Blocks.STRUCTURE_VOID) || want.is(Blocks.JIGSAW) || want.canBeReplaced() || want.is(BlockTags.CROPS)
                    || !want.getFluidState().isEmpty()) continue;
            BlockPos p = b.pos();
            if (!level.hasChunkAt(p)) return List.of();
            BlockState now = level.getBlockState(p);
            boolean gone = now.isAir() || now.getBlock() instanceof BaseFireBlock || now.canBeReplaced() && now.getFluidState().isEmpty() && !now.is(want.getBlock());
            if (!gone || v.unbuilt.contains(p.asLong()) || PlayerBuilt.is(level, p) || PlayerBuilt.cleared(level, p)) continue;
            out.add(p.immutable());
        }
        return out;
    }

    /** Whether anything round {@code h} is still on fire (the village waits for it to go out). */
    static boolean burning(ServerLevel level, Projects.Home h) {
        StructureTemplate t = level.getStructureManager().get(h.template()).orElse(null);
        if (t == null) return false;
        var box = t.getBoundingBox(new StructurePlaceSettings().setRotation(h.rotation()), h.origin()).inflatedBy(1);
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) return true;
        return false;
    }

    /** Something blew up near a village: it looks itself over at its next pass instead of waiting. */
    @SubscribeEvent
    public static void exploded(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var c = event.getExplosion().center();
        for (VillageMemory.Village v : VillageMemory.all(level))
            if (v.bell.distToCenterSqr(c) <= 96 * 96) SOON.add(v.bell.asLong());
    }

    /** A village was forgotten. */
    static void forgotten(BlockPos bell) {
        LOOKED.remove(bell.asLong());
        SOON.remove(bell.asLong());
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        LOOKED.clear();
        SOON.clear();
    }
}
