package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.events.SpeedEvents;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SpeedWaterFoodGameTests {
    private static class Runner extends FakePlayer {
        Runner(GameTestHelper h) { super(h.getLevel(), new GameProfile(UUID.randomUUID(), "water-food-test")); }
        void foodTick() { updateUsingItem(getUseItem()); }
    }
    private static Runner player(GameTestHelper h, int level) {
        var p = new Runner(h);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
        PlayerProgressionService.setLevel(p, level);
        SpeedService.toggle(p); p.setSprinting(true); SpeedService.tickRunner(p);
        return p;
    }
    @GameTest(template="rewind_empty", batch="speed.water_surface")
    public static void runningCrossesWaterWithoutBobbingOrSwimmingAndStopsWhenReleased(GameTestHelper h) {
        var p = player(h, 3); BlockPos water = p.blockPosition().below();
        try {
            for (int x = -1; x <= 6; x++) for (int z = -1; z <= 1; z++)
                h.getLevel().setBlock(water.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 2);
            double surface = water.getY() + 8 / 9.0 + .001;
            for (int i = 0; i < 16; i++) {
                p.setDeltaMovement(.25, -.16, 0);
                p.move(MoverType.SELF, p.getDeltaMovement());
                p.baseTick();
                h.assertTrue(Math.abs(p.getY() - surface) < .0001 && p.onGround(), "Repeated movement must stay supported at the water surface");
                h.assertTrue(!p.isInWater(), "Surface running must not enter swimming drag");
            }
            p.setSprinting(false); p.move(MoverType.SELF, new Vec3(0, -.2, 0));
            h.assertTrue(p.getY() < surface - .15, "Releasing sprint must let the runner sink immediately");
            h.succeed();
        } finally { SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.water_variants")
    public static void flowingWaterAndWaterPlantsSupportFeetButNotSubmergedSwimmers(GameTestHelper h) {
        var p = player(h, 3); BlockPos water = p.blockPosition().below();
        try {
            for (var state : new net.minecraft.world.level.block.state.BlockState[]{
                    Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3), Blocks.SEAGRASS.defaultBlockState()}) {
                h.getLevel().setBlock(water, state, 2);
                p.setPos(water.getX() + .5, water.getY() + 1, water.getZ() + .5); p.fallDistance = 0;
                p.setDeltaMovement(0, -.2, 0);
                double height = state.getFluidState().getHeight(h.getLevel(), water);
                p.move(MoverType.SELF, new Vec3(0, -.49, 0));
                h.assertTrue(Math.abs(p.getY() - water.getY() - height - .001) < .0001, "Use the actual water height, including water plants");
                p.setPos(water.getX() + .5, water.getY() + .1, water.getZ() + .5);
                h.assertTrue(state.getCollisionShape(h.getLevel(), water, CollisionContext.of(p)).isEmpty(), "No solid water around a submerged swimmer");
            }
            h.succeed();
        } finally { SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.water_limits")
    public static void waterRunningRespectsLevelCrouchingLongFallsLavaAndPowerRemoval(GameTestHelper h) {
        var p = player(h, 2); BlockPos water = p.blockPosition().below();
        var state = Blocks.WATER.defaultBlockState(); h.getLevel().setBlock(water, state, 2);
        try {
            h.assertTrue(state.getCollisionShape(h.getLevel(), water, CollisionContext.of(p)).isEmpty(), "Level two cannot run on water");
            PlayerProgressionService.setLevel(p, 3); SpeedService.tickRunner(p);
            h.assertTrue(!state.getCollisionShape(h.getLevel(), water, CollisionContext.of(p)).isEmpty(), "Level three gains surface support");
            p.setShiftKeyDown(true);
            h.assertTrue(state.getCollisionShape(h.getLevel(), water, CollisionContext.of(p)).isEmpty(), "Crouching lets the player dive");
            p.setShiftKeyDown(false); p.fallDistance = 10;
            h.assertTrue(state.getCollisionShape(h.getLevel(), water, CollisionContext.of(p)).isEmpty(), "Long falls still enter water normally");
            p.fallDistance = 0;
            h.assertTrue(Blocks.LAVA.defaultBlockState().getCollisionShape(h.getLevel(), water, CollisionContext.of(p)).isEmpty(), "No lava running");
            SpeedService.stop(p);
            h.assertTrue(state.getCollisionShape(h.getLevel(), water, CollisionContext.of(p)).isEmpty(), "Disabling Accelerate removes support");
            h.succeed();
        } finally { SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.fast_food")
    public static void acceleratedFoodFinishesInEightTicksAndConsumesExactlyOneItem(GameTestHelper h) {
        var p = player(h, 1);
        try {
            p.setSprinting(false); p.getFoodData().setFoodLevel(10);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
            p.startUsingItem(InteractionHand.MAIN_HAND);
            for (int i = 0; i < 7; i++) p.foodTick();
            h.assertTrue(p.getMainHandItem().getCount() == 2 && p.isUsingItem(), "Meal must not finish early");
            p.foodTick();
            h.assertTrue(p.getMainHandItem().getCount() == 1 && !p.isUsingItem() && p.getFoodData().getFoodLevel() == 14,
                    "Eight accelerated ticks consume one apple and apply normal hunger exactly once");
            for (Item item : new Item[]{Items.BOW, Items.POTION, Items.MILK_BUCKET}) {
                var event = new LivingEntityUseItemEvent.Tick(p, new ItemStack(item), 20); SpeedEvents.eat(event);
                h.assertTrue(event.getDuration() == 20, "Non-food items keep their normal use duration");
            }
            p.startUsingItem(InteractionHand.MAIN_HAND); p.foodTick();
            SpeedService.stop(p); int remaining = p.getUseItemRemainingTicks(); p.foodTick();
            h.assertTrue(p.getUseItemRemainingTicks() == remaining - 1, "Stopping Accelerate restores normal eating speed mid-meal");
            h.succeed();
        } finally { p.stopUsingItem(); SpeedService.forget(p); }
    }
}
