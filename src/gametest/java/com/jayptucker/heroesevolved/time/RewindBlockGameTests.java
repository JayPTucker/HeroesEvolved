package com.jayptucker.heroesevolved.time;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class RewindBlockGameTests {
    private static ServerPlayer watcher(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "rewind-test"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        player.setInvulnerable(true);
        helper.getLevel().addNewPlayer(player);
        TimeRewindBlocks.track(player);
        return player;
    }

    private static void remove(ServerPlayer player) {
        TimeRewindService.forget(player);
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    private static String explain(ServerPlayer player, AABB box, TimeRewindBlocks.Plan plan) {
        StringBuilder result = new StringBuilder("border=" + player.serverLevel().getWorldBorder().isWithinBounds(box)
                + ", entities=" + player.serverLevel().getEntityCollisions(player, box).size() + ", box=" + box);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX - 1, box.minY - 1, box.minZ - 1),
                BlockPos.containing(box.maxX + 1, box.maxY + 1, box.maxZ + 1))) {
            var state = plan.blocks().containsKey(pos) ? plan.blocks().get(pos).getState() : player.serverLevel().getBlockState(pos);
            var shape = state.getCollisionShape(player.serverLevel(), pos).move(pos.getX(), pos.getY(), pos.getZ());
            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(net.minecraft.world.phys.shapes.Shapes.create(box),
                    shape, net.minecraft.world.phys.shapes.BooleanOp.AND)) result.append("; collision=").append(pos).append(" ").append(state);
        }
        return result.toString();
    }

    @GameTest(template = "rewind_empty")
    public static void restoresMiningAndExplosion(GameTestHelper helper) {
        helper.setBlock(3, 1, 3, Blocks.STONE);
        helper.setBlock(7, 2, 7, Blocks.GLASS);
        ServerPlayer player = watcher(helper);
        try {
            long before = TimeRewindBlocks.track(player);
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)), false, player);
            Vec3 blast = Vec3.atCenterOf(helper.absolutePos(new BlockPos(7, 2, 7)));
            helper.getLevel().explode(null, blast.x, blast.y, blast.z, 2.0F, Level.ExplosionInteraction.TNT);
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, 2, 7));
            TimeRewindBlocks.restore(player, TimeRewindBlocks.plan(player, before));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 3));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(7, 2, 7));
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty")
    public static void repeatedEditsRespectCutoff(GameTestHelper helper) {
        helper.setBlock(3, 1, 3, Blocks.STONE);
        ServerPlayer player = watcher(helper);
        try {
            long first = TimeRewindBlocks.track(player);
            helper.setBlock(3, 1, 3, Blocks.DIRT);
            long middle = TimeRewindBlocks.track(player);
            helper.setBlock(3, 1, 3, Blocks.AIR);
            TimeRewindBlocks.restore(player, TimeRewindBlocks.plan(player, middle));
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(3, 1, 3));
            TimeRewindBlocks.restore(player, TimeRewindBlocks.plan(player, first));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 3));
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty")
    public static void removesPlacedBlocksAndChecksFutureTerrain(GameTestHelper helper) {
        ServerPlayer player = watcher(helper);
        try {
            long before = TimeRewindBlocks.track(player);
            BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            AABB destination = player.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(player.position()));
            helper.assertTrue(!helper.getLevel().noCollision(player, destination), "Current block must obstruct destination");
            TimeRewindBlocks.Plan plan = TimeRewindBlocks.plan(player, before);
            helper.assertTrue(TimeRewindBlocks.canOccupy(player, destination, plan), "Removed placement must not block rewind: " + explain(player, destination, plan));
            TimeRewindBlocks.restore(player, plan);
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 2, 4));
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty")
    public static void restoresContainerContentsWithoutLooseCopies(GameTestHelper helper) {
        helper.setBlock(3, 1, 3, Blocks.CHEST);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        ((ChestBlockEntity) helper.getLevel().getBlockEntity(pos)).setItem(0, new ItemStack(Items.DIAMOND, 3));
        ServerPlayer player = watcher(helper);
        try {
            long before = TimeRewindBlocks.track(player);
            helper.getLevel().destroyBlock(pos, false, player);
            TimeRewindBlocks.restore(player, TimeRewindBlocks.plan(player, before));
            helper.assertBlockPresent(Blocks.CHEST, new BlockPos(3, 1, 3));
            ChestBlockEntity restored = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(restored.getItem(0).is(Items.DIAMOND) && restored.getItem(0).getCount() == 3,
                    "Container NBT must restore its contents");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).isEmpty(),
                    "Restored contents must not also remain as loose drops");
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty")
    public static void ignoresNoOpAndDistantChanges(GameTestHelper helper) {
        helper.setBlock(3, 1, 3, Blocks.STONE);
        ServerPlayer player = watcher(helper);
        try {
            long before = TimeRewindBlocks.track(player);
            helper.setBlock(3, 1, 3, Blocks.STONE);
            BlockPos far = player.blockPosition().offset(64, 0, 0);
            helper.getLevel().setBlockAndUpdate(far, Blocks.GLASS.defaultBlockState());
            helper.assertTrue(TimeRewindBlocks.plan(player, before).blocks().isEmpty(),
                    "No-op and out-of-range writes must not enter the journal");
            helper.getLevel().setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty")
    public static void fullFifteenSecondRewindRestoresTerrain(GameTestHelper helper) {
        helper.setBlock(3, 1, 3, Blocks.STONE);
        ServerPlayer player = watcher(helper);
        PlayerAbilityService.replaceWithAbility(player, ModAbilities.TIME_MANIPULATION_ID);
        try {
            long now = helper.getLevel().getServer().overworld().getGameTime();
            long before = TimeRewindBlocks.track(player);
            TimeRewindService.record(player, now - 300);
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)), false, player);
            TimeRewindService.record(player, now);
            var plan = TimeRewindBlocks.plan(player, before);
            helper.assertTrue(TimeRewindBlocks.canOccupy(player, player.getBoundingBox(), plan), explain(player, player.getBoundingBox(), plan));
            helper.assertTrue(TimeRewindService.rewind(player), "Full recorded rewind must succeed");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 3));
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty")
    public static void overlappingRewindsPreserveBothTimelines(GameTestHelper helper) {
        helper.setBlock(3, 1, 3, Blocks.STONE);
        ServerPlayer first = watcher(helper);
        ServerPlayer second = watcher(helper);
        try {
            long before = TimeRewindBlocks.track(first);
            helper.setBlock(3, 1, 3, Blocks.AIR);
            long after = TimeRewindBlocks.track(second);
            TimeRewindBlocks.restore(first, TimeRewindBlocks.plan(first, before));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 3));
            // The second player's target was after the break, before the first rewind.
            TimeRewindBlocks.restore(second, TimeRewindBlocks.plan(second, after));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
            helper.succeed();
        } finally { remove(first); remove(second); }
    }

    @GameTest(template = "rewind_empty")
    public static void restoresFireFluidAndBlockProperties(GameTestHelper helper) {
        var original = Blocks.OAK_STAIRS.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                net.minecraft.core.Direction.EAST);
        helper.setBlock(new BlockPos(3, 2, 3), original);
        helper.setBlock(5, 2, 5, Blocks.OAK_PLANKS);
        ServerPlayer player = watcher(helper);
        try {
            long before = TimeRewindBlocks.track(player);
            helper.setBlock(3, 2, 3, Blocks.WATER);
            helper.setBlock(5, 2, 5, Blocks.FIRE);
            TimeRewindBlocks.restore(player, TimeRewindBlocks.plan(player, before));
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 2, 3))) == original,
                    "Original block properties must be restored");
            helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 2, 5));
            helper.succeed();
        } finally { remove(player); }
    }
}
