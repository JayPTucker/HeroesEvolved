package com.jayptucker.heroesevolved.story.prologue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/** A street detour beside an unnecessary doorway, photographed with the real guide renderer. */
public final class TrelloGuidePreview {
    public static final Vec3 STAGE = new Vec3(2000.5, 80, 2000.5);
    public static void stage(ServerPlayer player) {
        var level = player.getServer().getLevel(com.jayptucker.heroesevolved.time.intro.TimeIntroService.DIMENSION);
        for (int x = 124; x <= 126; x++) for (int z = 124; z <= 126; z++) level.setChunkForced(x, z, true);
        BlockPos base = BlockPos.containing(STAGE);
        for (int x = -8; x <= 8; x++) for (int z = -2; z <= 13; z++)
            level.setBlock(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        player.teleportTo(level, STAGE.x, STAGE.y, STAGE.z, 0, 0);
        for (int x = -2; x <= 3; x++) for (int y = 0; y < 3; y++)
            level.setBlock(base.offset(x, y, 5), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        var door = Blocks.OAK_DOOR.defaultBlockState();
        level.setBlock(base.offset(0, 0, 5), door, 2);
        level.setBlock(base.offset(0, 1, 5), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        var guide = new LastDayGuide();
        guide.guide(level, player, new LastDayGuide.Goal(base.offset(0, 0, 9),
                BoundingBox.fromCorners(base.offset(-7, 0, -1), base.offset(7, 3, 12))));
    }
}
