package com.jayptucker.heroesevolved.time.intro;

import com.jayptucker.heroesevolved.time.intro.city.CityAudit;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Odessa is drawn whole and checked block by block: nothing floats, no door opens into a wall, no pane ends in a stub. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CityAuditGameTests {
    @GameTest(template = "rewind_empty", timeoutTicks = 400, batch = "CityAuditGameTests.wholeCity")
    public static void theWholeCityStandsUp(GameTestHelper helper) {
        ServerLevel city = helper.getLevel().getServer().getLevel(TimeIntroService.DIMENSION);
        int x0 = CityGrid.OUTER_MIN, x1 = CityGrid.OUTER_MAX;
        long start = System.currentTimeMillis();
        for (int x = x0; x <= x1; x += 16)
            for (int z = x0; z <= x1; z += 16) FractureCity.rebuild(city, x, z, Math.min(x + 15, x1), Math.min(z + 15, x1));
        long built = System.currentTimeMillis();
        CityAudit.Report report = CityAudit.run(city, x0, x0, x1, x1);
        System.out.println("CITY_AUDIT built in " + (built - start) + " ms, audited in " + (System.currentTimeMillis() - built) + " ms\n" + report);
        helper.assertTrue(report.total() == 0, "The city has " + report.total() + " faults:\n" + report);
        helper.succeed();
    }

    /** No monster spawns on its own in the vision city; the overworld is left alone. */
    @GameTest(template = "rewind_empty", batch = "CityAuditGameTests.monsters")
    public static void noMonstersSpawnInTheCity(GameTestHelper helper) {
        ServerLevel city = helper.getLevel().getServer().getLevel(TimeIntroService.DIMENSION);
        for (ServerLevel level : new ServerLevel[] {city, helper.getLevel().getServer().overworld()}) {
            var zombie = new net.minecraft.world.entity.monster.Zombie(level);
            zombie.setPos(4.5, CityGrid.STREET_Y + 1, 4.5);
            var event = new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(zombie, level, zombie.getX(), zombie.getY(),
                    zombie.getZ(), level.getCurrentDifficultyAt(zombie.blockPosition()), net.minecraft.world.entity.MobSpawnType.NATURAL, null, null);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            boolean inCity = level == city;
            helper.assertTrue(event.isSpawnCancelled() == inCity,
                    inCity ? "A zombie must not spawn on its own in the city" : "Overworld spawning must be left alone");
        }
        helper.succeed();
    }
}
