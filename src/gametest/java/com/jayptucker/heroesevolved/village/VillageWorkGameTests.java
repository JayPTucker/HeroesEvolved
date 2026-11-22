package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Villagers at work: gathering for their project and carrying it to the board, crews at every site (and none where
 * nobody's left), mending what was broken from the village's own templates, and building on a slope.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VillageWorkGameTests {
    private static final int R = 44;

    private static BlockPos meadow(GameTestHelper h, int step) {
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(6, 0, 6)).atY(300);
        PlayerBuilt.clear(level, center.offset(-R - 16, -2, -R - 16), center.offset(R + 16, 30, R + 16));
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                // A terrace: the far side stands {@code step} higher, earth all the way down.
                int top = dz > 10 ? step : 0;
                for (int y = 0; y <= top; y++) level.setBlock(center.offset(dx, y, dz), (y == top ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
            }
        BlockPos bell = center.above();
        level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static List<Villager> people(GameTestHelper h, BlockPos near, int n) {
        List<Villager> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Villager v = EntityType.VILLAGER.create(h.getLevel());
            v.moveTo(Vec3.atBottomCenterOf(near.offset(i % 3, 0, i / 3)));
            v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
            v.setNoAi(true);
            h.getLevel().addFreshEntity(v);
            out.add(v);
        }
        return out;
    }

    private static void clear(GameTestHelper h, BlockPos bell) {
        ServerLevel level = h.getLevel();
        for (Villager x : level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R + 4, 30, R + 4))) x.discard();
        for (Rebuild.Site s : Rebuild.all(level)) if (s.bell().equals(bell)) Rebuild.drop(level, s);
        BlockPos center = bell.below();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-R - 2, -2, -R - 2), center.offset(R + 2, 24, R + 2)))
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(bell).inflate(R + 4, 30, R + 4))) item.discard();
        VillageMemory.forget(level, bell);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "work.gather")
    public static void villagersGatherForTheirProjectAndCarryItToTheBoard(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h, 0);
        long time = level.getDayTime();
        try {
            var v = VillageMemory.of(level, bell);
            people(h, bell.offset(2, 0, 2), 6);
            level.setDayTime(Days.DAY * (level.getDayTime() / Days.DAY) + 6_000);
            Projects.consider(level, v);
            var p = v.project();
            h.assertTrue(p != null && p.gathered == Days.today(level), "It starts counting days the day it posts");
            float before = p.have(Projects.Material.PLANKS);
            level.setDayTime(level.getDayTime() + Days.DAY);
            Workers.gather(level, v);
            h.assertTrue(Workers.carrying(v), "The next day someone carries a share to the board");
            Villager carrier = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(10), x -> !x.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty())
                    .stream().findFirst().orElse(null);
            h.assertTrue(carrier != null, "Holding it");
            carrier.teleportTo(v.board().chest().getX() + .5, v.board().chest().getY(), v.board().chest().getZ() + 1.5);
            h.runAfterDelay(25, () -> {
                h.assertTrue(!Workers.carrying(v) && p.have(Projects.Material.PLANKS) > before, "At the board it goes in: " + before + " -> " + p.have(Projects.Material.PLANKS));
                h.assertTrue(carrier.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "And their hands are empty again");
                h.assertTrue(Workers.rate(level, v, 6) > Workers.rate(level, v, 1), "More people gather more");
                level.setDayTime(time);
                clear(h, bell);
                h.succeed();
            });
        } catch (RuntimeException | AssertionError e) {
            level.setDayTime(time);
            clear(h, bell);
            throw e;
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "work.crew")
    public static void aSiteWaitsWithNobodyLeftAndItsCrewHoldsWhatGoesDown(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h, 0);
        long time = level.getDayTime();
        try {
            var v = VillageMemory.of(level, bell);
            Rebuild.Site site = Growth.grow(level, v);
            level.setDayTime(Days.DAY * (level.getDayTime() / Days.DAY) + 6_000);
            site.startAt = Days.clock(level);
            for (int i = 0; i < 5; i++) Rebuild.work(level, Rebuild.get(level), site);
            h.assertTrue(site.done() == 0, "Nobody left in the village: nothing goes up");
            List<Villager> crew = people(h, site.center().above(), 4);
            for (int i = 0; i < 5; i++) Rebuild.work(level, Rebuild.get(level), site);
            h.assertTrue(site.done() > 0, "With people it goes up: " + site.done());
            h.assertTrue(Workers.crew(level, site).size() == Workers.CREW, "A crew of " + Workers.CREW + ": " + Workers.crew(level, site).size());
            h.assertTrue(crew.stream().anyMatch(x -> !x.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()), "Holding what goes down next");
            Rebuild.hurry(level, site, 100_000);
            h.assertTrue(crew.stream().allMatch(x -> x.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()), "Hands empty when it's done");
            h.succeed();
        } finally {
            level.setDayTime(time);
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "work.mend")
    public static void aBrokenHouseIsMendedWhileAnyoneIsLeft(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h, 0);
        try {
            var v = VillageMemory.of(level, bell);
            Rebuild.Site built = Growth.grow(level, v);
            Rebuild.hurry(level, built, 100_000);
            Projects.Home home = built.home();
            // The house brought a newcomer; with them gone, nobody lives there.
            for (Villager x : level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R + 4, 30, R + 4))) x.discard();
            Repairs.consider(level, v);
            h.assertTrue(!v.baselined, "Nobody lives there: it doesn't even look");
            people(h, bell.offset(2, 0, 2), 3);
            h.assertTrue(Repairs.check(level, v) == null && v.baselined, "First it learns how it was");
            // Knock a hole in it (an explosion would), and a window out by hand.
            List<BlockPos> walls = new ArrayList<>();
            for (BlockPos p : built.positions) {
                if (walls.size() >= 6) break;
                if (p.getY() == built.base + 2 && level.getBlockState(p).isSolidRender(level, p)) walls.add(p.immutable());
            }
            for (BlockPos p : walls) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            BlockPos byHand = walls.getLast();
            PlayerBuilt.clearedByHand(level, byHand);
            var gone = Repairs.missing(level, v, home);
            // What hung on those walls (a torch) may have fallen too: that's missing as well.
            h.assertTrue(gone.containsAll(walls.subList(0, walls.size() - 1)) && !gone.contains(byHand), "The holes are missing; the one broken by hand is left be: " + gone.size()
                    + " of " + walls + " missing " + gone + " in " + home.template().getPath());
            Rebuild.Site mend = Repairs.check(level, v);
            h.assertTrue(mend != null && mend.mending() && mend.size() == gone.size(), "It plans the mending: " + (mend == null ? "none" : mend.size()));
            Rebuild.hurry(level, mend, 10_000);
            for (BlockPos p : walls.subList(0, 5)) h.assertTrue(!level.getBlockState(p).isAir(), "Mended at " + p.toShortString());
            h.assertTrue(level.getBlockState(byHand).isAir(), "The player's gap stays");
            h.assertTrue(Repairs.missing(level, v, home).isEmpty() || Repairs.missing(level, v, home).equals(List.of()), "Whole again");
            h.succeed();
        } finally {
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "work.slope")
    public static void aHouseGoesUpOnASlopeWithAFoundation(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h, 2);
        try {
            var v = VillageMemory.of(level, bell);
            var size = new net.minecraft.core.Vec3i(7, 8, 7);
            int x0 = bell.getX() - 3, z0 = bell.getZ() + 8;   // across the terrace's step
            h.assertTrue(Growth.fits(level, x0, z0, size, 1) == null, "Not on level ground");
            BlockPos corner = Growth.fits(level, x0, z0, size, Growth.SLOPE);
            h.assertTrue(corner != null, "But on a gentle slope");
            List<BlockPos> dig = new ArrayList<>(), fill = new ArrayList<>();
            List<net.minecraft.world.level.block.state.BlockState> states = new ArrayList<>();
            Growth.grade(level, v, corner, size, dig, fill, states);
            h.assertTrue(!dig.isEmpty() || !fill.isEmpty(), "Ground to dig or a foundation to lay: " + dig.size() + " / " + fill.size());
            // A house on the terrace: whatever spot it takes, it stands whole.
            for (int i = 0; i < 3; i++) {
                Rebuild.Site site = Growth.grow(level, v);
                h.assertTrue(site != null, "Room for a house");
                Rebuild.hurry(level, site, 100_000);
                h.assertTrue(Projects.standing(level, v, site.home()) > 0.9F, "It stands: " + Projects.standing(level, v, site.home()));
            }
            h.succeed();
        } finally {
            clear(h, bell);
        }
    }
}
