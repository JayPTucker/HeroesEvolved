package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import com.jayptucker.heroesevolved.structure.RuinWater;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ScatteredRuinsGameTests {
    @GameTest(template = "rewind_empty", batch = "scattered.ruins")
    public static void rebuiltRuinsKeepTheirLootAndCannotBecomeResourceQuarries(GameTestHelper h) {
        for (String kind : new String[]{"burnt_house", "collapsed_house", "bennet_house", "isaac_studio"}) {
            var bp = Blueprint.load("ruins_" + kind);
            h.assertTrue(bp.statesMatching(RuinMaterials::valuable).isEmpty(), "Only ordinary construction materials in " + kind);
            var template = h.getLevel().getServer().getStructureManager().get(id("ruins/" + kind)).orElseThrow();
            var chests = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
            String loot = kind.equals("bennet_house") || kind.equals("isaac_studio") ? kind : "ruins_house";
            h.assertTrue(chests.size() == 1 && chests.getFirst().nbt() != null
                    && chests.getFirst().nbt().getString("LootTable").equals("heroesevolved:chests/" + loot), "The exported chest keeps its story loot: " + kind);
        }
        var sets = h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var ordinary = (RandomSpreadStructurePlacement) sets.get(id("ruins")).placement();
        var story = (RandomSpreadStructurePlacement) sets.get(id("story_ruins")).placement();
        h.assertTrue(ordinary.spacing() >= 48 && story.spacing() >= ordinary.spacing() * 2, "Discoveries stay sparse and the named houses stay rarer");
        var processors = h.getLevel().registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).get(id("ancient_ruins")).list();
        BlockPos soil = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(soil, Blocks.DIRT.defaultBlockState(), 2 | 16);
        var source = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(soil, Blocks.AIR.defaultBlockState(), null);
        h.assertTrue(processors.getFirst().processBlock(h.getLevel(), BlockPos.ZERO, BlockPos.ZERO, source, source, new StructurePlaceSettings()) == null,
                "The configured processor prevents air from carving native soil out of a hillside");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "scattered.ocean", timeoutTicks = 200)
    public static void oceanRuinsAreFloodedAndOnlySalvageFloats(GameTestHelper h) {
        var level = h.getLevel();
        String[] kinds = {"burnt_house", "collapsed_house", "bennet_house", "isaac_studio"};
        for (int i = 0; i < kinds.length; i++) {
            BlockPos at = new BlockPos(58000 + i * 64, 100, 58000);
            for (int cx = (at.getX() - 2) >> 4; cx <= (at.getX() + 24) >> 4; cx++)
                for (int cz = (at.getZ() - 2) >> 4; cz <= (at.getZ() + 24) >> 4; cz++) level.getChunk(cx, cz);
            for (int x = -1; x <= 22; x++) for (int z = -1; z <= 22; z++) {
                level.setBlock(at.offset(x, 0, z), Blocks.SAND.defaultBlockState(), 2 | 16);
                for (int y = 1; y <= 24; y++) level.setBlock(at.offset(x, y, z), Blocks.WATER.defaultBlockState(), 2 | 16);
            }
            h.assertTrue(OdessaRuins.ground(level, at.getX() + 10, at.getZ() + 10) == 101,
                    "Odessa's terrain sampler finds the seabed, not the sea surface");
            var template = level.getServer().getStructureManager().get(id("ruins/" + kinds[i])).orElseThrow();
            // Real worldgen places a piece once per chunk, using a full-height clipping box.
            for (int cx = at.getX() >> 4; cx <= (at.getX() + 20) >> 4; cx++) for (int cz = at.getZ() >> 4; cz <= (at.getZ() + 20) >> 4; cz++) {
                var settings = wetSettings(h).setBoundingBox(new BoundingBox(cx * 16, -64, cz * 16, cx * 16 + 15, 319, cz * 16 + 15));
                template.placeInWorld(level, at, at, settings, level.random, 2 | 16);
            }
            h.assertTrue(RuinWater.water(level, at.offset(9, 2, 9)), "The room has water, not an air pocket: " + kinds[i]);
            int boards = 0, chests = 0;
            for (BlockPos p : BlockPos.betweenClosed(at, at.offset(20, 25, 20))) {
                var state = level.getBlockState(p);
                h.assertTrue(!state.is(net.minecraft.tags.BlockTags.LEAVES), "Submerged woodland has decayed instead of remaining a living canopy");
                if (state.is(Blocks.CHEST)) chests++;
                if (p.getY() == 125 && !state.isAir()) {
                    h.assertTrue(state.is(net.minecraft.tags.BlockTags.WOODEN_SLABS), "Only wooden debris reaches the surface: " + state);
                    boards++;
                }
            }
            h.assertTrue(boards > 0 && chests == 1, "Surface boards and exactly one original submerged loot chest: " + kinds[i]);
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("ruins_" + kinds[i]));
            var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var json = net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC.encodeStart(ops, structure).getOrThrow().getAsJsonObject();
            h.assertTrue(json.get("project_start_to_heightmap").getAsString().equals("OCEAN_FLOOR_WG"), "Live structure registration projects onto the seabed");
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "scattered.barrels")
    public static void risingBarrelKeepsItsJournalAndDoesNotDuplicate(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos source = new BlockPos(58500, 160, 58500), at = source.offset(8, -20, 0);
        level.getChunkAt(source); level.getChunkAt(at);
        OdessaCaches.place(level, source, OdessaCaches.JOURNALS.getFirst());
        level.setBlock(source.above(5), Blocks.SPRUCE_SLAB.defaultBlockState(), 2 | 16);
        var original = ((net.minecraft.world.level.block.entity.BarrelBlockEntity) level.getBlockEntity(source)).getItem(13).copy();
        var template = new StructureTemplate();
        template.fillFromWorld(level, source, new net.minecraft.core.Vec3i(1, 6, 1), false, Blocks.STRUCTURE_VOID);
        for (int y = 0; y <= 12; y++) level.setBlock(at.above(y), Blocks.WATER.defaultBlockState(), 2 | 16);
        template.placeInWorld(level, at, at, wetSettings(h), level.random, 2 | 16);
        BlockPos top = at.above(13);
        h.assertTrue(level.getBlockState(at).is(Blocks.WATER) && level.getBlockEntity(at) == null, "No duplicate container stays below");
        var barrel = (net.minecraft.world.level.block.entity.BarrelBlockEntity) level.getBlockEntity(top);
        h.assertTrue(barrel != null && net.minecraft.world.item.ItemStack.isSameItemSameComponents(original, barrel.getItem(13)), "The floating barrel retains the exact written journal");
        OdessaCaches.supplies(level, source, 103L);
        template.fillFromWorld(level, source, new net.minecraft.core.Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
        BlockPos supplies = at.offset(3, 0, 0);
        level.getChunkAt(supplies);
        for (int y = 0; y <= 12; y++) level.setBlock(supplies.above(y), Blocks.WATER.defaultBlockState(), 2 | 16);
        template.placeInWorld(level, supplies, supplies, wetSettings(h), level.random, 2 | 16);
        var saved = level.getBlockEntity(supplies.above(13)).saveWithFullMetadata(level.registryAccess());
        // Vanilla assigns every newly placed template container a fresh seed, whether it moves or stays put.
        h.assertTrue(saved.getString("LootTable").equals("heroesevolved:chests/odessa_supplies") && saved.contains("LootTableSeed"),
                "Unopened floating barrels retain their lazy loot table and normal generated seed");
        level.setBlock(at.above(6), Blocks.ICE.defaultBlockState(), 2 | 16);
        h.assertTrue(RuinWater.surface(level, at).equals(at), "Salvage does not punch through ice or a ceiling");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "scattered.odessa.ocean", timeoutTicks = 200)
    public static void oceanCityDistrictSinksWhileItsJournalBarrelsFloat(GameTestHelper h) {
        var level = h.getLevel();
        var journal = OdessaCaches.JOURNALS.get(1);
        int bx = com.jayptucker.heroesevolved.time.intro.city.CityGrid.bx(journal.x());
        int bz = com.jayptucker.heroesevolved.time.intro.city.CityGrid.bz(journal.z());
        int x0 = com.jayptucker.heroesevolved.time.intro.city.CityGrid.originX(bx);
        int z0 = com.jayptucker.heroesevolved.time.intro.city.CityGrid.originZ(bz);
        BlockPos corner = new BlockPos(58720, 100, 58720), origin = corner.offset(-x0, 0, -z0);
        for (int cx = (corner.getX() - 8) >> 4; cx <= (corner.getX() + 40) >> 4; cx++)
            for (int cz = (corner.getZ() - 8) >> 4; cz <= (corner.getZ() + 40) >> 4; cz++) level.getChunk(cx, cz);
        for (int x = -4; x < 36; x++) for (int z = -4; z < 36; z++) {
            level.setBlock(corner.offset(x, 0, z), Blocks.SAND.defaultBlockState(), 2 | 16);
            for (int y = 1; y <= 40; y++) level.setBlock(corner.offset(x, y, z), Blocks.WATER.defaultBlockState(), 2 | 16);
        }
        int floor = OdessaRuins.buildBlock(level, origin, bx, bz);
        h.assertTrue(floor <= 101, "The district uses seabed height rather than the ocean surface");
        int barrels = 0;
        boolean foundJournal = false;
        for (BlockPos p : BlockPos.betweenClosed(corner.offset(0, 41, 0), corner.offset(31, 41, 31))) {
            var state = level.getBlockState(p);
            h.assertTrue(state.isAir() || state.is(Blocks.BARREL) || state.is(net.minecraft.tags.BlockTags.WOODEN_SLABS), "No district or stone pedestal at the surface");
            if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity box) {
                barrels++;
                h.assertTrue(level.getBlockState(p.below()).is(Blocks.WATER), "The barrel floats directly on water");
                if (box.getItem(13).is(net.minecraft.world.item.Items.WRITABLE_BOOK)) foundJournal = true;
            }
        }
        h.assertTrue(barrels > 0 && foundJournal, "The market's unfinished civilian journal is in a floating barrel");
        h.succeed();
    }

    private static StructurePlaceSettings wetSettings(GameTestHelper h) {
        var settings = new StructurePlaceSettings();
        h.getLevel().registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).get(id("ancient_ruins")).list().forEach(settings::addProcessor);
        return settings;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("heroesevolved", path); }
}
