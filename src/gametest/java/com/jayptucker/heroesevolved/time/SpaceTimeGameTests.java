package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SpaceTimeGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "time-test")) {
            @Override public void teleportTo(net.minecraft.server.level.ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
            @Override public void teleportTo(double x, double y, double z) {
                super.teleportTo(x, y, z); absMoveTo(x, y, z, getYRot(), getXRot());
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
        PlayerProgressionService.setLevel(p, 5); p.setOnGround(true);
        h.getLevel().setBlockAndUpdate(p.blockPosition().below(), Blocks.STONE.defaultBlockState());
        return p;
    }
    private static void clear(ServerPlayer p) { SpaceTimeService.forget(p); TimeStopService.stop(p); TimeSlowService.stop(p); }

    @GameTest(template="rewind_empty", batch="time.redesign.anchor")
    public static void anchorPersistsAndRejectsObstructedArrival(GameTestHelper h) {
        var p = player(h);
        try {
            Vec3 home = p.position();
            h.assertTrue(SpaceTimeService.remember(p), "Safe grounded destination should be remembered");
            var anchor = p.getData(ModDataAttachments.SPACE_TIME_ANCHOR);
            var decoded = SpaceTimeService.Anchor.CODEC.parse(JsonOps.INSTANCE, SpaceTimeService.Anchor.CODEC.encodeStart(JsonOps.INSTANCE, anchor).getOrThrow()).getOrThrow();
            h.assertTrue(anchor.equals(decoded), "Anchor must survive serialization with dimension and orientation");
            p.setPos(home.add(3, 0, 0));
            h.getLevel().setBlockAndUpdate(BlockPos.containing(home), Blocks.STONE.defaultBlockState());
            h.assertTrue(!SpaceTimeService.recall(p) && p.position().distanceToSqr(home) > 1, "Must not teleport into a block");
            h.getLevel().setBlockAndUpdate(BlockPos.containing(home), Blocks.AIR.defaultBlockState());
            h.assertTrue(SpaceTimeService.recall(p) && p.position().distanceToSqr(home) < .01, "Clear anchor must teleport back");
            h.succeed();
        } finally { clear(p); }
    }
    @GameTest(template="rewind_empty", batch="time.redesign.history")
    public static void secondChanceRestoresOnlyPersonalStateAndConsumesHistory(GameTestHelper h) {
        var p = player(h);
        try {
            long now = h.getLevel().getServer().overworld().getGameTime(); Vec3 before = p.position();
            p.setHealth(18); SpaceTimeService.record(p, now - 100);
            p.setPos(before.add(3, 0, 0)); p.setHealth(5); p.setRemainingFireTicks(120); SpaceTimeService.record(p, now);
            var item = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3); p.getInventory().setItem(0, item);
            h.assertTrue(SpaceTimeService.secondChance(p), "Complete five second history should rewind");
            h.assertTrue(p.position().distanceToSqr(before) < .01 && p.getHealth() == 18 && !p.isOnFire(), "Position, health and fire must rewind");
            h.assertTrue(p.getInventory().getItem(0).getCount() == 3, "Personal rewind must not duplicate inventory");
            h.assertTrue(!SpaceTimeService.secondChance(p), "Cannot reuse consumed history"); h.succeed();
        } finally { clear(p); }
    }
    @GameTest(template="rewind_empty", batch="time.redesign.stasis")
    public static void selectiveStasisActuallyPausesTicksAndReleasesPreviousTarget(GameTestHelper h) {
        var p = player(h); var first = EntityType.PILLAGER.create(h.getLevel()); var second = EntityType.PILLAGER.create(h.getLevel());
        first.setPos(p.position().add(0, 0, 3)); second.setPos(p.position().add(0, 0, 5));
        h.getLevel().addFreshEntity(first); h.getLevel().addFreshEntity(second);
        try {
            SpaceTimeService.suspend(p, first, 100);
            h.assertTrue(TimeStopService.shouldStopEntityTick(first) && !TimeStopService.shouldStopEntityTick(second), "Stasis must pause the actual entity only");
            SpaceTimeService.suspend(p, second, 100);
            h.assertTrue(!TimeStopService.isStopped(first) && TimeStopService.isStopped(second), "One target per traveller");
            SpaceTimeService.forget(p); h.assertTrue(!TimeStopService.isStopped(second), "Logout/revocation must release stasis"); h.succeed();
        } finally { first.discard(); second.discard(); clear(p); }
    }
    @GameTest(template="rewind_empty", batch="time.redesign.companion")
    public static void contactCompanionCanMoveInStoppedTimeAndProjectilesRecover(GameTestHelper h) {
        var p = player(h); var companion = EntityType.VILLAGER.create(h.getLevel()); var enemy = EntityType.PILLAGER.create(h.getLevel());
        companion.setPos(p.position().add(.7, 0, 0)); enemy.setPos(p.position().add(0, 0, 4));
        h.getLevel().addFreshEntity(companion); h.getLevel().addFreshEntity(enemy);
        var shot = new Arrow(EntityType.ARROW, h.getLevel()); shot.setPos(p.position().add(0, 1, 3)); shot.setDeltaMovement(0, 0, -1);
        h.getLevel().addFreshEntity(shot);
        try {
            TimeStopService.start(p, 5, true);
            h.assertTrue(!TimeStopService.isStopped(p) && !TimeStopService.isStopped(companion) && TimeStopService.isStopped(enemy), "Owner and contact companion must move outside stopped time");
            h.assertTrue(TimeStopService.shouldStopEntityTick(shot) && shot.getDeltaMovement().lengthSqr() == 0, "Arrow must hang in place");
            TimeStopService.stop(p);
            h.assertTrue(shot.getDeltaMovement().z == -1 && !shot.isNoGravity(), "Release must restore original velocity and gravity"); h.succeed();
        } finally { companion.discard(); enemy.discard(); shot.discard(); clear(p); }
    }
    @GameTest(template="rewind_empty", batch="time.redesign.blink")
    public static void blinkWorksInOpenAirAndResetsFallMomentum(GameTestHelper h) {
        var p = player(h);
        try {
            p.setXRot(-70); p.setYRot(0); p.fallDistance = 30; p.setDeltaMovement(0, -2, 0); Vec3 from = p.position();
            var action = ModAbilities.TIME_MANIPULATION.get().actions().get(AbilitySlot.PRIMARY);
            h.assertTrue(action.activate(new AbilityUseContext(p, ModAbilities.TIME_MANIPULATION_ID, 5, false)) == AbilityActivationResult.SUCCESS, "Open-air Blink should succeed");
            h.assertTrue(p.position().distanceToSqr(from) > 4 && p.fallDistance == 0 && p.getDeltaMovement().lengthSqr() == 0, "Blink must move and safely cancel falling momentum"); h.succeed();
        } finally { clear(p); }
    }
    @GameTest(template="rewind_empty", batch="time.redesign.story")
    public static void storyFlagsAreIdempotentAndAvailableToFutureMissions(GameTestHelper h) {
        var p = player(h);
        TemporalStoryService.reach(p, TemporalStoryService.VISION); TemporalStoryService.reach(p, TemporalStoryService.VISION);
        TemporalStoryService.reach(p, TemporalStoryService.RESCUE);
        h.assertTrue(p.getData(ModDataAttachments.TEMPORAL_STORY) == 3, "Repeated triggers must not duplicate progress");
        h.assertTrue(TemporalStoryService.has(p, TemporalStoryService.RESCUE) && !TemporalStoryService.has(p, TemporalStoryService.TRAVEL), "Missions must query actual milestones");
        h.succeed(); clear(p);
    }
}
