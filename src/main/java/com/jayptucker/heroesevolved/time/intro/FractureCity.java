package com.jayptucker.heroesevolved.time.intro;

import com.jayptucker.heroesevolved.time.intro.city.CityCanvas;
import com.jayptucker.heroesevolved.time.intro.city.CityDraw;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Odessa on the Last Day, as the Time vision and the avenue at 6:14 see it. The city is drawn from
 * {@link com.jayptucker.heroesevolved.time.intro.city.CityPlan} a chunk at a time while the server
 * runs, never more than a few milliseconds a tick, and any patch of it can be put back exactly.
 * A saved city from an older version is rebuilt over, column by column.
 */
public final class FractureCity extends SavedData {
    public static final int MIN = CityGrid.MIN, WIDTH = CityGrid.WIDTH, STREET_Y = CityGrid.STREET_Y;
    /**
     * 4: the Odessa Arms and the way down from Primatech's lobby (the Last Day); 5: a stair instead of a ladder; 6: the city
     * redrawn (reveals, rooms behind the windows, nothing hanging in the air) and the outskirts round it; 7: Odessa as a
     * small Manhattan (the great towers in the middle, Odessa Square, the Commons, Kirby Plaza's red sculpture) and the
     * Odessa Arms with a real stairwell.
     */
    private static final int VERSION = 7;
    private static final long BUDGET_NANOS = 12_000_000L;
    private static final int CHUNKS = WIDTH / 16, FIRST_CHUNK = MIN >> 4;
    /** The city's own chunks come first, row by row; then the outskirts, nearest ring first. */
    private static final int[][] ORDER = order();
    private static final int CITY_UNITS = CHUNKS * CHUNKS;
    /** How far the build has got, and which version it is building (so a restart carries on where it stopped). */
    private int unit, version, building;
    /** Someone is waiting on the city (a new player's Last Day): build it about three times as fast. */
    private static boolean rush;

    public void rush(boolean on) { rush = on; }

    public static FractureCity get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(FractureCity::new,
                (tag, registries) -> {
                    FractureCity city = new FractureCity();
                    city.version = tag.getInt("version");
                    city.building = tag.getInt("building");
                    city.unit = city.building == VERSION ? tag.getInt("unit") : 0;
                    return city;
                }), "heroesevolved_fracture_city");
    }

    /** The city itself stands (its outskirts may still be going up). */
    public boolean ready() { return version == VERSION || unit >= CITY_UNITS; }
    /** The city and its outskirts are both done. */
    public boolean complete() { return version == VERSION; }
    /** How much of the city stands, from 0 to 1. */
    public float progress() { return ready() ? 1 : unit / (float) CITY_UNITS; }

    private static int[][] order() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < CHUNKS * CHUNKS; i++) out.add(new int[] {FIRST_CHUNK + i % CHUNKS, FIRST_CHUNK + i / CHUNKS});
        int first = CityGrid.OUTER_MIN >> 4, last = CityGrid.OUTER_MAX >> 4, lo = FIRST_CHUNK, hi = FIRST_CHUNK + CHUNKS - 1;
        java.util.List<int[]> ring = new java.util.ArrayList<>();
        for (int cx = first; cx <= last; cx++)
            for (int cz = first; cz <= last; cz++)
                if (cx < lo || cx > hi || cz < lo || cz > hi) ring.add(new int[] {cx, cz});
        ring.sort(java.util.Comparator.comparingInt(c -> Math.max(Math.max(lo - c[0], c[0] - hi), Math.max(lo - c[1], c[1] - hi))));
        out.addAll(ring);
        return out.toArray(new int[0][]);
    }

    public void tickBuild(ServerLevel level) {
        if (complete()) return;
        if (building != VERSION) { building = VERSION; unit = 0; }
        long start = System.nanoTime();
        long budget = rush ? BUDGET_NANOS * 3 : BUDGET_NANOS;
        while (unit < ORDER.length && System.nanoTime() - start < budget) {
            int cx = ORDER[unit][0], cz = ORDER[unit][1];
            rebuild(level, cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15);
            unit++;
        }
        if (unit >= ORDER.length) version = VERSION;
        setDirty();
    }

    /** Puts a rectangle of columns back exactly as the plan draws it, whatever stands there now. */
    public static void rebuild(ServerLevel level, int x0, int z0, int x1, int z1) {
        CityCanvas canvas = new CityCanvas(level, x0, z0, x1, z1);
        CityDraw.draw(canvas);
    }

    /** One column of the city; also how a mission puts back whatever it built on the street. */
    public static void buildColumn(ServerLevel level, int x, int z) { rebuild(level, x, z, x, z); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", version);
        tag.putInt("building", building);
        tag.putInt("unit", unit);
        return tag;
    }
}
