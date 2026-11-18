package com.jayptucker.heroesevolved.ability;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.Elements;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/** Water boils away in the Nether and rain puts fire out: those powers fizzle, free, and their held state goes. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ElementsGameTests {
    @GameTest(template = "rewind_empty", batch = "elements.nether")
    public static void hydrokinesisFizzlesInTheNether(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "The test server must have a Nether");
        ServerPlayer p = player(nether, ModAbilities.HYDROKINESIS_ID, new Vec3(0.5, 80, 0.5));
        ServerPlayer home = player(h.getLevel(), ModAbilities.HYDROKINESIS_ID,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        try {
            HydroService.fill(p, 200);
            h.assertTrue(HydroService.water(p) > 0, "The bender starts with water");
            h.assertTrue(Elements.snuffed(p), "Hydrokinesis is snuffed in the Nether");
            h.assertFalse(Elements.snuffed(home), "Hydrokinesis works in the Overworld");
            h.assertFalse(HydroService.canBend(p), "Nothing can be bent in the Nether");
            h.assertTrue(HydroService.canBend(home), "Water bends in the Overworld");

            double stamina = PlayerEnergyService.getStamina(p);
            for (AbilitySlot slot : AbilitySlot.values()) {
                AbilityUseResult result = AbilityUseService.activateAssignedAction(p, slot, false);
                h.assertTrue(result == AbilityUseResult.CANNOT_USE, slot + " must fizzle in the Nether, was " + result);
            }
            h.assertTrue(PlayerEnergyService.getStamina(p) == stamina, "A fizzle costs no stamina");
            h.assertFalse(FightingStyles.dodge(p, FightingStyles.Dodge.BACK), "Waterbending dodges fizzle too");

            Elements.check(p);
            h.assertTrue(HydroService.water(p) == 0, "The carried water boils away, had " + HydroService.water(p));
            h.succeed();
        } finally {
            HydroService.forget(p);
            HydroService.forget(home);
        }
    }

    @GameTest(template = "rewind_empty", batch = "elements.rain", skyAccess = true)
    public static void pyrokinesisFizzlesInTheRain(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = player(level, ModAbilities.PYROKINESIS_ID,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 5))));
        level.setWeatherParameters(0, 6000, true, false);
        level.setRainLevel(1);
        try {
            h.assertTrue(Elements.doused(p), "The test needs rain falling on the player");
            h.assertTrue(Elements.snuffed(p), "Pyrokinesis is snuffed in the rain");
            h.assertFalse(PyroService.canStartCast(p), "No flame can start in the rain");
            PyroService.addHeat(p, 50);
            h.assertTrue(PyroService.heat(p) > 0, "The pyro starts with Heat");

            double stamina = PlayerEnergyService.getStamina(p);
            for (AbilitySlot slot : AbilitySlot.values()) {
                AbilityUseResult result = AbilityUseService.activateAssignedAction(p, slot, false);
                h.assertTrue(result == AbilityUseResult.CANNOT_USE, slot + " must fizzle in the rain, was " + result);
            }
            h.assertTrue(PlayerEnergyService.getStamina(p) == stamina, "A fizzle costs no stamina");
            h.assertFalse(FightingStyles.dodge(p, FightingStyles.Dodge.LEFT), "Firebending dodges fizzle too");

            Elements.check(p);
            h.assertTrue(PyroService.heat(p) == 0, "The rain puts the Heat out, had " + PyroService.heat(p));

            level.setWeatherParameters(6000, 0, false, false);
            level.setRainLevel(0);
            h.assertFalse(Elements.snuffed(p), "Out of the rain, fire burns again");
            h.assertTrue(PyroService.canStartCast(p), "A dry pyro can cast");
            h.succeed();
        } finally {
            level.setWeatherParameters(6000, 0, false, false);
            level.setRainLevel(0);
            PyroService.forget(p);
        }
    }

    @GameTest(template = "rewind_empty", batch = "elements.rainwater", skyAccess = true)
    public static void rainMendsAndFeedsAWaterbender(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = player(level, ModAbilities.HYDROKINESIS_ID,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 5))));
        try {
            double dry = PlayerEnergyService.regenerationPerSecond(p);
            h.assertTrue(HydroService.seaEnergyBonus(p) == 0, "No bonus out of the rain");
            level.setWeatherParameters(0, 6000, true, false);
            level.setRainLevel(1);
            h.assertTrue(p.isInWaterRainOrBubble(), "The test needs rain falling on the player");
            h.assertTrue(HydroService.seaEnergyBonus(p) == 1, "Rain adds 1 stamina a second");
            h.assertTrue(PlayerEnergyService.regenerationPerSecond(p) == dry + 1,
                    "Recovery includes the rain's bonus: " + PlayerEnergyService.regenerationPerSecond(p) + " vs " + dry);

            p.setHealth(10);
            p.tickCount = 20;
            HydroService.playerTick(p);
            h.assertTrue(Math.abs(p.getHealth() - 10.5F) < 1.0E-4, "Rain heals half a point a second, health " + p.getHealth());
            h.succeed();
        } finally {
            level.setWeatherParameters(6000, 0, false, false);
            level.setRainLevel(0);
            HydroService.forget(p);
        }
    }

    private static ServerPlayer player(ServerLevel level, ResourceLocation power, Vec3 at) {
        ServerPlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "element-test"));
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(at);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }
}
