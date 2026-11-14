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
    /** 4: the Odessa Arms and the way down from Primatech's lobby (the Last Day); 5: a stair instead of a ladder. */
    private static final int VERSION = 5;
    private static final long BUDGET_NANOS = 12_000_000L;
    private static final int CHUNKS = WIDTH / 16, FIRST_CHUNK = MIN >> 4;
    private int unit, version;
    /** Someone is waiting on the city (a new player's Last Day): build it about three times as fast. */
    private static boolean rush;

    public void rush(boolean on) { rush = on; }

    public static FractureCity get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(FractureCity::new,
                (tag, registries) -> {
                    FractureCity city = new FractureCity();
                    city.version = tag.getInt("version");
                    return city;
                }), "heroesevolved_fracture_city");
    }

    public boolean ready() { return version == VERSION; }
    /** How much of the city stands, from 0 to 1. */
    public float progress() { return ready() ? 1 : unit / (float) (CHUNKS * CHUNKS); }

    public void tickBuild(ServerLevel level) {
        if (ready()) return;
        long start = System.nanoTime();
        long budget = rush ? BUDGET_NANOS * 3 : BUDGET_NANOS;
        while (unit < CHUNKS * CHUNKS && System.nanoTime() - start < budget) {
            int cx = FIRST_CHUNK + unit % CHUNKS, cz = FIRST_CHUNK + unit / CHUNKS;
            rebuild(level, cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15);
            unit++;
        }
        if (unit >= CHUNKS * CHUNKS) { version = VERSION; setDirty(); }
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
        return tag;
    }
}
