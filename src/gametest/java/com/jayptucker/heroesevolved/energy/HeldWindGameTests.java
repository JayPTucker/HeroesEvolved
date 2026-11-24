package com.jayptucker.heroesevolved.energy;

import com.mojang.authlib.GameProfile;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.PowerBalance;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedKit;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.speed.SpeedWindArms;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class HeldWindGameTests {
    private static ServerPlayer player(GameTestHelper h, ResourceLocation power) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "held-wind"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.PLAYER_UNLIMITED_ENERGY, false);
        PlayerProgressionService.setPowerLevel(p, 15);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static void paidSecond(GameTestHelper h, ServerPlayer p, double expected) {
        double before = PlayerEnergyService.getStamina(p);
        for (int i = 0; i < 20; i++) StaminaDrain.tick(h.getLevel().getServer());
        h.assertTrue(Math.abs(before - PlayerEnergyService.getStamina(p) - expected) < .0001,
                "Twenty tick payments spend exactly " + expected + " stamina, including the first second");
        h.assertTrue(!StaminaDrain.holdsRecovery(p), "Wind upkeep allows normal stamina recovery");
    }

    private static void empty(ServerPlayer p) {
        PlayerEnergyService.drain(p, PlayerEnergyService.getStamina(p) - .01);
        StaminaDrain.tick(p.getServer());
    }

    private static void cleanup(ServerPlayer p) {
        SpeedService.forget(p); FlightService.stopForPowerRemoval(p); HydroService.leave(p);
        StaminaDrain.forget(p); PlayerEnergyService.forget(p); MasteryService.forgetPlayer(p.getUUID());
    }

    @GameTest(template = "rewind_empty", batch = "heldwind.first")
    public static void cyclonePaysImmediatelyAndStopsWhenTheNextTickCannotBePaid(GameTestHelper h) {
        var p = player(h, ModAbilities.SUPERSPEED_ID);
        try {
            h.assertTrue(SpeedKit.cyclone(p, 3, true), "The held Cyclone starts");
            paidSecond(h, p, PowerBalance.drain("speed_cyclone", 4));
            empty(p);
            h.assertTrue(!SpeedKit.cycloning(p) && !StaminaDrain.active(p, "speed_cyclone"), "An unpaid next tick ends the vortex and its HUD drain");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "cryo_rework_arena", batch = "heldwind.anchor", timeoutTicks = 320)
    public static void anchoringNeverMakesContinuedCycloneUpkeepFree(GameTestHelper h) {
        var p = player(h, ModAbilities.SUPERSPEED_ID);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16, 2, 16))));
        h.assertTrue(SpeedKit.cyclone(p, 3, true), "The held Cyclone starts");
        for (int i = 0; i < 8; i++) SpeedKit.cycloneSpeed(p, 1);
        h.runAfterDelay(SpeedKit.TORNADO_HOLD + SpeedKit.TORNADO_ANCHOR + 10, () -> {
            try {
                h.assertTrue(SpeedKit.tornado(p), "A fast held ring rose into a tornado");
                paidSecond(h, p, PowerBalance.drain("speed_cyclone", 4));
                SpeedKit.releaseCyclone(p);
                h.assertTrue(SpeedKit.standing(p) && StaminaDrain.active(p, "speed_cyclone"), "A detached tornado still pays while it exists");
                empty(p);
                h.assertTrue(!SpeedKit.cycloning(p), "An anchored tornado also collapses on exhaustion");
                h.succeed();
            } finally { cleanup(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "heldwind.arms")
    public static void windArmsPaysWhileHeldAndReleaseClearsTheDrain(GameTestHelper h) {
        var p = player(h, ModAbilities.SUPERSPEED_ID);
        try {
            h.assertTrue(SpeedWindArms.start(p), "Wind Arms starts");
            paidSecond(h, p, PowerBalance.drain("speed_cyclone", 4));
            h.assertTrue(SpeedWindArms.stop(p) && !StaminaDrain.active(p, "speed_wind_arms"), "Releasing stops the wind and all further payments");
            h.assertTrue(SpeedWindArms.start(p), "It can start again");
            empty(p);
            h.assertTrue(!SpeedWindArms.active(p), "Wind Arms ends on exhaustion too");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "heldwind.tempest")
    public static void tempestPaysThroughItsWindupAndCancellationAndExhaustionCleanUp(GameTestHelper h) {
        var p = player(h, ModAbilities.FLIGHT_ID);
        try {
            FlightService.startCyclone(p);
            paidSecond(h, p, PowerBalance.drain("flight_cyclone", 12));
            FlightService.cancelCyclone(p);
            h.assertTrue(!StaminaDrain.active(p, "flight_cyclone"), "Canceling Tempest clears its drain");
            FlightService.startCyclone(p); empty(p);
            h.assertTrue(!FlightService.isCycloneActive(p) && !StaminaDrain.active(p), "An unpaid Tempest ends without overexertion");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "heldwind.maelstrom")
    public static void maelstromPaysDuringItsWindupAndEndsItsDrainWithTheStorm(GameTestHelper h) {
        var p = player(h, ModAbilities.HYDROKINESIS_ID);
        try {
            p.setXRot(50); p.setYRot(0); HydroService.fill(p, 100);
            var c = new AbilityUseContext(p, ModAbilities.HYDROKINESIS_ID, 5, false);
            h.assertTrue(HydroService.startMaelstrom(c), "Maelstrom starts over the arena floor");
            paidSecond(h, p, PowerBalance.drain("hydro_maelstrom", 12));
            HydroService.endMaelstrom(p);
            h.assertTrue(!StaminaDrain.active(p, "hydro_maelstrom"), "Bringing the spout down clears its drain");
            h.assertTrue(HydroService.startMaelstrom(c), "The storm can start again"); empty(p);
            h.assertTrue(!HydroService.storming(p) && !StaminaDrain.active(p), "Exhaustion ends Maelstrom and its drain");
            h.succeed();
        } finally { cleanup(p); }
    }
}
