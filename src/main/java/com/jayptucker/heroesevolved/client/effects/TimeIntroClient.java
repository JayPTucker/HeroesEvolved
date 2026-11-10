package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.effects.intro.IntroDirector;

/** Where the server's cues for the Time vision and the avenue arrive; the show itself is {@link IntroDirector}. */
public final class TimeIntroClient {
    private TimeIntroClient() {}

    public static void cue(int stage) {
        IntroDirector.cue(stage);
    }
}
