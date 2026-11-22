package com.jayptucker.heroesevolved.village;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Homes rebuilt bigger and towns: the town houses stand with every pane joined and every stair the shape the game would
 * give it, a turned house's blocks turn with it, tiers grow with beds and buildings, a small home is rebuilt bigger and
 * then as a two-storey town house, and a village's own pieces are read for their templates.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VillageUpgradeGameTests {
    private static final int R = 44;

    private static final class Listener extends FakePlayer {
        final List<String> heard = new ArrayList<>();
        Listener(GameTestHelper h) { super(h.getLevel(), new GameProfile(UUID.randomUUID(), "Watcher")); }
        @Override public void sendSystemMessage(Component c) { heard.add(c.getString()); }
    }

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
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R + 4, 30, R + 4))) v.discard();
        for (Rebuild.Site s : Rebuild.all(level)) if (s.bell().equals(bell)) Rebuild.drop(level, s);
        BlockPos center = bell.below();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-R - 2, 0, -R - 2), center.offset(R + 2, 24, R + 2)))
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(bell).inflate(R + 4, 30, R + 4))) item.discard();
        VillageMemory.forget(level, bell);
    }

    /** What's wrong with the panes, stairs and doors of a building at {@code positions}: one line each. */
    static List<String> faults(ServerLevel level, List<BlockPos> positions) {
        List<String> out = new ArrayList<>();
        for (BlockPos p : positions) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof IronBarsBlock) {
                int arms = 0;
                for (var prop : List.of(CrossCollisionBlock.NORTH, CrossCollisionBlock.SOUTH, CrossCollisionBlock.EAST, CrossCollisionBlock.WEST))
                    if (s.getValue(prop)) arms++;
                if (arms == 0) out.add("a pane joined to nothing at " + p.toShortString());
            }
            if (Rebuild.settles(s) && Block.updateFromNeighbourShapes(s, level, p) != s)
                out.add("not settled: " + s + " at " + p.toShortString());
            if (s.getBlock() instanceof DoorBlock) {
                boolean lower = s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                BlockState other = level.getBlockState(lower ? p.above() : p.below());
                if (!(other.getBlock() instanceof DoorBlock) || other.getValue(DoorBlock.FACING) != s.getValue(DoorBlock.FACING))
                    out.add("a door with one half at " + p.toShortString());
            }
        }
        return out;
    }

    private static List<BlockPos> box(BlockPos center, int r, int up) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, up, r))) out.add(p.immutable());
        return out;
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "upgrade.townhouses")
    public static void theTownHousesStandWithEveryPaneJoinedAndEveryStairSettled(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        try {
            var v = VillageMemory.of(level, bell);
            for (ResourceLocation id : Projects.TOWN_HOUSES) {
                Rebuild.Site site = Growth.grow(level, v, id);
                h.assertTrue(site != null && site.home() != null, "Room for " + id);
                Rebuild.hurry(level, site, 100_000);
                List<String> faults = faults(level, box(site.center(), 9, 15));
                h.assertTrue(faults.isEmpty(), id + " stands clean: " + faults);
                int beds = 0, upstairs = 0;
                for (BlockPos p : box(site.center(), 9, 15)) {
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
                        beds++;
                        if (p.getY() >= site.home().origin().getY() + 4) upstairs++;
                    }
                }
                h.assertTrue(beds == 2 && upstairs == 2, id + ": two beds, upstairs: " + beds + "/" + upstairs);
                h.assertTrue(Projects.standing(level, v, site.home()) > 0.95F, id + " is all there: " + Projects.standing(level, v, site.home()));
            }
            h.assertTrue(v.homes.size() == 2 && v.houses() == 2, "Both remembered as its homes");
            h.succeed();
        } finally {
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "upgrade.turned")
    public static void aTurnedHouseIsTurnedBlockByBlockAsVanillaTurnsIt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var template = level.getStructureManager().get(ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
        BlockPos ours = h.absolutePos(new BlockPos(2, 0, 2)).atY(300), theirs = ours.offset(0, 20, 0);
        try {
            for (Rotation r : Rotation.values()) {
                var blocks = Growth.blocks(level, template, ours, r);
                template.placeInWorld(level, theirs, theirs, new StructurePlaceSettings().setRotation(r).setIgnoreEntities(true)
                        .addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE), level.random, 2);
                int checked = 0;
                for (var b : blocks) {
                    BlockState mine = b.state(), vanilla = level.getBlockState(b.pos().offset(0, 20, 0));
                    if (mine.isAir() || mine.is(Blocks.STRUCTURE_VOID)) continue;
                    for (var prop : List.of(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.AXIS))
                        if (mine.hasProperty(prop) && vanilla.hasProperty(prop))
                            h.assertTrue(mine.getValue(prop) == vanilla.getValue(prop), r + ": " + mine + " where vanilla has " + vanilla);
                    checked++;
                }
                h.assertTrue(checked > 30, r + ": checked " + checked);
                for (BlockPos p : BlockPos.betweenClosed(theirs.offset(-16, -1, -16), theirs.offset(16, 14, 16))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
            h.succeed();
        } finally {
            for (BlockPos p : BlockPos.betweenClosed(theirs.offset(-16, -1, -16), theirs.offset(16, 14, 16))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "upgrade.tiers")
    public static void aPlaceGrowsFromHamletToVillageToTown(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener watcher = new Listener(h);
        try {
            watcher.setPos(Vec3.atBottomCenterOf(bell.offset(3, 0, 3)));
            watcher.setGameMode(GameType.SURVIVAL);
            level.addNewPlayer(watcher);
            var v = VillageMemory.of(level, bell);
            h.assertTrue(Projects.tier(level, v) == Projects.Tier.HAMLET, "A bell and nothing else is a hamlet");
            Projects.grown(level, v);
            h.assertTrue(watcher.heard.isEmpty(), "Nobody's told what it already was");
            h.assertTrue(Projects.options(level, v, Projects.Tier.HAMLET).stream().noneMatch(o -> o.old() != null || o.plan().tier() != Projects.Tier.HAMLET),
                    "A hamlet builds houses and farms only");
            v.houses = 4;
            Projects.grown(level, v);
            h.assertTrue(Projects.tier(level, v) == Projects.Tier.VILLAGE && watcher.heard.stream().anyMatch(s -> s.contains("has grown into a village")),
                    "Four buildings make it a village: " + watcher.heard);
            v.houses = 10;
            Projects.grown(level, v);
            h.assertTrue(Projects.tier(level, v) == Projects.Tier.TOWN && watcher.heard.stream().anyMatch(s -> s.contains("has grown into a town")),
                    "Ten make it a town: " + watcher.heard);
            h.assertTrue(Projects.options(level, v, Projects.Tier.TOWN).stream().anyMatch(o -> o.plan().title().equals("A town house")),
                    "A town builds town houses");
            h.succeed();
        } finally {
            clear(h, bell, watcher);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "upgrade.rebuild")
    public static void aSmallHomeIsRebuiltBiggerThenWithASecondStorey(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        try {
            var v = VillageMemory.of(level, bell);
            Rebuild.Site first = Growth.grow(level, v);
            Rebuild.hurry(level, first, 100_000);
            Projects.Home small = first.home();
            h.assertTrue(small != null && small.small() && Projects.homes(level, v).contains(small), "A small home to rebuild");
            // A village rebuilds it as a bigger house of its kind.
            v.houses = 4;
            var options = Projects.options(level, v, Projects.Tier.VILLAGE);
            var upgrade = options.stream().filter(o -> o.old() != null).findFirst().orElse(null);
            h.assertTrue(upgrade != null && upgrade.plan().title().equals(Projects.UPGRADE) && upgrade.old().equals(small)
                    && upgrade.plan().id().getPath().contains("medium_house"), "A village offers to rebuild it bigger: " + upgrade);
            v.project = Projects.project(level, v, upgrade.plan(), small);
            h.assertTrue(v.project.need(Projects.Material.PLANKS) > 0, "It asks for what the bigger house needs, less what the old one gives back");
            Projects.fill(level, v);
            h.assertTrue(v.project.stage() == Projects.Stage.BUILDING, "Planned: " + v.project.stage() + (v.project.noRoom ? " (no room)" : ""));
            Rebuild.Site site = Rebuild.all(level).stream().filter(s -> s.bell().equals(bell) && s.growth()).findFirst().orElseThrow();
            h.assertTrue(site.clearing() > 20 && small.equals(site.replaces()), "It takes the old house down first: " + site.clearing());
            Rebuild.hurry(level, site, 100_000);
            Projects.Home medium = site.home();
            h.assertTrue(v.project.stage() == Projects.Stage.DONE && Projects.standing(level, v, small) < 0.5F
                    && Projects.standing(level, v, medium) > 0.9F, "The old one gone, the new one up: " + Projects.standing(level, v, small)
                    + " / " + Projects.standing(level, v, medium));
            h.assertTrue(v.replaced.contains(small.origin().asLong()) && v.homes.contains(medium) && !v.homes.contains(small), "Remembered as rebuilt");
            List<String> faults = faults(level, box(site.center(), 10, 14));
            h.assertTrue(faults.isEmpty(), "The bigger house stands clean: " + faults);
            // A town rebuilds that one again with a second storey.
            v.houses = 10;
            var storey = Projects.options(level, v, Projects.Tier.TOWN).stream().filter(o -> o.old() != null).findFirst().orElse(null);
            h.assertTrue(storey != null && storey.plan().title().equals(Projects.STOREY) && Projects.TOWN_HOUSES.contains(storey.plan().id()),
                    "A town offers a second storey: " + storey);
            v.project = Projects.project(level, v, storey.plan(), storey.old());
            Projects.fill(level, v);
            Rebuild.Site again = Rebuild.all(level).stream().filter(s -> s.bell().equals(bell) && s.growth()).findFirst().orElse(null);
            h.assertTrue(again != null, "Room for the town house where the home stood (" + v.project.stage() + ")");
            Rebuild.hurry(level, again, 100_000);
            h.assertTrue(Projects.standing(level, v, again.home()) > 0.9F, "Two storeys now");
            Direction front = again.home().rotation().rotate(Direction.NORTH);
            Direction was = storey.old().rotation().rotate(Growth.front(level.getStructureManager().get(storey.old().template()).orElseThrow(), storey.old().template()));
            h.assertTrue(front == was, "Facing the street the way the old home did: " + front + " / " + was);
            List<String> faults2 = faults(level, box(again.center(), 10, 16));
            h.assertTrue(faults2.isEmpty(), "The town house stands clean: " + faults2);
            h.succeed();
        } finally {
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "upgrade.pieces")
    public static void aVillagesOwnPiecesNameTheirTemplates(GameTestHelper h) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
        var legacy = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement.legacy(id.toString())
                .apply(net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID);
        h.assertTrue(id.equals(Projects.templateOf(legacy)), "Read the template of a village piece: " + Projects.templateOf(legacy));
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "upgrade.styles")
    public static void townHousesTakeTheirVillagesColours(GameTestHelper h) {
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState taiga = Styles.apply(stair, "taiga");
        h.assertTrue(taiga.is(Blocks.SPRUCE_STAIRS) && taiga.getValue(StairBlock.FACING) == Direction.EAST, "Spruce in the taiga, facing as drawn: " + taiga);
        h.assertTrue(Styles.apply(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), "savanna").is(Blocks.STRIPPED_ACACIA_LOG), "Acacia on the savanna");
        h.assertTrue(Styles.apply(Blocks.COBBLESTONE.defaultBlockState(), "desert").is(Blocks.SANDSTONE)
                && Styles.apply(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), "desert").is(Blocks.CHISELED_SANDSTONE), "Sandstone in the desert");
        h.assertTrue(Styles.apply(Blocks.DARK_OAK_STAIRS.defaultBlockState(), "taiga").is(Blocks.DARK_OAK_STAIRS), "Dark oak isn't oak");
        h.assertTrue(Styles.apply(stair, "plains") == stair, "Plains as drawn");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "upgrade.everyfit")
    public static void everyHomeCanBeRebuiltAsEveryBiggerDesignOnOpenGround(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        try {
            var v = VillageMemory.of(level, bell);
            List<ResourceLocation> from = new ArrayList<>(), mediums = new ArrayList<>();
            for (var plan : Projects.plans(level, "plains")) {
                if (plan.id().getPath().contains("small_house")) from.add(plan.id());
                if (plan.id().getPath().contains("medium_house")) { from.add(plan.id()); mediums.add(plan.id()); }
            }
            List<String> failed = new ArrayList<>();
            int tried = 0;
            BlockPos spot = bell.offset(-6, -1, 14);
            for (ResourceLocation old : from)
                for (Rotation r : Rotation.values()) {
                    List<ResourceLocation> to = new ArrayList<>(Projects.TOWN_HOUSES);
                    if (old.getPath().contains("small_house")) to.addAll(mediums);
                    for (ResourceLocation next : to) {
                        tried++;
                        Rebuild.Site site = Growth.upgrade(level, v, new Projects.Home(old, spot, r), next);
                        if (site == null) failed.add(old.getPath().substring(old.getPath().lastIndexOf('/') + 1) + " " + r + " -> " + next.getPath());
                        else Rebuild.drop(level, site);
                    }
                }
            h.assertTrue(failed.isEmpty(), failed.size() + " of " + tried + " don't fit: " + failed);
            h.succeed();
        } finally {
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "upgrade.exported")
    public static void theTownHouseTemplatesMatchTheirBlueprints(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(2, 0, 2)).atY(300), b = a.offset(0, 0, 24);
        try {
            for (int n = 1; n <= 2; n++) {
                var template = level.getStructureManager().get(Projects.TOWN_HOUSES.get(n - 1)).orElseThrow();
                com.jayptucker.heroesevolved.structure.Blueprint.load("village_town_house_" + n).place(level, a, Rotation.NONE);
                template.placeInWorld(level, b, b, new StructurePlaceSettings(), level.random, 2);
                var size = template.getSize();
                int differ = 0;
                String first = null;
                for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
                    BlockState x = level.getBlockState(a.offset(p)), y = level.getBlockState(b.offset(p));
                    if (x.getBlock() != y.getBlock()) { differ++; if (first == null) first = p.toShortString() + ": " + x + " vs " + y; }
                }
                h.assertTrue(differ == 0, "town_house_" + n + ".nbt is out of date with its blueprint (export it again with to-nbt): "
                        + differ + " differ, first " + first);
                for (BlockPos p : BlockPos.betweenClosed(a.offset(-1, -1, -1), b.offset(size.getX(), size.getY(), size.getZ())))
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
            h.succeed();
        } finally {
            for (BlockPos p : BlockPos.betweenClosed(a.offset(-1, -1, -1), b.offset(14, 16, 14))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
