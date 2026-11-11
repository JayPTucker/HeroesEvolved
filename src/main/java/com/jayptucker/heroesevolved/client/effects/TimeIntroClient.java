package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.effects.intro.IntroDirector;

import java.util.List;

/** Where the server's cues for the Time vision and the avenue arrive; the show itself is {@link IntroDirector}. */
public final class TimeIntroClient {
    private TimeIntroClient() {}

    public static void cue(int stage) {
        cue(stage, List.of());
    }

    /** {@code players}: with FREEZE, the entity ids of the visitors whose power it is. */
    public static void cue(int stage, List<Integer> players) {
        IntroDirector.cue(stage, players);
    }
}
