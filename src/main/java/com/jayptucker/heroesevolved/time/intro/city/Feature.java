package com.jayptucker.heroesevolved.time.intro.city;

import java.util.function.Consumer;

/** Something drawn in the city, with the columns it can touch (including anything that overhangs). */
record Feature(int x0, int z0, int x1, int z1, Consumer<CityCanvas> draw) {
    boolean touches(CityCanvas c) { return c.touches(x0, z0, x1, z1); }
}
