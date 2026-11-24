package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.time.intro.city.CityAudit;
import com.jayptucker.heroesevolved.time.intro.city.CityCanvas;
import com.jayptucker.heroesevolved.time.intro.city.CityDraw;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class OdessaRuinsGameTests {
    @GameTest(template = "rewind_empty", batch = "odessa.audit")
    public static void aCanopyOrBeamHeldBeyondTheAuditEdgeIsNotFloating(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos at = new BlockPos(55500, 20, 55500);
        for (int cx = at.getX() >> 4; cx <= (at.getX() + 8) >> 4; cx++)
            for (int cz = at.getZ() >> 4; cz <= (at.getZ() + 8) >> 4; cz++) level.getChunk(cx, cz);
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 6; z++) level.setBlock(at.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2 | 16);
        for (int y = 1; y <= 5; y++) level.setBlock(at.offset(4, y, 3), Blocks.OAK_LOG.defaultBlockState(), 2 | 16);
        for (int x = 1; x <= 4; x++) level.setBlock(at.offset(x, 5, 3), Blocks.OAK_LOG.defaultBlockState(), 2 | 16);
        var audit = CityAudit.run(level, at.getX(), at.getY(), at.getZ(), at.getX() + 3, at.getY() + 6, at.getZ() + 6);
        h.assertTrue(audit.total() == 0, "The beam reaches the edge even though its first block does not: " + audit);
        // A truly detached piece wholly inside the patch must still be caught.
        level.setBlock(at.offset(1, 3, 1), Blocks.STONE.defaultBlockState(), 2 | 16);
        h.assertTrue(CityAudit.run(level, at.getX(), at.getY(), at.getZ(), at.getX() + 3, at.getY() + 6, at.getZ() + 6).total() == 1,
                "A detached internal block is still a real fault");
        h.succeed();
    }
    @GameTest(template = "rewind_empty", batch = "odessa.ruins", timeoutTicks = 400)
    public static void theRuinedCityKeepsItsStreetsJournalsAndScavengedState(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = new BlockPos(52000, -48, 52000);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("started", true);
        tag.putLong("origin", origin.asLong());
        OdessaRuins city = OdessaRuins.load(tag, level.registryAccess());
        h.assertTrue(!OdessaRuins.load(new CompoundTag(), level.registryAccess()).started(), "Existing worlds do not opt into terrain replacement");
        // The stair already exists. None of the new city's grading, collapse or settling may touch its lot.
        BlockPos pin = new BlockPos(origin.getX() + CityGrid.STAIR_X0, -61, origin.getZ() + CityGrid.STAIR_Z0);
        level.getChunkAt(pin);
        level.setBlock(pin, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState(), 2 | 16);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) city.tickBuild(level);
        // A server restart must carry on rather than re-roll the first streets and their loot.
        int cursor = city.completedBlocks();
        city = OdessaRuins.load(city.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(city.completedBlocks() == cursor, "The build cursor survives a restart");
        while (!city.complete()) city.tickBuild(level);
        h.assertTrue(level.getBlockState(pin).is(Blocks.CHISELED_TUFF_BRICKS), "The existing Primatech stair is untouched");
        h.assertTrue(city.completedBlocks() == 64, "All 64 original city blocks are represented");
        for (int z = CityGrid.MIN; z < CityGrid.PRIMATECH_LOT_Z0; z++) {
            int x = origin.getX() + 4, wz = origin.getZ() + z;
            BlockPos feet = new BlockPos(x, OdessaRuins.ground(level, x, wz), wz);
            h.assertTrue(!level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty(), "The avenue has ground at " + z);
            h.assertTrue(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty(), "The avenue stays walkable at " + z);
        }
        for (var journal : OdessaCaches.JOURNALS) {
            BlockPos pos = city.journals().get(journal.title());
            h.assertTrue(pos != null && Math.abs(pos.getX() - origin.getX() - journal.x()) <= 3
                    && Math.abs(pos.getZ() - origin.getZ() - journal.z()) <= 3, "The cache keeps the original building's position");
            h.assertTrue(level.getBlockEntity(pos) instanceof BarrelBlockEntity, journal.title() + " is in its original building");
            BarrelBlockEntity box = (BarrelBlockEntity) level.getBlockEntity(pos);
            ItemStack book = box.getItem(13);
            h.assertTrue(book.is(journal.unfinished() ? Items.WRITABLE_BOOK : Items.WRITTEN_BOOK), "The journal is a real readable book: " + journal.title());
            h.assertTrue(book.has(journal.unfinished() ? DataComponents.WRITABLE_BOOK_CONTENT : DataComponents.WRITTEN_BOOK_CONTENT), "The pages exist");
            h.assertTrue(level.getBlockState(pos.above()).isAir(), "The cache opens: " + journal.title());
        }
        var audit = CityAudit.run(level, origin.getX() + CityGrid.MIN, level.getMinBuildHeight(), origin.getZ() + CityGrid.MIN,
                origin.getX() + CityGrid.MAX, origin.getY() + OdessaDecay.TOP, origin.getZ() + CityGrid.MAX);
        System.out.println("ODESSA_RUINS_AUDIT built and audited in " + (System.currentTimeMillis() - start) + "ms\n" + audit);
        h.assertTrue(audit.total() == 0, "All ruins stand without unsupported blocks: " + audit);
        for (BlockPos p : BlockPos.betweenClosed(origin.getX() + CityGrid.MIN, -60, origin.getZ() + CityGrid.MIN,
                origin.getX() + CityGrid.MAX, origin.getY() + OdessaDecay.TOP, origin.getZ() + CityGrid.MAX))
            h.assertTrue(!RuinMaterials.valuable(level.getBlockState(p)), "No farmable resource block in the city at " + p + ": " + level.getBlockState(p));
        var first = OdessaCaches.JOURNALS.getFirst();
        BarrelBlockEntity looted = (BarrelBlockEntity) level.getBlockEntity(city.journals().get(first.title()));
        looted.clearContent();
        city = OdessaRuins.load(city.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        city.tickBuild(level);
        h.assertTrue(looted.isEmpty(), "A finished city never refills a scavenged container after a restart");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "odessa.terrain", timeoutTicks = 200)
    public static void hillsBuryBuildingsWithoutBeingFlattenedAndTreesStayRooted(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = new BlockPos(54000, -48, 54000);
        int bx = 3, bz = 2, x0 = CityGrid.originX(bx), z0 = CityGrid.originZ(bz);
        for (int cx = (origin.getX() + x0 - 8) >> 4; cx <= (origin.getX() + x0 + 40) >> 4; cx++)
            for (int cz = (origin.getZ() + z0 - 8) >> 4; cz <= (origin.getZ() + z0 + 40) >> 4; cz++) level.getChunk(cx, cz);
        var original = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int x = 11; x <= 28; x++) for (int z = 11; z <= 28; z++) {
            int top = -56 + Math.max(0, 16 - Math.abs(x - 24) - Math.abs(z - 24));
            for (int y = -60; y <= top; y++) {
                BlockPos p = new BlockPos(origin.getX() + x0 + x, y, origin.getZ() + z0 + z);
                var soil = (y == top ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState();
                level.setBlock(p, soil, 2 | 16);
                // The one supply cupboard is allowed its small opening in the sediment.
                if (Math.abs(x - 13) > 3 || Math.abs(z - 25) > 3) original.put(p, soil);
            }
        }
        BlockPos tree = new BlockPos(origin.getX() + x0 + 23, -41, origin.getZ() + z0 + 24);
        for (int y = 0; y < 6; y++) {
            level.setBlock(tree.above(y), Blocks.OAK_LOG.defaultBlockState(), 2 | 16);
            original.put(tree.above(y), Blocks.OAK_LOG.defaultBlockState());
        }
        int floor = OdessaRuins.buildBlock(level, origin, bx, bz);
        for (var cell : original.entrySet()) h.assertTrue(level.getBlockState(cell.getKey()).equals(cell.getValue()), "Native hill and roots remain at " + cell.getKey());
        h.assertTrue(floor < -44, "The building's lower storeys are buried in the hill rather than lifting it to a platform");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "odessa.plan")
    public static void planningTheCityNeverTouchesTheWorld(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = new BlockPos(4, CityGrid.STREET_Y + 2, 100);
        level.getChunkAt(pos);
        var old = level.getBlockState(pos);
        level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2 | 16);
        try {
            CityCanvas plan = CityCanvas.plan(level, -32, 96, 31, 127);
            CityDraw.draw(plan);
            h.assertTrue(!plan.planned().isEmpty() && !plan.plannedSigns().isEmpty(), "The city plan includes its blocks and signs");
            h.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK), "Planning neither clears columns nor finishes signs in the real world");
        } finally { level.setBlock(pos, old, 2 | 16); }
        h.succeed();
    }
}
