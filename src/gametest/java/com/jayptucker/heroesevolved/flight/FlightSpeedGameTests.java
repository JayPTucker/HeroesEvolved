package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class FlightSpeedGameTests {
    private static ServerPlayer player(GameTestHelper helper, int level) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wheel-flight"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 40, 3))));
        player.setOnGround(false);
        player.setData(ModDataAttachments.PLAYER_ABILITIES.get(), PlayerAbilityData.empty()
                .assignDormant(ModAbilities.FLIGHT_ID).unlock(ModAbilities.FLIGHT_ID));
        PlayerProgressionService.setLevel(player, level);
        FlightService.toggleFlight(player);
        return player;
    }
    private static PlayerFlightData data(ServerPlayer player) {
        return player.getData(ModDataAttachments.PLAYER_FLIGHT.get());
    }

    @GameTest(template = "rewind_empty", batch = "flight.wheel_levels")
    public static void wheelSpeedRespectsEveryLevelAndCannotSkipTiers(GameTestHelper helper) {
        ServerPlayer player = player(helper, 1);
        try {
            FlightService.setForwardInput(player, true);
            for (int level = 1; level <= 5; level++) {
                PlayerProgressionService.setLevel(player, level);
                int before = data(player).speedStep();
                FlightService.changeSpeed(player, Integer.MAX_VALUE);
                helper.assertTrue(data(player).speedStep() == Math.min(before + 1, level - 1),
                        "A forged large scroll amount must move only one unlocked step");
                for (int i = 0; i < 6; i++) FlightService.changeSpeed(player, 1);
                helper.assertTrue(data(player).speedStep() == level - 1, "Each level must cap its speed setting on the server");
                helper.assertTrue(data(player).visualPoseActive() == (level > 1), "Forward input alone uses selected speed without Ctrl");
            }
            helper.succeed();
        } finally { FlightService.stopForPowerRemoval(player); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.wheel_braking")
    public static void scrollingDownBrakesAndLandingResetsSelection(GameTestHelper helper) {
        ServerPlayer player = player(helper, 5);
        try {
            FlightService.setForwardInput(player, true);
            FlightService.changeSpeed(player, 1);
            FlightService.changeSpeed(player, 1);
            FlightService.tick(player);
            player.setDeltaMovement(new Vec3(0, 0, 2));
            FlightService.changeSpeed(player, -1);
            FlightService.tick(player);
            helper.assertTrue(player.getDeltaMovement().z < 1.9, "Selecting a lower speed must actually decelerate");
            FlightService.setForwardInput(player, false);
            helper.assertTrue(!data(player).visualPoseActive() && data(player).speedStep() == 1,
                    "Releasing Forward stops fast flight but retains the selected speed");
            FlightService.setForwardInput(player, true);
            FlightService.changeSpeed(player, -1);
            helper.assertTrue(data(player).speedStep() == 0 && !data(player).visualPoseActive()
                    && player.getDeltaMovement().length() < .6, "Scrolling to normal speed must brake into upright flight");
            player.setOnGround(true);
            FlightService.tick(player);
            helper.assertTrue(!data(player).sessionActive() && data(player).speedStep() == 0, "Landing must reset session speed");
            helper.succeed();
        } finally { FlightService.stopForPowerRemoval(player); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.wheel_validation")
    public static void invalidScrollCannotEnableFlightOrBypassControlModes(GameTestHelper helper) {
        ServerPlayer player = player(helper, 5);
        try {
            player.setOnGround(true); FlightService.changeSpeed(player, 1);
            helper.assertTrue(data(player).speedStep() == 0, "Ground scrolling must not affect flight speed");
            player.setOnGround(false);
            player.setData(ModDataAttachments.PLAYER_ABILITIES.get(), PlayerAbilityData.empty().assignDormant(ModAbilities.FLIGHT_ID));
            FlightService.changeSpeed(player, 1);
            helper.assertTrue(data(player).speedStep() == 0, "Dormant Flight cannot select faster speeds");
            player.setData(ModDataAttachments.PLAYER_ABILITIES.get(), PlayerAbilityData.empty());
            FlightService.changeSpeed(player, 1);
            helper.assertTrue(data(player).speedStep() == 0, "A non-Flight player cannot use the speed packet");
            FlightService.stopForPowerRemoval(player);
            FlightService.changeSpeed(player, 1);
            helper.assertTrue(!data(player).flightEnabled(), "Scrolling cannot start a flight session");
            helper.succeed();
        } finally { FlightService.stopForPowerRemoval(player); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.wheel_downgrade")
    public static void levelReductionClampsSpeedAndCycloneKeepsItsOwnMovement(GameTestHelper helper) {
        ServerPlayer player = player(helper, 5);
        try {
            FlightService.setForwardInput(player, true);
            for (int i = 0; i < 4; i++) FlightService.changeSpeed(player, 1);
            PlayerProgressionService.setLevel(player, 2);
            FlightService.tick(player);
            helper.assertTrue(data(player).speedStep() == 1, "Level reductions must revoke locked speeds immediately");
            PlayerProgressionService.setLevel(player, 5);
            FlightService.startCyclone(player);
            FlightService.changeSpeed(player, 1);
            FlightService.setForwardInput(player, true);
            helper.assertTrue(data(player).speedStep() == 0 && !data(player).movingForward()
                    && FlightService.isCycloneActive(player), "Wheel/forward input must not override cyclone movement");
            helper.succeed();
        } finally { FlightService.stopForPowerRemoval(player); }
    }
}
