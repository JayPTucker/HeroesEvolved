package com.jayptucker.heroesevolved.client.sound;

/** Small playback ramps prevent an abrupt onset/cut without repeating the vocal. */
public final class SonicVoiceEnvelope {
    public static final int ATTACK_TICKS = 2;
    public static final int RELEASE_TICKS = 2;
    public static final int MAXIMUM_TICKS = 110;
    private SonicVoiceEnvelope() {}

    public static float gain(int age, int releaseRemaining) {
        float attack = Math.clamp(age / (float) ATTACK_TICKS, 0, 1);
        float release = releaseRemaining < 0 ? 1 : Math.clamp(releaseRemaining / (float) RELEASE_TICKS, 0, 1);
        return attack * release;
    }
}
