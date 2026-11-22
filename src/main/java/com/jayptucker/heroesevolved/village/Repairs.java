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

    /** Looks {@code v} over now and plans mending its worst-hit building. The site, or null. Tests call it directly. */
    public static Rebuild.Site check(ServerLevel level, VillageMemory.Village v) {
        List<Projects.Home> pieces = Projects.pieces(level, v);
        if (!v.baselined) {
            for (Projects.Home h : pieces) for (BlockPos p : missing(level, v, h)) v.unbuilt.add(p.asLong());
            v.baselined = true;
            VillageMemory.get(level).setDirty();
            return null;
        }
        List<Rebuild.Site> mending = new ArrayList<>();
        for (Rebuild.Site s : Rebuild.all(level)) if (s.mend && s.bell().equals(v.bell)) mending.add(s);
        if (mending.size() >= MOST_SITES) return null;
        int from = Growth.mood(level, v) == Growth.Mood.STRUGGLING ? STRUGGLING_FROM : FROM;
        Projects.Home worst = null;
        List<BlockPos> worstMissing = List.of();
        for (Projects.Home h : pieces) {
            if (mending.stream().anyMatch(s -> h.equals(s.home()))) continue;
            List<BlockPos> missing = missing(level, v, h);
            if (missing.size() >= from && missing.size() > worstMissing.size() && !burning(level, h)) { worst = h; worstMissing = missing; }
        }
        if (worst == null) return null;
        StructureTemplate t = level.getStructureManager().get(worst.template()).orElse(null);
        if (t == null) return null;
        Map<BlockPos, BlockState> want = new HashMap<>();
        var blocks = Growth.blocks(level, t, worst.origin(), worst.rotation());
        if (blocks == null) return null;
        Growth.restyle(level, v, worst.template(), blocks);
        for (var b : blocks) want.put(b.pos(), b.state());
        List<BlockPos> positions = new ArrayList<>(worstMissing);
        positions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        List<BlockState> states = new ArrayList<>();
        for (BlockPos p : positions) states.add(want.get(p));
        var box = t.getBoundingBox(new StructurePlaceSettings().setRotation(worst.rotation()), worst.origin());
        Rebuild.Site site = Rebuild.mend(level, v.bell, box.getCenter(), positions, states);
        site.home = worst;
        BandChatter.news(level, v.bell, v.name + "'s patching up what was broken. Everyone's lending a hand.", (int) Days.DAY);
        HeroesEvolved.LOGGER.info("Village {} will mend {} blocks of {} at {}", v.name, positions.size(), worst.template(), worst.origin());
        return site;
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
