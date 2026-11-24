package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
            // A two-sided board needs a clearing; a crowded centre must not prevent the village from getting one.
            clear(h, bell);
            bell = meadow(h);
            for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++)
                if ((x != 0 || z != 0) && x * x + z * z <= 400)
                    for (int y = 0; y < 5; y++) level.setBlock(bell.offset(x, y, z), Blocks.STONE.defaultBlockState(), 2);
            v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            h.assertTrue(v.board() != null && Projects.whole(level, v), "It finds a clearing beyond the crowded centre");
            h.assertTrue(v.board().origin().distSqr(bell.atY(v.board().origin().getY())) > 18 * 18, "The board can search past the old eighteen-block limit");
            h.succeed();
        } finally {
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "projects.plans")
    public static void theReverseBoardShowsTheSavedFutureBuildAndQueuedHomes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        long time = level.getDayTime();
        try {
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            Projects.Project next = v.planned;
            h.assertTrue(next != null && !next.title().equals(v.project().title()), "A definite next design is announced");
            for (int i = 0; i < 6; i++) {
                BlockPos at = v.board().statusSign(i);
                h.assertTrue(level.getBlockState(at).getValue(WallSignBlock.FACING) == v.board().facing().getOpposite()
                        && level.getBlockState(at).canSurvive(level, at), "Reverse sign faces its reader and is supported");
                h.assertTrue(level.getBlockEntity(at) instanceof SignBlockEntity s && s.isWaxed(), "Schedule is waxed");
            }
            h.assertTrue(line(level, v.board().statusSign(1), 3).equals("0% gathered"), "The current build has a status");
            h.assertTrue(line(level, v.board().statusSign(3), 3).equals("Planned"), "The future build has a status");
            var saved = VillageMemory.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            var roundTrip = VillageMemory.load(saved, level.registryAccess()).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            h.assertTrue(saved.equals(roundTrip), "The announced plan and village survive saving and loading");
            // A paid order waits for the current project; it must immediately appear before the ordinary plan.
            v.project.have.put(Projects.Material.PLANKS, 1F);
            UUID owner = UUID.randomUUID();
            Homes.order(level, v, owner, "Alex");
            h.assertTrue(line(level, v.board().statusSign(3), 3).equals("Queued")
                    && line(level, v.board().statusSign(3), 1).equals("A home for Alex"), "An ordered home is queued on the board");
            h.assertTrue(line(level, v.board().statusSign(4), 3).equals("Planned"), "The saved village project still follows it");
            saved = VillageMemory.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            roundTrip = VillageMemory.load(saved, level.registryAccess()).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            h.assertTrue(saved.equals(roundTrip), "Queued owners' names and order survive reload too");
            v.orders.clear();
            Projects.fill(level, v);
            for (Rebuild.Site s : Rebuild.all(level)) if (s.growth() && s.bell().equals(bell)) Rebuild.hurry(level, s, 100_000);
            level.setDayTime(time + Days.DAY);
            Projects.consider(level, v);
            h.assertTrue(v.project() == next, "The project actually posted is the one the board promised");
            for (Component[] sign : Projects.schedule(level, v)) for (Component line : sign)
                h.assertTrue(Projects.width(line.getString()) <= Projects.SIGN_WIDTH, "Schedule text fits: " + line.getString());
            h.succeed();
        } finally { level.setDayTime(time); clear(h, bell); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "projects.paths")
    public static void aFinishedHomeGetsASavedPathAndItsCrewPavesIt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        long time = level.getDayTime();
        var rule = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean grief = rule.get();
        try {
            var v = VillageMemory.of(level, bell);
            Projects.consider(level, v);
            // This cottage's door is three blocks above its base, with only one entrance stair in the template.
            var raised = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_8");
            v.project = Projects.project(level, v, new Projects.Plan(raised, "A new house", 4, Projects.Tier.HAMLET), null);
            Projects.fill(level, v);
            Rebuild.Site house = Rebuild.all(level).stream().filter(s -> s.growth() && s.bell().equals(bell)).findFirst().orElseThrow();
            Rebuild.hurry(level, house, 100_000);
            Rebuild.Site path = Rebuild.all(level).stream().filter(s -> s.paving() && s.bell().equals(bell)).findFirst().orElse(null);
            h.assertTrue(path != null && path.size() > 4, "Finishing a house automatically schedules its connection");
            var saved = Rebuild.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            var restored = Rebuild.load(saved, level.registryAccess());
            h.assertTrue(restored.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()).equals(saved), "Paving and its progress survive reload");
            level.setDayTime(Days.DAY * (time / Days.DAY) + 6_000);
            path.startAt = Days.clock(level);
            rule.set(false, level.getServer());
            Rebuild.work(level, Rebuild.get(level), path);
            h.assertTrue(path.done() == 0, "Turning mobGriefing off pauses an existing paving job");
            rule.set(true, level.getServer());
            for (Villager x : level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(60))) x.discard();
            Rebuild.work(level, Rebuild.get(level), path);
            h.assertTrue(path.done() == 0, "No villagers means no paving");
            Villager builder = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            builder.moveTo(Vec3.atBottomCenterOf(path.center().above()));
            builder.setNoAi(true);
            builder.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
            level.addFreshEntity(builder);
            Rebuild.work(level, Rebuild.get(level), path);
            h.assertTrue(path.done() > 0, "A villager starts paving through the normal worker path");
            Rebuild.hurry(level, path, 100_000);
            h.assertTrue(v.connected.contains(house.home().origin().asLong()), "The finished connection is remembered");
            for (int i = 0; i < path.size(); i++)
                h.assertTrue(level.getBlockState(path.positions.get(i)).is(path.states.get(i).getBlock()), "Paved at " + path.positions.get(i));
            h.assertTrue(line(level, v.board().statusSign(2), 3).equals("Connected"), "The board reports the finished path");
            h.succeed();
        } finally { rule.set(grief, level.getServer()); level.setDayTime(time); clear(h, bell); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "projects.rotatedpaths")
    public static void pathsFindRotatedDoorsAndGoAroundWaterCropsAndPlayerWork(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        try {
            var v = VillageMemory.of(level, bell);
            var id = Projects.TOWN_HOUSES.getFirst();
            var template = level.getStructureManager().get(id).orElseThrow();
            for (var rotation : net.minecraft.world.level.block.Rotation.values()) {
                BlockPos origin = Growth.originFor(template, rotation, bell.offset(20, -1, -5));
                var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(rotation);
                template.placeInWorld(level, origin, origin, settings, level.random, 2);
                var home = new Projects.Home(id, origin, rotation);
                Rebuild.Site owned = new Rebuild.Site(bell, origin);
                owned.home = home;
                for (var b : Growth.blocks(level, template, origin, rotation))
                    if (!b.state().is(Blocks.STRUCTURE_VOID)) owned.positions.add(b.pos());
                Homes.built(level, v, owned, UUID.randomUUID());
                List<BlockPos> route = Paths.route(level, bell, home);
                h.assertTrue(route.size() > 5, "A route from the owned front door and porch at " + rotation);
                BlockPos obstacle = route.get(route.size() / 2);
                level.setBlock(obstacle, Blocks.FARMLAND.defaultBlockState(), 2);
                level.setBlock(obstacle.above(), Blocks.WHEAT.defaultBlockState(), 2);
                BlockPos water = route.get(route.size() / 2 + 1);
                level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
                BlockPos player = route.get(route.size() / 2 - 1);
                PlayerBuilt.mark(level, player);
                List<BlockPos> detour = Paths.route(level, bell, home);
                h.assertTrue(!detour.isEmpty() && !detour.contains(obstacle) && !detour.contains(water) && !detour.contains(player), "A continuous detour avoids all three");
                Rebuild.Site path = Paths.plan(level, v, home);
                h.assertTrue(path != null, "Paving can be planned");
                BlockPos late = path.positions.get(path.size() / 2);
                level.setBlock(late, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                PlayerBuilt.mark(level, late);
                Rebuild.hurry(level, path, 100_000);
                h.assertTrue(level.getBlockState(late).is(Blocks.DIAMOND_BLOCK) && level.getBlockState(player).is(Blocks.GRASS_BLOCK)
                        && level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(obstacle).is(Blocks.FARMLAND), "Existing and late player work, crops and water stay");
                h.assertTrue(!v.connected.contains(home.origin().asLong()), "An obstructed connection remains pending for another route");
                clear(h, bell);
                bell = meadow(h);
                v = VillageMemory.of(level, bell);
            }
            h.succeed();
        } finally { clear(h, bell); }
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
