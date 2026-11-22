package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Villages that grow: the score, moods, havens, building a house of the village's kind, wearing and losing people. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VillageGrowthGameTests {
    private static BlockPos at(GameTestHelper h, int x, int z) { return h.absolutePos(new BlockPos(x, 2, z)); }

    private static BlockPos bell(GameTestHelper h, BlockPos bell) {
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static Villager villager(GameTestHelper h, BlockPos at) {
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(Vec3.atBottomCenterOf(at));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        v.setNoAi(true);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "growth.score")
    public static void theScoreCountsWhatHappenedAndWhatItHas(GameTestHelper h) {
        BlockPos bell = bell(h, at(h, 6, 6));
        Villager g = villager(h, at(h, 3, 3));
        try {
            var v = VillageMemory.of(h.getLevel(), bell);
            int start = Growth.score(h.getLevel(), v);
            VillageMemory.record(h.getLevel(), bell, "the wildfire", VillageMemory.Outcome.SAVED, null, List.of(), 3, 3);
            int saved = Growth.score(h.getLevel(), v);
            h.assertTrue(saved >= 3 + start - 5, "Saved counts for it: " + start + " -> " + saved);
            Renegades.guard(g, ModAbilities.HYDROKINESIS_ID);
            h.assertTrue(Growth.score(h.getLevel(), v) == saved + 2, "A guardian counts for it");
            Growth.kindness(h.getLevel(), bell);
            Growth.sheltered(h.getLevel(), bell);
            int helped = Growth.score(h.getLevel(), v);
            h.assertTrue(helped == saved + 2 + 1 + 2, "A favour and a family taken in count for it: " + helped);
            h.assertTrue(Growth.mood(h.getLevel(), v) == Growth.Mood.THRIVING && Growth.haven(h.getLevel(), v), "Thriving, and with a guardian a haven");
            VillageMemory.record(h.getLevel(), bell, "a renegade", VillageMemory.Outcome.HIT, null, List.of(), 2, 3);
            VillageMemory.record(h.getLevel(), bell, "the Company", VillageMemory.Outcome.HIT, null, List.of(), 2, 3);
            h.assertTrue(Growth.score(h.getLevel(), v) < helped - 6, "Hit, and robbed by the Company, count against it");
            h.succeed();
        } finally {
            g.discard();
            VillageMemory.forget(h.getLevel(), bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "growth.house")
    public static void aThrivingVillageBuildsOneOfItsOwnHouses(GameTestHelper h) {
        var level = h.getLevel();
        // An open meadow in the sky, nothing above it: room for a house the way a real village has.
        BlockPos center = h.absolutePos(new BlockPos(6, 0, 6)).atY(300);
        int r = 40;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) level.setBlock(center.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        BlockPos bell = bell(h, center.above());
        List<Villager> people = new ArrayList<>();
        try {
            var v = VillageMemory.of(level, bell);
            int before = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(r, 20, r)).size();
            Rebuild.Site site = Growth.grow(level, v);
            h.assertTrue(site != null && site.growth() && site.size() > 30, "A house planned: " + (site == null ? "none" : site.size() + " blocks"));
            h.assertTrue(site.center().distSqr(bell) >= (Growth.NEAREST - 8) * (Growth.NEAREST - 8), "Out from the bell");
            Rebuild.hurry(level, site, 100_000);
            h.assertTrue(!Rebuild.all(level).contains(site) && v.houses() == 1, "Built, and counted");
            boolean bed = false;
            for (BlockPos p : BlockPos.betweenClosed(site.center().offset(-8, -1, -8), site.center().offset(8, 10, 8)))
                if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BedBlock) bed = true;
            h.assertTrue(bed, "A house with a bed in it, so the village can grow on its own");
            people.addAll(level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(r, 20, r)));
            h.assertTrue(people.size() == before + 1, "And a newcomer moves in: " + before + " -> " + people.size());
            h.succeed();
        } finally {
            for (Villager p : people) p.discard();
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, 0, -r), center.offset(r, 14, r)))
                if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            VillageMemory.forget(level, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "growth.decline")
    public static void aStrugglingVillageWearsAndLosesPeopleButNeverTooMany(GameTestHelper h) {
        BlockPos bell = bell(h, at(h, 6, 6));
        List<Villager> people = new ArrayList<>();
        for (int i = 0; i < 5; i++) people.add(villager(h, at(h, 1 + i * 2, 2)));
        for (int i = 0; i < 4; i++) {
            h.getLevel().setBlockAndUpdate(at(h, 2 + i, 9), Blocks.GLASS_PANE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(at(h, 2 + i, 10), Blocks.COBBLESTONE.defaultBlockState());
        }
        try {
            var v = VillageMemory.of(h.getLevel(), bell);
            VillageMemory.record(h.getLevel(), bell, "a renegade", VillageMemory.Outcome.HIT, null, List.of(), 4, 6);
            VillageMemory.record(h.getLevel(), bell, "a meltdown", VillageMemory.Outcome.HIT, null, List.of(), 3, 4);
            h.assertTrue(Growth.mood(h.getLevel(), v) == Growth.Mood.STRUGGLING, "Struggling: " + Growth.score(h.getLevel(), v));
            int changed = 0;
            for (int i = 0; i < 20 && changed == 0; i++) changed += Growth.wear(h.getLevel(), bell);
            h.assertTrue(changed > 0, "It looks worn");
            int left = 0;
            for (int i = 0; i < 5; i++) if (Growth.leave(h.getLevel(), bell)) left++;
            h.assertTrue(left == 2 && people.stream().filter(Villager::isAlive).count() == Growth.FEWEST, "People leave, down to " + Growth.FEWEST + ": " + left);
            h.succeed();
        } finally {
            for (Villager p : people) p.discard();
            VillageMemory.forget(h.getLevel(), bell);
        }
    }
}
