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
}
