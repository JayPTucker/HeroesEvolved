package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Village projects: the notice board by the bell, what each building asks for, filling the chest and the signs counting
 * it, building once it's all in and posting the next, mending the board, and nothing without {@code mobGriefing}.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VillageProjectGameTests {
    private static final int R = 40;

    private static final class Listener extends FakePlayer {
        final List<String> heard = new ArrayList<>();
        Listener(GameTestHelper h) { super(h.getLevel(), new GameProfile(UUID.randomUUID(), "Giver")); }
        @Override public void sendSystemMessage(Component c) { heard.add(c.getString()); }
    }

    /** An open meadow in the sky with a bell in the middle, nothing above it: room for a board and a building. */
    private static BlockPos meadow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(6, 0, 6)).atY(300);
        // Ground reused from earlier tests may carry marks from blocks a test player put down there.
        PlayerBuilt.clear(level, center.offset(-R - 16, -2, -R - 16), center.offset(R + 16, 30, R + 16));
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) level.setBlock(center.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        BlockPos bell = center.above();
        level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static void clear(GameTestHelper h, BlockPos bell, Entity... entities) {
        ServerLevel level = h.getLevel();
        for (Entity e : entities)
            if (e instanceof FakePlayer p) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else if (e != null) e.discard();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R + 4, 24, R + 4))) v.discard();
        for (Rebuild.Site s : Rebuild.all(level)) if (s.bell().equals(bell)) Rebuild.drop(level, s);
        BlockPos center = bell.below();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-R - 2, 0, -R - 2), center.offset(R + 2, 20, R + 2)))
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        VillageMemory.forget(level, bell);
    }

    private static String line(ServerLevel level, BlockPos sign, int line) {
        return level.getBlockEntity(sign) instanceof SignBlockEntity s ? s.getFrontText().getMessage(line, false).getString() : "<no sign>";
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "projects.board")
    public static void aVillagePutsUpItsNoticeBoardByTheBell(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        try {
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            Projects.Board b = v.board();
            h.assertTrue(b != null, "A board went up");
            double d = Math.sqrt(b.origin().distSqr(bell.atY(b.origin().getY())));
            h.assertTrue(d >= Projects.BOARD_NEAREST - 1 && d <= Projects.BOARD_FURTHEST + 1, "By the bell: " + d);
            h.assertTrue(Projects.whole(level, v), "Every piece of it");
            Vec3 toBell = Vec3.atCenterOf(bell).subtract(Vec3.atCenterOf(b.origin()));
            h.assertTrue(toBell.x * b.facing().getStepX() + toBell.z * b.facing().getStepZ() > 0, "Its signs face the bell");
            for (int i = 0; i < 6; i++)
                h.assertTrue(level.getBlockState(b.sign(i)).getBlock() instanceof WallSignBlock
                        && level.getBlockState(b.sign(i)).getValue(WallSignBlock.FACING) == b.facing(), "Sign " + i + " on its face");
            h.assertTrue(level.getBlockEntity(b.chest()) instanceof ChestBlockEntity chest && chest.getCustomName() != null
                    && chest.getCustomName().getString().contains(v.name()), "A chest for the village's builders");
            Projects.Project p = v.project();
            h.assertTrue(p != null && p.stage() == Projects.Stage.GATHERING && p.title().equals("A new house"), "Its first project is a house");
            h.assertTrue(p.need(Projects.Material.PLANKS) > 0, "It asks for wood: " + p.need());
            h.assertTrue(line(level, b.sign(0), 0).equals(v.name()), "The board says whose it is: " + line(level, b.sign(0), 0));
            h.assertTrue(line(level, b.sign(0), 1).equals("A new house") && line(level, b.sign(0), 3).equals("0% gathered"),
                    "What it's for and how far along: " + line(level, b.sign(0), 1) + " / " + line(level, b.sign(0), 3));
            h.assertTrue(line(level, b.sign(1), 0).equals("Planks") && line(level, b.sign(1), 1).equals("0 / " + p.need(Projects.Material.PLANKS)),
                    "Each material and how much: " + line(level, b.sign(1), 0) + " " + line(level, b.sign(1), 1));
            h.assertTrue(level.getBlockEntity(b.sign(0)) instanceof SignBlockEntity s && s.isWaxed(), "Waxed, so nobody rewrites it");
            h.succeed();
        } finally {
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "projects.plans")
    public static void everyKindOfVillageAsksForSensibleMaterials(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (String kind : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
            List<Projects.Plan> plans = Projects.plans(level, kind);
            h.assertTrue(plans.size() >= 10, kind + " has its buildings: " + plans.size());
            h.assertTrue(plans.stream().anyMatch(p -> p.title().equals("A new house")), kind + " has houses");
            h.assertTrue(plans.stream().anyMatch(p -> !p.title().contains("house")), kind + " has workshops and farms");
            boolean beds = false;
            for (Projects.Plan plan : plans) {
                var t = level.getStructureManager().get(plan.id()).orElse(null);
                h.assertTrue(t != null, "Template " + plan.id());
                var m = Projects.materials(Growth.blocks(level, t, BlockPos.ZERO, net.minecraft.world.level.block.Rotation.NONE));
                float total = 0;
                for (float n : m.values()) {
                    h.assertTrue(Float.isFinite(n) && n > 0, plan.id() + " has a positive, finite material count: " + m);
                    total += n;
                }
                HeroesEvolved.LOGGER.info("PROJECT {} {}: {}", plan.id().getPath(), plan.title(), m);
                // Open farms and snow houses can be mostly natural blocks, which the village supplies itself.
                // Even three planks can be the full manufactured bill; no fixed minimum fits every template.
                h.assertTrue(!m.isEmpty() && total > 0 && total <= 1500, plan.id() + " asks for something sensible: " + m);
                if (plan.title().equals("A new house") && m.getOrDefault(Projects.Material.BEDS, 0F) >= 1) beds = true;
            }
            h.assertTrue(beds, kind + "'s houses have beds to ask for");
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "projects.fill")
    public static void theVillageTakesWhatItNeedsAndTheSignsCountIt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener giver = new Listener(h);
        try {
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            Projects.Project p = v.project();
            Projects.Board b = v.board();
            giver.setPos(Vec3.atBottomCenterOf(b.chest().relative(b.facing())));
            giver.setGameMode(GameType.SURVIVAL);
            level.addNewPlayer(giver);
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(b.chest());
            int planks = p.need(Projects.Material.PLANKS);
            h.assertTrue(planks > 8, "A house needs more than two logs of wood: " + planks);
            chest.setItem(0, new ItemStack(Items.OAK_LOG, 2));
            chest.setItem(1, new ItemStack(Items.DIRT, 10));
            // Closing the chest is what hands it over.
            NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(giver, ChestMenu.threeRows(1, giver.getInventory(), chest)));
            h.assertTrue(Math.abs(p.have(Projects.Material.PLANKS) - 8) < 1e-3, "Two logs are eight planks: " + p.have(Projects.Material.PLANKS));
            h.assertTrue(chest.getItem(0).isEmpty() && chest.getItem(1).is(Items.DIRT) && chest.getItem(1).getCount() == 10,
                    "It takes what it needs and leaves the rest");
            h.assertTrue(line(level, b.sign(1), 1).equals("8 / " + planks), "The sign counts it: " + line(level, b.sign(1), 1));
            h.assertTrue(p.givers().contains(giver.getUUID()) && v.standing(giver.getUUID()) >= 1, "And thinks better of the giver");
            h.assertTrue(giver.heard.stream().anyMatch(s -> s.contains("take 8 planks") && s.contains("Still needed")),
                    "The giver hears what was taken: " + giver.heard);
            // More than it needs: it takes only what's left.
            chest.setItem(2, new ItemStack(Items.OAK_PLANKS, 64));
            chest.setItem(3, new ItemStack(Items.OAK_PLANKS, 64));
            chest.setItem(4, new ItemStack(Items.OAK_PLANKS, 64));
            Projects.collect(level, v, null);
            int left = 0;
            for (int i = 2; i <= 4; i++) left += chest.getItem(i).getCount();
            h.assertTrue(p.has(Projects.Material.PLANKS) && left == 192 - (planks - 8), "Only what it still needed: " + left + " left");
            h.assertTrue(line(level, b.sign(1), 1).equals("Done (" + planks + ")"), "And the sign says so: " + line(level, b.sign(1), 1));
            h.assertTrue(p.stage() == Projects.Stage.GATHERING || p.complete(), "Still gathering the rest");
            h.succeed();
        } finally {
            clear(h, bell, giver);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "projects.neighbouringbell")
    public static void donationsBelongToTheChestEvenWhenAnotherBellIsCloser(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h), otherBell = null;
        Listener giver = new Listener(h);
        try {
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            var board = v.board();
            giver.setPos(Vec3.atBottomCenterOf(board.chest().relative(board.facing())));
            giver.setGameMode(GameType.SURVIVAL);
            level.addNewPlayer(giver);
            otherBell = giver.blockPosition().relative(board.facing());
            level.setBlockAndUpdate(otherBell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
            var neighbour = VillageMemory.of(level, otherBell);
            h.assertTrue(VillageMemory.near(level, giver.blockPosition()) == neighbour, "Another village's bell is closer to the giver");
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(board.chest());
            chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 1));
            NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(giver, ChestMenu.threeRows(1, giver.getInventory(), chest)));
            h.assertTrue(v.project().have(Projects.Material.PLANKS) == 1 && chest.getItem(0).isEmpty(), "The opened chest's project receives the donation");
            h.assertTrue(v.project().givers().contains(giver.getUUID()), "The right village credits the giver");
            h.succeed();
        } finally {
            if (otherBell != null) VillageMemory.forget(level, otherBell);
            clear(h, bell, giver);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "projects.doublechest")
    public static void donationsInEitherHalfOfADoubleChestAreCollected(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener giver = new Listener(h);
        try {
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            var board = v.board();
            giver.setPos(Vec3.atBottomCenterOf(board.chest().relative(board.facing())));
            giver.setGameMode(GameType.SURVIVAL);
            level.addNewPlayer(giver);
            BlockPos added = board.chest().relative(board.facing().getClockWise());
            level.setBlock(added, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, board.facing())
                    .setValue(ChestBlock.TYPE, ChestType.RIGHT), 2);
            level.setBlock(board.chest(), level.getBlockState(board.chest()).setValue(ChestBlock.TYPE, ChestType.LEFT), 2);
            ChestBlockEntity original = (ChestBlockEntity) level.getBlockEntity(board.chest());
            ChestBlockEntity extra = (ChestBlockEntity) level.getBlockEntity(added);
            extra.setItem(0, new ItemStack(Items.OAK_PLANKS, 1));
            var inventory = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, level.getBlockState(board.chest()), level, board.chest(), true);
            h.assertTrue(inventory != null && inventory.getContainerSize() == 54, "The chest has two connected halves");
            NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(giver, ChestMenu.sixRows(1, giver.getInventory(), inventory)));
            h.assertTrue(extra.getItem(0).isEmpty() && v.project().have(Projects.Material.PLANKS) == 1, "A gift in the added half is collected");
            h.assertTrue(v.project().givers().contains(giver.getUUID()), "The giver is credited");
            // Periodic collection (including hopper deliveries) reads both halves too.
            original.setItem(0, new ItemStack(Items.OAK_PLANKS, 1));
            extra.setItem(0, new ItemStack(Items.OAK_PLANKS, 1));
            h.assertTrue(Projects.collect(level, v, null) == 2 && v.project().have(Projects.Material.PLANKS) == 3, "Both halves are read without a player too");
            h.succeed();
        } finally { clear(h, bell, giver); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "projects.build")
    public static void onceItHasEverythingItBuildsAndPostsTheNext(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        long time = level.getDayTime();
        try {
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            Projects.Project p = v.project();
            int before = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R, 20, R)).size();
            Projects.fill(level, v);
            h.assertTrue(p.stage() == Projects.Stage.BUILDING, "Everything's in: it plans the building (" + p.stage() + ")");
            Rebuild.Site site = Rebuild.all(level).stream().filter(s -> s.growth() && s.bell().equals(bell)).findFirst().orElse(null);
            h.assertTrue(site != null && site.size() > 30, "A site for it");
            Projects.signs(level, v);
            h.assertTrue(line(level, v.board().sign(0), 3).equals("Starts at dawn"), "The board says when: " + line(level, v.board().sign(0), 3));
            Rebuild.hurry(level, site, 100_000);
            h.assertTrue(p.stage() == Projects.Stage.DONE && v.houses() == 1, "Built: " + p.stage());
            h.assertTrue(line(level, v.board().sign(0), 3).equals("Finished!"), "And the board says so: " + line(level, v.board().sign(0), 3));
            int after = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R, 20, R)).size();
            h.assertTrue(after == before + Math.min(2, p.beds()) && p.beds() >= 1, "A newcomer for each bed: " + before + " -> " + after);
            Projects.consider(level, v);
            h.assertTrue(v.project() == p, "Not the same day");
            level.setDayTime(time + Days.DAY);
            Projects.consider(level, v);
            h.assertTrue(v.project() != p && v.project().stage() == Projects.Stage.GATHERING && !v.project().title().equals("A new house"),
                    "The next morning it posts something else: " + (v.project() == null ? "none" : v.project().title()));
            h.succeed();
        } finally {
            level.setDayTime(time);
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "projects.mend")
    public static void aBrokenBoardIsMendedOnceADay(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        long time = level.getDayTime();
        try {
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            Projects.Board b = v.board();
            level.setBlock(b.sign(2), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(b.chest(), Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(!Projects.whole(level, v), "Broken");
            Projects.consider(level, v);
            h.assertTrue(Projects.whole(level, v) && line(level, b.sign(0), 0).equals(v.name()), "Put back");
            level.setBlock(b.sign(5), Blocks.AIR.defaultBlockState(), 3);
            Projects.consider(level, v);
            h.assertTrue(!Projects.whole(level, v), "Only once a day");
            level.setDayTime(time + Days.DAY);
            Projects.consider(level, v);
            h.assertTrue(Projects.whole(level, v), "The next day it's mended again");
            h.succeed();
        } finally {
            level.setDayTime(time);
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "projects.rules")
    public static void noBoardWhereMobsMayNotChangeTheWorld(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        var rule = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean was = rule.get();
        try {
            rule.set(false, level.getServer());
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            h.assertTrue(v.board() == null && v.project() == null, "No board, no project");
            h.succeed();
        } finally {
            rule.set(was, level.getServer());
            clear(h, bell);
        }
    }
}
