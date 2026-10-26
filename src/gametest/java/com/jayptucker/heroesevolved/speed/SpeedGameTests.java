package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SpeedGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "speed-test")) {
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
        PlayerProgressionService.setLevel(p, 5); return p;
    }
    @GameTest(template="rewind_empty", batch="speed.stride")
    public static void sprintBoostIsControlledAndNeverStacksOrSurvivesRevocation(GameTestHelper h) {
        var p = player(h);
        try {
            double normal = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            SpeedService.toggle(p); SpeedService.tickRunner(p);
            h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) == normal, "Walking should keep normal precision");
            p.setSprinting(true); SpeedService.tickRunner(p);
            double fast = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(fast > normal * 4, "Sprinting must actually accelerate");
            SpeedService.tickRunner(p); h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) == fast, "Modifier must not stack every tick");
            p.setShiftKeyDown(true); SpeedService.tickRunner(p);
            h.assertTrue(!p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SpeedService.STRIDE), "Crouching must restore precision");
            p.setShiftKeyDown(false); p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()); SpeedService.tickRunner(p);
            h.assertTrue(!SpeedService.active(p) && !p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SpeedService.STRIDE), "Power removal must clear speed");
            h.succeed();
        } finally { SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.collision")
    public static void quickstepCannotTunnelThroughAWallOrResetAFall(GameTestHelper h) {
        var p = player(h);
        try {
            Vec3 from = p.position(); BlockPos wall = p.blockPosition().south(3);
            for (int y = 0; y < 3; y++) for (int x = -1; x <= 1; x++) h.getLevel().setBlockAndUpdate(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
            p.fallDistance = 18; p.setDeltaMovement(0, -1, 0);
            h.assertTrue(SpeedService.dash(p, 5), "Partial clear path should dash");
            h.assertTrue(p.getZ() < wall.getZ() && p.position().distanceToSqr(from) > 1, "Dash must stop before wall even with empty space beyond");
            h.assertTrue(p.fallDistance >= 18 && p.getDeltaMovement().y == -1, "Horizontal dash cannot erase a fall");
            h.succeed();
        } finally { SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.energy")
    public static void runningConsumesEnergyAndStopsWhenEmpty(GameTestHelper h) {
        var p = player(h);
        try {
            PlayerEnergyService.consumeUpTo(p, 10000); PlayerEnergyService.restore(p, 20); p.setSprinting(true); SpeedService.toggle(p);
            for (int i = 0; i < 20; i++) SpeedService.tickRunner(p);
            h.assertTrue(PlayerEnergyService.getEnergy(p) == 17, "Running must spend three stamina per second");
            PlayerEnergyService.consumeUpTo(p, 10000);
            for (int i = 0; i < 20; i++) SpeedService.tickRunner(p);
            h.assertTrue(!SpeedService.active(p) && !p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SpeedService.STRIDE), "Empty energy must end the boost"); h.succeed();
        } finally { SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.toggle_budget")
    public static void rapidlyTogglingCannotAvoidRunningCost(GameTestHelper h) {
        var p = player(h);
        try {
            PlayerEnergyService.consumeUpTo(p, 10000); PlayerEnergyService.restore(p, 40); p.setSprinting(true);
            SpeedService.toggle(p); SpeedService.tickRunner(p); SpeedService.stop(p);
            SpeedService.toggle(p); SpeedService.tickRunner(p);
            h.assertTrue(PlayerEnergyService.getEnergy(p) == 34, "Every fresh running interval must be paid upfront"); h.succeed();
        } finally { SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.time")
    public static void movingRunnerOutrunsTimeButStandingAndColdDoNot(GameTestHelper h) {
        var p = player(h); var clock = player(h);
        clock.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
        try {
            SpeedService.toggle(p); p.setSprinting(true); p.setPos(p.position().add(0, 0, .5)); SpeedService.tickRunner(p);
            TimeStopService.start(clock, 5);
            h.assertTrue(SpeedService.outrunsTime(p) && !TimeStopService.isStopped(p), "Moving speedster must outrun stopped time");
            p.setSprinting(false); h.assertTrue(TimeStopService.isStopped(p), "Standing speedster must still be vulnerable to time stop");
            TimeStopService.stop(clock); p.setSprinting(true); CryoService.freezeSolid(p, 80, clock);
            h.assertTrue(!SpeedService.outrunsTime(p), "Cold must defeat temporal speed resistance");
            SpeedService.tickRunner(p); h.assertTrue(!SpeedService.active(p), "Freezing must interrupt sprint boost"); h.succeed();
        } finally { CryoService.releaseEntity(p); TimeStopService.stop(clock); SpeedService.forget(p); SpeedService.forget(clock); }
    }
    @GameTest(template="rewind_empty", batch="speed.reflex")
    public static void reflexDodgeNeedsSpaceEnergyAndAProjectile(GameTestHelper h) {
        var p = player(h); var arrow = new Arrow(EntityType.ARROW, h.getLevel());
        try {
            var c = new AbilityUseContext(p, ModAbilities.SUPERSPEED_ID, 5, false);
            SpeedService.skill(c, 0); PlayerEnergyService.consumeUpTo(p, 10000); PlayerEnergyService.restore(p, 20);
            h.assertTrue(!SpeedService.dodge(p, p.damageSources().fall()), "Reflex must not provide generic invulnerability");
            Vec3 before = p.position(); var source = p.damageSources().arrow(arrow, null);
            h.assertTrue(SpeedService.dodge(p, source) && p.position().distanceToSqr(before) >= 2.25, "Projectile dodge must actually relocate the player");
            h.assertTrue(PlayerEnergyService.getEnergy(p) == 17 && !SpeedService.dodge(p, source), "Dodge spends energy and has a one-second interval"); h.succeed();
        } finally { arrow.discard(); SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.rescue")
    public static void rescueMovesBothBodiesAndMomentumStrikeDamagesItsTarget(GameTestHelper h) {
        var p = player(h); var villager = EntityType.VILLAGER.create(h.getLevel());
        villager.setPos(p.position().add(.9, 0, 0)); h.getLevel().addFreshEntity(villager);
        try {
            Vec3 from = p.position(), companion = villager.position();
            var c = new AbilityUseContext(p, ModAbilities.SUPERSPEED_ID, 5, false);
            h.assertTrue(SpeedService.skill(c, 2), "Contact rescue should activate");
            h.assertTrue(p.position().distanceToSqr(from) > 4 && villager.position().subtract(companion).distanceToSqr(p.position().subtract(from)) < .01, "Both travellers must move by the same safe displacement");
            villager.discard(); p.setPos(from);
            var enemy = EntityType.PILLAGER.create(h.getLevel()); enemy.setNoAi(true); enemy.setPos(from.add(0, 0, 5)); h.getLevel().addFreshEntity(enemy);
            try {
                float health = enemy.getHealth();
                h.assertTrue(SpeedService.skill(c, 1) && enemy.getHealth() < health && p.distanceToSqr(enemy) <= 9, "Momentum strike must close distance and damage a real mob");
            } finally { enemy.discard(); }
            h.succeed();
        } finally { villager.discard(); SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.flurry", timeoutTicks=30)
    public static void flurryDealsRepeatedHitsAndStopsOnIncapacitation(GameTestHelper h) {
        var p = player(h); var enemy = EntityType.PILLAGER.create(h.getLevel()); enemy.setNoAi(true); enemy.setPos(p.position().add(0, 0, 3)); h.getLevel().addFreshEntity(enemy);
        h.assertTrue(SpeedService.flurry(p, 2), "Aimed target should start a flurry");
        h.runAfterDelay(5, () -> {
            h.assertTrue(enemy.getHealth() <= enemy.getMaxHealth() - 6, "Several real hits should land over time");
            CryoService.freezeSolid(p, 80, null); float health = enemy.getHealth();
            h.runAfterDelay(5, () -> {
                try { h.assertTrue(enemy.getHealth() == health, "Frozen caster must stop attacking"); h.succeed(); }
                finally { CryoService.releaseEntity(p); enemy.discard(); SpeedService.forget(p); }
            });
        });
    }
    @GameTest(template="rewind_empty", batch="speed.momentum")
    public static void runningBuildsMomentumAndImpactConsumesIt(GameTestHelper h) {
        var p = player(h);
        try {
            SpeedService.toggle(p); p.setSprinting(true);
            for (int i = 0; i < 35; i++) { p.setPos(p.position().add(.2, 0, 0)); SpeedService.tickRunner(p); }
            h.assertTrue(SpeedService.momentum(p) == 100, "Real movement must build full momentum");
            h.assertTrue(SpeedService.consumeImpact(p, true) == 4 && SpeedService.momentum(p) == 0, "PvP bonus is capped and consumes charge");
            h.assertTrue(SpeedService.consumeImpact(p, true) == 0, "Cannot repeat free charged hits");
            SpeedService.redline(p);
            h.assertTrue(SpeedService.momentum(p) == 100 && SpeedService.consumeImpact(p, false) == 12, "Redline immediately primes a stronger PvE impact"); h.succeed();
        } finally { SpeedService.forget(p); }
    }
    @GameTest(template="rewind_empty", batch="speed.ownership")
    public static void superspeedUsesTheUniqueOwnershipRule(GameTestHelper h) {
        var data = new PowerOwnershipSavedData(); UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        h.assertTrue(data.claim(ModAbilities.SUPERSPEED_ID, first) && !data.claim(ModAbilities.SUPERSPEED_ID, second), "Only one owner can claim Superspeed"); h.succeed();
    }
}
