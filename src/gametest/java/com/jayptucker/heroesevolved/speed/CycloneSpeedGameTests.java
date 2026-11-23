package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.radiation.MeltdownFieldGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A Cyclone's speed, set with the scroll wheel: a vacuum when slow, a tornado when fast and held, never round a reactor. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CycloneSpeedGameTests {
    private static ServerPlayer runner(GameTestHelper h) {
        ServerPlayer p = MeltdownFieldGameTests.player(h, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16, 2, 16))), ModAbilities.SUPERSPEED_ID);
        PlayerProgressionService.setLevel(p, 5);
        com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(p, 999);
        return p;
    }

    private static Pillager target(GameTestHelper h) {
        Pillager t = EntityType.PILLAGER.create(h.getLevel());
        t.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(19, 2, 16))));
        h.getLevel().addFreshEntity(t);
        return t;
    }

    private static void slowTo(ServerPlayer p, int speed) {
        for (int i = 0; i < 8 && SpeedKit.cycloneSpeed(p) > speed; i++) SpeedKit.cycloneSpeed(p, -1);
    }

    private static void done(ServerPlayer p, Entity... others) {
        SpeedKit.forget(p);
        for (Entity e : others) {
            if (e instanceof ServerPlayer other) other.serverLevel().removePlayerImmediately(other, Entity.RemovalReason.DISCARDED);
            else if (e != null) e.discard();
        }
        p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "cryo_rework_arena", timeoutTicks = 240, batch = "speed.cyclone.vacuum")
    public static void aSlowCycloneIsAVacuum(GameTestHelper h) {
        ServerPlayer p = runner(h);
        Pillager t = target(h);
        try {
            h.assertTrue(SpeedKit.cyclone(p, 5, true), "The Cyclone starts");
            h.assertTrue(SpeedKit.cycloneSpeed(p) == SpeedService.maximumSpeedStep(p), "It starts at their run setting (their fastest, with Accelerate off)");
            SpeedKit.cycloneSpeed(p, 1);
            h.assertTrue(SpeedKit.cycloneSpeed(p) == SpeedService.maximumSpeedStep(p), "Never faster than their fastest run setting");
            slowTo(p, 1);
            h.assertTrue(SpeedKit.cycloneSpeed(p) == 1 && SpeedKit.vacuum(p), "Scrolled down to 1: a vacuum");
        } catch (RuntimeException e) { done(p, t); throw e; }
        double y = t.getY();
        h.runAfterDelay(SpeedKit.VACUUM_TICKS - 5, () -> {
            try {
                h.assertTrue(!SpeedKit.tornado(p), "Held past the moment a fast one rises, a vacuum never does");
                h.assertTrue(t.getAirSupply() < t.getMaxAirSupply() - 50, "Whatever is caught can't breathe: air " + t.getAirSupply());
                h.assertTrue(t.getY() - y < 0.5, "And it isn't lifted");
                h.succeed();
            } finally { done(p, t); }
        });
    }

    @GameTest(template = "cryo_rework_arena", timeoutTicks = 240, batch = "speed.cyclone.tornado")
    public static void aFastHeldCycloneRisesAndSlowingItBringsItDown(GameTestHelper h) {
        ServerPlayer p = runner(h);
        try {
            h.assertTrue(SpeedKit.cyclone(p, 5, true), "The Cyclone starts, fast");
        } catch (RuntimeException e) { done(p); throw e; }
        h.runAfterDelay(SpeedKit.TORNADO_HOLD + 5, () -> {
            try {
                h.assertTrue(SpeedKit.tornado(p), "Fast and held: it rises into a tornado");
                slowTo(p, SpeedKit.VACUUM_UP_TO);
                h.assertTrue(!SpeedKit.tornado(p) && SpeedKit.vacuum(p), "Slowed before it's anchored, it comes down into a vacuum");
                h.succeed();
            } finally { done(p); }
        });
    }

    @GameTest(template = "cryo_rework_arena", timeoutTicks = 240, batch = "speed.cyclone.reactor")
    public static void noTornadoRoundAReactor(GameTestHelper h) {
        ServerPlayer p = runner(h);
        ServerPlayer reactor = MeltdownFieldGameTests.player(h, p.position().add(3, 0, 0), ModAbilities.RADIATION_ID);
        try {
            h.assertTrue(SpeedKit.cyclone(p, 5, true), "The Cyclone starts, fast");
        } catch (RuntimeException e) { done(p, reactor); throw e; }
        h.runAfterDelay(SpeedKit.TORNADO_HOLD + 5, () -> {
            try {
                h.assertTrue(!SpeedKit.tornado(p) && SpeedKit.cycloneSpeed(p) > 0, "A radioactive player in the ring keeps it a ring");
                h.succeed();
            } finally { done(p, reactor); }
        });
    }
}
