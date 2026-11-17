package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.radiation.MeltdownFieldGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Friction at a speedster's top speeds, and the velocity suit that keeps it off. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SpeedFrictionGameTests {
    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "speed.friction")
    public static void topSpeedsBurnWithoutTheSuit(GameTestHelper helper) {
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
        ServerPlayer bare = MeltdownFieldGameTests.player(helper, at, ModAbilities.SUPERSPEED_ID);
        ServerPlayer suited = MeltdownFieldGameTests.player(helper, at.add(2, 0, 0), ModAbilities.SUPERSPEED_ID);
        ServerPlayer jogger = MeltdownFieldGameTests.player(helper, at.add(-2, 0, 0), ModAbilities.SUPERSPEED_ID);
        try {
            MeltdownFieldGameTests.suit(suited, true);
            long now = helper.getLevel().getGameTime();
            for (int i = 0; i < 60; i++) {
                SpeedFriction.run(bare, SpeedFriction.FRICTION_PACE * 1.6, now + i);
                SpeedFriction.run(suited, SpeedFriction.FRICTION_PACE * 1.6, now + i);
                SpeedFriction.run(jogger, SpeedFriction.FRICTION_PACE * 0.7, now + i);
            }
            helper.assertTrue(SpeedFriction.heat(bare) >= SpeedFriction.BURN, "Three seconds flat out with no suit: burning (" + SpeedFriction.heat(bare) + ")");
            helper.assertTrue(bare.getHealth() < bare.getMaxHealth(), "And it hurts");
            helper.assertTrue(SpeedFriction.heat(suited) == 0, "The whole velocity suit keeps all of it off");
            helper.assertTrue(SpeedFriction.heat(jogger) == 0, "Ordinary speeds build no friction");
            for (int i = 0; i < 40; i++) SpeedFriction.run(bare, 0, now + 60 + i);
            helper.assertTrue(SpeedFriction.heat(bare) == 0, "Easing off cools them in a couple of seconds");
            SpeedFriction.burst(suited, SpeedFriction.LIGHTSPEED_HEAT);
            helper.assertTrue(SpeedFriction.heat(suited) == 0, "Even a Lightspeed streak, in the suit");
            helper.succeed();
        } finally {
            for (ServerPlayer p : new ServerPlayer[] {bare, suited, jogger}) {
                SpeedFriction.forget(p);
                p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "speed.heal")
    public static void aSpeedsterHealsALittleFasterAtHigherTiers(GameTestHelper helper) {
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
        ServerPlayer slow = MeltdownFieldGameTests.player(helper, at, ModAbilities.SUPERSPEED_ID);
        ServerPlayer fast = MeltdownFieldGameTests.player(helper, at.add(2, 0, 0), ModAbilities.SUPERSPEED_ID);
        ServerPlayer plain = MeltdownFieldGameTests.player(helper, at.add(-2, 0, 0), ModAbilities.CRYOKINESIS_ID);
        try {
            com.jayptucker.heroesevolved.progression.PlayerProgressionService.setLevel(slow, 1);
            com.jayptucker.heroesevolved.progression.PlayerProgressionService.setLevel(fast, 5);
            for (ServerPlayer p : new ServerPlayer[] {slow, fast, plain}) p.setHealth(4);
            long now = helper.getLevel().getGameTime() / 210 * 210 + 210; // a multiple of every heal period
            for (int i = 0; i < 420; i++) {
                SpeedFriction.recover(slow, now + i);
                SpeedFriction.recover(fast, now + i);
                if (com.jayptucker.heroesevolved.hazard.Suits.speedster(plain)) SpeedFriction.recover(plain, now + i);
            }
            float slowGain = slow.getHealth() - 4, fastGain = fast.getHealth() - 4;
            helper.assertTrue(slowGain > 0 && fastGain > slowGain, "Higher tiers heal faster: tier 1 +" + slowGain + ", tier 5 +" + fastGain);
            helper.assertTrue(fastGain <= 7.5F, "But not overpowered: a third of a heart a second at most (" + fastGain + " in 21 s)");
            helper.assertTrue(plain.getHealth() == 4, "Only speedsters");
            helper.assertTrue(fast.getFoodData().getExhaustionLevel() > 0 || fast.getFoodData().getFoodLevel() < 20, "It costs hunger");
            helper.succeed();
        } finally {
            for (ServerPlayer p : new ServerPlayer[] {slow, fast, plain}) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 160, batch = "speed.regrow")
    public static void aSpeedstersBurntSkinGrowsBackFaster(GameTestHelper helper) {
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
        ServerPlayer runner = MeltdownFieldGameTests.player(helper, at, ModAbilities.SUPERSPEED_ID);
        ServerPlayer plain = MeltdownFieldGameTests.player(helper, at.add(2, 0, 0), ModAbilities.CRYOKINESIS_ID);
        com.jayptucker.heroesevolved.progression.PlayerProgressionService.setLevel(runner, 5);
        com.jayptucker.heroesevolved.hazard.BodyBurn.atLeast(runner, 1);
        com.jayptucker.heroesevolved.hazard.BodyBurn.atLeast(plain, 1);
        helper.runAfterDelay(110, () -> {
            try {
                float fast = com.jayptucker.heroesevolved.hazard.BodyBurn.level(runner), slow = com.jayptucker.heroesevolved.hazard.BodyBurn.level(plain);
                helper.assertTrue(fast < 0.1F, "A tier-5 speedster's skin is nearly whole again within a few seconds: " + fast);
                helper.assertTrue(slow > 0.5F, "Anyone else's takes much longer: " + slow);
                helper.succeed();
            } finally {
                for (ServerPlayer p : new ServerPlayer[] {runner, plain}) {
                    com.jayptucker.heroesevolved.hazard.BodyBurn.heal(p);
                    p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
                }
            }
        });
    }
}
