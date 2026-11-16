package com.jayptucker.heroesevolved.energy;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.PowerBalance;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.combat.PlayerCombatData;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.time.TimeSlowService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The stamina rework: held powers, distance costs, the recovery pause and calm recovery. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class StaminaGameTests {
    /** A traveller at {@code tier}, in the player list (Still World looks its owner up there), on a stone floor. */
    private static ServerPlayer traveller(GameTestHelper h, int tier) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "stamina-test")) {
            @Override public void teleportTo(double x, double y, double z) { super.teleportTo(x, y, z); absMoveTo(x, y, z, getYRot(), getXRot()); }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 2))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                com.jayptucker.heroesevolved.intro.PowerIntroHistory.empty().with(ModAbilities.TIME_MANIPULATION_ID));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        p.setOnGround(true);
        list(p, true);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static void list(ServerPlayer p, boolean add) {
        PlayerList list = p.getServer().getPlayerList();
        try {
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
            var all = (List<ServerPlayer>) players.get(list);
            var index = (Map<UUID, ServerPlayer>) byId.get(list);
            if (add) { all.add(p); index.put(p.getUUID(), p); }
            else { all.remove(p); index.remove(p.getUUID()); }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void done(ServerPlayer p) {
        TimeStopService.stop(p);
        TimeSlowService.stop(p);
        StaminaDrain.forget(p);
        PlayerEnergyService.forget(p);
        list(p, false);
    }

    private static void spendTo(ServerPlayer p, double stamina) {
        PlayerEnergyService.drain(p, Math.max(0, PlayerEnergyService.getStamina(p) - stamina));
    }

    @GameTest(template = "rewind_empty", batch = "stamina.still_world", timeoutTicks = 240)
    public static void stillWorldLastsWhileStaminaPaysForIt(GameTestHelper h) {
        var p = traveller(h, 5);
        try {
            spendTo(p, 60);
            var result = AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false);
            h.assertTrue(result == AbilityUseResult.SUCCESS && TimeStopService.hasActiveField(p), "Still World should start, got " + result);
            double afterStart = PlayerEnergyService.getStamina(p);
            double rate = TimeStopService.drainPerSecond(5);
            h.assertTrue(Math.abs(rate - 8) < .01, "Level 15 holds time for 8 stamina a second, got " + rate);
            h.assertTrue(StaminaDrain.holdsRecovery(p), "Stopped time holds recovery");
            h.runAfterDelay(40, () -> {
                try {
                    double spent = afterStart - PlayerEnergyService.getStamina(p);
                    h.assertTrue(TimeStopService.hasActiveField(p), "Still World should still hold after two seconds");
                    h.assertTrue(spent > 12 && spent < 20, "Two seconds should take about 16, took " + spent);
                } catch (RuntimeException e) { done(p); throw e; }
            });
            // 60 less the start leaves enough for a few seconds; then the stamina gives out and time runs on.
            h.runAfterDelay(200, () -> {
                try {
                    h.assertTrue(!TimeStopService.hasActiveField(p), "Still World ends when the stamina runs out");
                    h.assertTrue(!StaminaDrain.active(p, TimeStopService.DRAIN), "The drain ends with it");
                    h.assertTrue(PlayerEnergyService.getStamina(p) < 1, "It spent the bar");
                    h.succeed();
                } finally { done(p); }
            });
        } catch (RuntimeException e) { done(p); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "stamina.press_again")
    public static void pressingStillWorldAgainLetsTimeRunOn(GameTestHelper h) {
        var p = traveller(h, 5);
        try {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.SUCCESS, "Starts");
            h.runAfterDelay(10, () -> {
                try {
                    double before = PlayerEnergyService.getStamina(p);
                    var again = AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false);
                    h.assertTrue(again == AbilityUseResult.SUCCESS && !TimeStopService.hasActiveField(p), "A second press ends it, got " + again);
                    h.assertTrue(PlayerEnergyService.getStamina(p) >= before - .01, "Ending it is free");
                    h.assertTrue(!StaminaDrain.active(p), "No drain is left running");
                    h.succeed();
                } finally { done(p); }
            });
        } catch (RuntimeException e) { done(p); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "stamina.between_seconds", timeoutTicks = 120)
    public static void betweenSecondsIsHeldToo(GameTestHelper h) {
        var p = traveller(h, 1);
        try {
            spendTo(p, 20);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS
                    && TimeSlowService.hasActiveField(p), "Between Seconds starts");
            // 20 less 10 to start leaves two seconds at 5 a second.
            h.runAfterDelay(70, () -> {
                try {
                    h.assertTrue(!TimeSlowService.hasActiveField(p), "Between Seconds ends when the stamina does");
                    h.succeed();
                } finally { done(p); }
            });
        } catch (RuntimeException e) { done(p); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "stamina.fold_distance")
    public static void foldingFurtherCostsMore(GameTestHelper h) {
        var p = traveller(h, 5);
        try {
            for (int x = 1; x <= 10; x++) for (int z = 1; z <= 10; z++)
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());
            var fold = ModAbilities.TIME_MANIPULATION.get().actions().get(AbilitySlot.PRIMARY);
            var context = new com.jayptucker.heroesevolved.ability.AbilityUseContext(p, ModAbilities.TIME_MANIPULATION_ID, 5, false);
            int near = travelTo(h, p, fold, context, new BlockPos(2, 1, 4));
            int far = travelTo(h, p, fold, context, new BlockPos(9, 1, 9));
            h.assertTrue(far > near, "A longer fold costs more: " + near + " vs " + far);
            double perBlock = PowerBalance.perBlock("time_blink", 0);
            h.assertTrue(perBlock > 0, "The table gives Fold Space a per-block cost");
            double before = PlayerEnergyService.getStamina(p);
            var result = AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false);
            double spent = before - PlayerEnergyService.getStamina(p);
            int base = PowerBalance.tuning("time_blink").energy();
            // Level 15 is fourteen levels past Fold Space's Level 1, so the 30% practice discount applies.
            int expected = com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(p, ModAbilities.TIME_MANIPULATION_ID, fold, base + far);
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityRanks.levelDiscount(p, fold) == .30, "A Level 1 move is at the cap by Level 15");
            h.assertTrue(result == AbilityUseResult.SUCCESS, "The fold should go, got " + result);
            h.assertTrue(Math.abs(spent - expected) < 1.01, "It charges the base and the distance, discounted: spent " + spent + ", expected " + expected);
            h.succeed();
        } finally { done(p); }
    }

    private static int travelTo(GameTestHelper h, ServerPlayer p, com.jayptucker.heroesevolved.ability.AbilityAction fold,
                                com.jayptucker.heroesevolved.ability.AbilityUseContext context, BlockPos target) {
        Vec3 aim = Vec3.atCenterOf(h.absolutePos(target)).add(0, .5, 0).subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
        p.setYRot(yaw); p.setXRot(pitch); p.setYHeadRot(yaw);
        return fold.travelCost(context);
    }

    @GameTest(template = "rewind_empty", batch = "stamina.recovery", timeoutTicks = 100)
    public static void recoveryPausesAfterAMoveAndDoublesWhenCalm(GameTestHelper h) {
        var p = traveller(h, 5);
        try {
            spendTo(p, 50);
            h.assertTrue(PlayerEnergyService.tryConsume(p, 10), "Pays for a move");
            double paused = PlayerEnergyService.getStamina(p);
            PlayerEnergyService.tick(p);
            h.assertTrue(Math.abs(PlayerEnergyService.getStamina(p) - paused) < 1e-6, "Nothing comes back right after a move");
            h.runAfterDelay(40, () -> {
                try {
                    double calmBefore = PlayerEnergyService.getStamina(p);
                    PlayerEnergyService.tick(p);
                    double calm = PlayerEnergyService.getStamina(p) - calmBefore;
                    double base = PlayerEnergyService.regenerationPerSecond(p) / 20;
                    h.assertTrue(Math.abs(calm - 2 * base) < 1e-6, "Out of combat recovery doubles: " + calm + " vs " + base);
                    p.setData(ModDataAttachments.PLAYER_COMBAT, new PlayerCombatData(p.level().getGameTime()));
                    double fightBefore = PlayerEnergyService.getStamina(p);
                    PlayerEnergyService.tick(p);
                    double fight = PlayerEnergyService.getStamina(p) - fightBefore;
                    h.assertTrue(Math.abs(fight - base) < 1e-6, "In a fight it is the base rate: " + fight);
                    // A held drain doesn't restart the pause.
                    h.assertTrue(PlayerEnergyService.drain(p, 2.5) && !PlayerEnergyService.paused(p, p.level().getGameTime()), "Drains don't pause recovery");
                    h.assertTrue(Math.abs(PlayerEnergyService.getStamina(p) - (fightBefore + fight - 2.5)) < 1e-6, "Drains take fractions");
                    h.succeed();
                } finally { done(p); }
            });
        } catch (RuntimeException e) { done(p); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "stamina.bar")
    public static void movesGetCheaperTheLongerYouHaveHadThem(GameTestHelper h) {
        var p = traveller(h, 1);
        try {
            var actions = ModAbilities.TIME_MANIPULATION.get().actions();
            var fold = actions.get(AbilitySlot.PRIMARY);      // opens at Level 1
            var still = actions.get(AbilitySlot.TERTIARY);    // opens at Level 6
            var id = ModAbilities.TIME_MANIPULATION_ID;
            PlayerProgressionService.setPowerLevel(p, 1);
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(p, id, fold, 100) == 100, "Full price on the level it opens");
            PlayerProgressionService.setPowerLevel(p, 6);
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(p, id, fold, 100) == 80, "4% a level: five levels on, 20% off");
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(p, id, still, 100) == 100, "A move that just opened is full price");
            PlayerProgressionService.setPowerLevel(p, 15);
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(p, id, fold, 100) == 70, "Capped at 30% off");
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(p, id, still, 100) == 70, "Nine levels on, at the cap too");
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(p, id, still, 0) == 0, "Free stays free");
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(p, id, still, 1) == 1, "Never below 1");
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "stamina.bar")
    public static void theBarGrowsWithPowerLevel(GameTestHelper h) {
        var p = traveller(h, 1);
        try {
            PlayerProgressionService.setPowerLevel(p, 1);
            h.assertTrue(PlayerEnergyService.getMaximumEnergy(p) == 100, "100 at Level 1");
            PlayerProgressionService.setPowerLevel(p, 10);
            h.assertTrue(PlayerEnergyService.getMaximumEnergy(p) == 136, "136 at Level 10");
            PlayerProgressionService.setPowerLevel(p, 15);
            h.assertTrue(PlayerEnergyService.getMaximumEnergy(p) == 156, "156 at Level 15");
            h.succeed();
        } finally { done(p); }
    }
}
