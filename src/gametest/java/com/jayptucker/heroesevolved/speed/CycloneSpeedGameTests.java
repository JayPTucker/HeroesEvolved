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

    /** The shared lap schedule: faster settings lap faster, and a change of setting carries on from where the runner is. */
    @GameTest(template = "cryo_rework_arena", timeoutTicks = 40, batch = "speed.cyclone.laps")
    public static void theLapScheduleFollowsTheSetting(GameTestHelper h) {
        var slow = CycloneGeometry.Laps.start(0, 1);
        var fast = CycloneGeometry.Laps.start(0, 6);
        h.assertTrue(CycloneGeometry.lapSpeed(1) < CycloneGeometry.LAP_SPEED && CycloneGeometry.lapSpeed(3) == CycloneGeometry.LAP_SPEED
                && CycloneGeometry.lapSpeed(6) > 2 * CycloneGeometry.LAP_SPEED, "Setting 3 keeps the old pace; 1 is slower, 6 more than twice as fast");
        h.assertTrue(fast.angle(60) - fast.angle(40) > 3 * (slow.angle(60) - slow.angle(40)), "Flat out, the top setting laps over three times as fast");
        var changed = slow.toward(20, CycloneGeometry.lapSpeed(6));
        h.assertTrue(Math.abs(changed.angle(20) - slow.angle(20)) < 1e-4 && Math.abs(changed.omega(20) - slow.omega(20)) < 1e-4,
                "A change of setting starts from where the runner is and how fast they are going");
        h.assertTrue(changed.omega(21) - changed.omega(20) <= CycloneGeometry.ACCEL + 1e-4, "And speeds up at the shared rate, not at once");
        h.assertTrue(Math.abs(changed.omega(40) - CycloneGeometry.lapSpeed(6)) < 1e-4, "Until it reaches the new pace");
        h.succeed();
    }

    /** In play: the body really laps faster once the setting is scrolled up, on the schedule, without a jump. */
    @GameTest(template = "cryo_rework_arena", timeoutTicks = 120, batch = "speed.cyclone.laps")
    public static void scrollingUpRunsTheBodyFaster(GameTestHelper h) {
        ServerPlayer p = runner(h);
        Vec3 center = p.position();
        double[] last = {Double.NaN}, slowRun = {0}, fastRun = {0}, biggest = {0};
        try {
            h.assertTrue(SpeedKit.cyclone(p, 5, true), "The Cyclone starts");
            slowTo(p, 1);
        } catch (RuntimeException e) { done(p); throw e; }
        long[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            double angle = Math.atan2(p.getZ() - center.z, p.getX() - center.x);
            if (!Double.isNaN(last[0]) && tick[0] > 8) {
                double step = ((angle - last[0]) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
                biggest[0] = Math.max(biggest[0], step);
                if (tick[0] > 10 && tick[0] <= 20) slowRun[0] += step;
                if (tick[0] > 45 && tick[0] <= 55) fastRun[0] += step;
            }
            last[0] = angle;
            if (tick[0] == 22) for (int i = 0; i < 8; i++) SpeedKit.cycloneSpeed(p, 1);
        });
        h.runAfterDelay(60, () -> {
            try {
                h.assertTrue(SpeedKit.cycloneSpeed(p) == SpeedService.maximumSpeedStep(p), "Scrolled up to their fastest");
                h.assertTrue(SpeedKit.cycloneSpeed(p) > 0, "The laps were never blocked");
                h.assertTrue(slowRun[0] > 2.0 && slowRun[0] < 3.2, "At setting 1 the body laps slowly: " + slowRun[0] + " radians in ten ticks");
                h.assertTrue(fastRun[0] > 2.5 * slowRun[0], "Scrolled up, the body laps far faster: " + fastRun[0] + " against " + slowRun[0]);
                h.assertTrue(biggest[0] < 1.0, "Never more than the top pace in a tick, so it never jumps: " + biggest[0]);
                h.assertTrue(Math.abs(p.position().subtract(center).horizontalDistance() - SpeedKit.CYCLONE_RADIUS) < 0.05,
                        "And the body is on the ring, where it is drawn");
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
