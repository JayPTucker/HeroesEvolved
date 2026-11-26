package com.jayptucker.heroesevolved.combat;

import net.minecraft.server.level.ServerPlayer;

/**
 * How strong a boss's party is, and what that does to the boss: the one rule every boss scales by (the bosses skill's
 * "Party scaling", the user's rule of November 25, 2026: by how many fight it, whether they have powers, and their
 * levels). The Wither and the Living Seal both read it.
 */
public final class BossScaling {
    /** Each Power Level above or below the boss's baseline adds or takes this much of a fighter's weight. */
    public static final float LEVEL_STEP = .06F, WEAKEST_GIFT = .6F, STRONGEST_GIFT = 1.3F, UNGIFTED = .5F;

    private BossScaling() {}

    /**
     * How much one fighter counts against a boss tuned for {@code baseline}: 1 with an awakened power at that level,
     * {@link #LEVEL_STEP} more or less a level (never under {@link #WEAKEST_GIFT}), {@link #UNGIFTED} with no power.
     */
    public static float strength(boolean gifted, int powerLevel, int baseline) {
        return gifted ? Math.clamp(1 + LEVEL_STEP * (powerLevel - baseline), WEAKEST_GIFT, STRONGEST_GIFT) : UNGIFTED;
    }
    public static float strength(ServerPlayer player, int baseline) {
        return strength(BossLocks.gifted(player), com.jayptucker.heroesevolved.progression.PlayerProgressionService.getPowerLevel(player), baseline);
    }
    /** Health for a party whose strengths add up to {@code weight}: half again per extra fighter's worth, 70% to three times. */
    public static float healthScale(float weight) { return Math.clamp(1 + .5F * (weight - 1), .7F, 3); }
    /** A lock target's health the same way, up to twice. */
    public static float lockScale(float weight) { return Math.clamp(1 + .5F * (weight - 1), .7F, 2); }
    /** Its blows by the average strength of the fighters there: a fifth softer against weak parties, a tenth harder against strong. */
    public static float blowScale(float average) { return Math.clamp(.7F + .3F * average, .8F, 1.1F); }
    /** Its warnings run up to 15% longer against weaker fighters, never shorter than listed. */
    public static float windupScale(float average) { return 1 + .25F * Math.clamp(1 - average, 0, .6F); }
}
