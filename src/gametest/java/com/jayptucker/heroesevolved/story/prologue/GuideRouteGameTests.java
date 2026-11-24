package com.jayptucker.heroesevolved.story.prologue;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class GuideRouteGameTests {
    @GameTest(template="cryo_rework_arena", batch="trello.guide")
    public static void threadStartsWithThePlayerAndAvoidsUnnecessaryDoors(GameTestHelper h) {
        var level = h.getLevel();
        for (int x=1; x<=6; x++) for (int y=2; y<=4; y++)
            level.setBlock(h.absolutePos(new BlockPos(x,y,4)), Blocks.STONE.defaultBlockState(), 2);
        var door = Blocks.OAK_DOOR.defaultBlockState();
        BlockPos at = h.absolutePos(new BlockPos(3,2,4));
        level.setBlock(at, door, 2);
        level.setBlock(at.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        var box = BoundingBox.fromCorners(h.absolutePos(new BlockPos(1,2,1)), h.absolutePos(new BlockPos(11,5,11)));
        Vec3 start = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3,2,3)));
        var route = new LastDayGuide().route(level, new LastDayGuide.Goal(h.absolutePos(new BlockPos(3,2,7)), box), start);
        h.assertTrue(route != null && !route.isEmpty() && route.getFirst().distanceToSqr(start) < .01,
                "The guide starts in the player's room, even when another room is much closer to the goal");
        for (Vec3 point : route) h.assertTrue(!BlockPos.containing(point).equals(at),
                "The open street is preferred to a shortcut through an unrelated door");
        h.succeed();
    }
}
