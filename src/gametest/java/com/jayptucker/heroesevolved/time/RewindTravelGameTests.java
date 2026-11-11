package com.jayptucker.heroesevolved.time;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** The trip back through your own past: along the path you took, across worlds, never faster than it can be shown. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class RewindTravelGameTests {
    private static TimeRewindPlayers.Point at(long time, net.minecraft.resources.ResourceKey<Level> world, double x) {
        return new TimeRewindPlayers.Point(time, world, new Vec3(x, 70, 0), 90, 0);
    }

    @GameTest(template = "rewind_empty", batch = "rewind.travel.worlds")
    public static void theTripBackFollowsThePathAcrossWorlds(GameTestHelper h) {
        // Newest first: walking in the Nether now, having come through a portal from the Overworld.
        List<TimeRewindPlayers.Point> path = List.of(
                at(100, Level.NETHER, 20), at(90, Level.NETHER, 10), at(80, Level.NETHER, 0),
                at(78, Level.OVERWORLD, 800), at(60, Level.OVERWORLD, 790), at(40, Level.OVERWORLD, 780));
        List<RewindTravel.Stop> stops = RewindTravel.plan(path, RewindTravel.MOMENT_TICKS);
        RewindTravel.Stop first = stops.getFirst(), last = stops.getLast();
        h.assertTrue(first.dimension() == Level.NETHER && first.at().x == 20, "It starts where they are now: " + first);
        h.assertTrue(last.dimension() == Level.OVERWORLD && last.at().x == 780, "It ends where they were then, in the other world: " + last);
        int crossing = -1;
        for (int i = 0; i < stops.size(); i++) if (stops.get(i).dimension() == Level.OVERWORLD) { crossing = i; break; }
        h.assertTrue(crossing > 1, "It goes back through the Nether first");
        for (int i = 0; i < stops.size(); i++) {
            RewindTravel.Stop stop = stops.get(i);
            h.assertTrue((stop.dimension() == Level.NETHER) == (i < crossing), "It crosses once, back through the portal: stop " + i);
            boolean inside = stop.dimension() == Level.NETHER ? stop.at().x >= 0 && stop.at().x <= 20 : stop.at().x >= 780 && stop.at().x <= 800;
            h.assertTrue(inside, "Never between worlds: " + stop);
            if (i > 0 && stops.get(i - 1).dimension() == stop.dimension())
                h.assertTrue(stop.at().x <= stops.get(i - 1).at().x + 1e-6, "Always backward along the path: " + stop);
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "rewind.travel.long")
    public static void aLongPathIsShownAsFastAsItCanBeThenCut(GameTestHelper h) {
        // A speedster's minute: two thousand blocks in a straight line.
        List<TimeRewindPlayers.Point> path = new ArrayList<>();
        for (int i = 0; i <= 400; i++) path.add(at(1200 - i * 3L, Level.OVERWORLD, 2000 - i * 5.0));
        List<RewindTravel.Stop> stops = RewindTravel.plan(path, RewindTravel.MINUTE_TICKS);
        h.assertTrue(stops.size() == RewindTravel.MINUTE_TICKS + 1, "One stop a tick for the whole trip: " + stops.size());
        for (int i = 1; i < stops.size() - 1; i++)
            h.assertTrue(stops.get(i - 1).at().distanceTo(stops.get(i).at()) <= RewindTravel.MAX_STEP + 1e-6, "Never faster than it can be shown");
        h.assertTrue(stops.getLast().at().x == 0, "And it still ends where they were a minute ago");
        // Standing still for the whole time is still a short trip back, not none.
        List<RewindTravel.Stop> still = RewindTravel.plan(List.of(at(100, Level.OVERWORLD, 5), at(50, Level.OVERWORLD, 5), at(0, Level.OVERWORLD, 5)),
                RewindTravel.MOMENT_TICKS);
        h.assertTrue(still.size() > 10, "Standing still still feels like going back");
        h.succeed();
    }
}
