package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.time.intro.city.CityAudit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What is left of Primatech over the bunker: still the building, nothing hanging, and a clear walk from the stair to the doors. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PrimatechRuinGameTests {
    private static final int FLOOR = 31, HEIGHT = 50;

    @GameTest(template = "rewind_empty", batch = "bunker.ward", timeoutTicks = 200)
    public static void primatechsRuinStandsAndItsWayOutIsClear(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = new BlockPos(48000, 20, 48000);
        for (int cx = (origin.getX() + PrimatechRuin.LOT_X0 - 2) >> 4; cx <= (origin.getX() + PrimatechRuin.LOT_X1 + 2) >> 4; cx++)
            for (int cz = (origin.getZ() + PrimatechRuin.LOT_Z0 - 2) >> 4; cz <= (origin.getZ() + PrimatechRuin.LOT_Z1 + 2) >> 4; cz++)
                level.getChunk(cx, cz);
        for (int x = PrimatechRuin.LOT_X0 - 1; x <= PrimatechRuin.LOT_X1 + 1; x++)
            for (int z = PrimatechRuin.LOT_Z0 - 1; z <= PrimatechRuin.LOT_Z1 + 1; z++) {
                for (int y = 0; y <= HEIGHT; y++) level.setBlock(origin.offset(x, FLOOR + y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
                level.setBlock(origin.offset(x, FLOOR, z), Blocks.STONE_BRICKS.defaultBlockState(), 2 | 16);
            }
        // The usual case: the top flight runs along the front wall and climbs west.
        PrimatechRuin.raise(level, origin, FLOOR, -7, -5, 3, 12, 2);

        // From the head of the stair, along the wall, through the doors and out over the forecourt.
        for (int x = 2; x >= -2; x--) walkable(h, level, origin, x, -6);
        for (int z = -7; z <= -5; z++) walkable(h, level, origin, -2, z);
        for (int x : new int[] {-2, -1}) {
            BlockState door = level.getBlockState(origin.offset(x, FLOOR + 1, -8));
            h.assertTrue(door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.OPEN), "Primatech's doors stand open at x " + x);
        }
        for (int z = -9; z >= -12; z--) walkable(h, level, origin, -2, z);

        // Still the building: its name over the doors, and two wings standing well above the heap between them.
        h.assertTrue(level.getBlockEntity(origin.offset(-1, FLOOR + 5, -9)) instanceof SignBlockEntity sign
                && sign.getFrontText().getMessage(1, false).getString().equals("PRIMATECH"), "The name is still over the doors");
        h.assertTrue(tallest(level, origin, PrimatechRuin.WALL_X0, PrimatechRuin.WALL_X0 + 12) >= 26, "The west wing still stands");
        h.assertTrue(tallest(level, origin, PrimatechRuin.WALL_X1 - 10, PrimatechRuin.WALL_X1) >= 22, "The east wing still stands");
        // A respawn lands on top of whatever is within ten blocks of the origin.
        int middle = tallest(level, origin, -10, 10);
        h.assertTrue(middle <= 20, "The middle, where people respawn, has fallen: it stands " + middle);

        var audit = CityAudit.run(level, origin.getX() + PrimatechRuin.LOT_X0 - 1, origin.getY() + FLOOR, origin.getZ() + PrimatechRuin.LOT_Z0 - 1,
                origin.getX() + PrimatechRuin.LOT_X1 + 1, origin.getY() + FLOOR + HEIGHT, origin.getZ() + PrimatechRuin.LOT_Z1 + 1);
        h.assertTrue(audit.total() == 0, "Nothing in the ruin hangs or can't stay: " + audit);
        for (BlockPos p : BlockPos.betweenClosed(origin.getX() + PrimatechRuin.LOT_X0, origin.getY() + FLOOR, origin.getZ() + PrimatechRuin.LOT_Z0,
                origin.getX() + PrimatechRuin.LOT_X1, origin.getY() + FLOOR + HEIGHT, origin.getZ() + PrimatechRuin.LOT_Z1))
            h.assertTrue(!RuinMaterials.valuable(level.getBlockState(p)), "Primatech has no farmable resource blocks: " + p);
        h.succeed();
    }

    private static void walkable(GameTestHelper h, ServerLevel level, BlockPos origin, int x, int z) {
        BlockPos floor = origin.offset(x, FLOOR, z);
        h.assertTrue(!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty(), "There is floor at " + x + ", " + z);
        for (int up = 1; up <= 2; up++) {
            BlockPos cell = floor.above(up);
            h.assertTrue(level.getBlockState(cell).getCollisionShape(level, cell).isEmpty(), "The way out is clear at " + x + ", " + up + ", " + z);
        }
    }

    /** The highest block of the building's own concrete, above the floor, between two x. */
    private static int tallest(ServerLevel level, BlockPos origin, int x0, int x1) {
        int best = 0;
        for (int x = x0; x <= x1; x++)
            for (int z = -8; z <= 8; z++)
                for (int y = HEIGHT; y > best; y--) {
                    BlockState state = level.getBlockState(origin.offset(x, FLOOR + y, z));
                    if (state.is(Blocks.WHITE_CONCRETE) || state.is(Blocks.LIGHT_GRAY_CONCRETE) || state.is(Blocks.POLISHED_ANDESITE)) { best = y; break; }
                }
        return best;
    }
}
