package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.intro.PowerIntroService;

/**
 * The radiation awakening's blackout, then uncertain blinks from the crater floor: the shared {@link DazeView}, for the
 * awakening's recovery, held a little after it. Only the owner's view is directed.
 */
public final class RadiationIntroClient {
    private RadiationIntroClient() {}

    public static void begin() { DazeView.begin(PowerIntroService.RADIATION_RECOVERY_TICKS, 40, false); }

    public static boolean active() { return DazeView.active(); }

    public static void clear() { DazeView.clear(); }
}
