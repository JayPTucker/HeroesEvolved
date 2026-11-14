package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.story.AvenueService;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The client's clock for the light at 6:14. Everything drawn for it is a function of one number,
 * {@link #t}: ticks since the flash, negative before it. When time stops for this visitor the clock
 * holds still, then runs back to just before the flash, so the whole explosion plays in reverse.
 */
public final class IntroScene {
    /** In the city for the Time vision or an evening on the avenue. */
    static boolean active;
    /** The Time vision, where time stops and runs back; the avenue simply ends. */
    static boolean vision;

    /** Whether the scene playing is the Time vision (not the Last Day or the avenue at 6:14). */
    public static boolean isVision() { return vision; }
    static long arrival = -1, detonation = -1, freeze = -1;
    /**
     * The Last Day's prologue: the light appears ({@link #light}) and grows for {@link #run} ticks, surging, but never
     * goes off while anyone is there to see it. {@link #underground} once this player is down in the safe house.
     */
    static boolean prologue, underground;
    static long light = -1;
    static int run = 1800;
    /** The prologue's clock: always long before the flash. */
    private static final float NEVER = -100000;
    static float frozenAt;
    /** Where the rewind ends: a second before the flash. */
    static final float REWOUND = -20;

    private IntroScene() {}

    static void reset() {
        active = false;
        vision = false;
        prologue = underground = false;
        arrival = detonation = freeze = light = -1;
        frozenAt = 0;
    }

    static float now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    /** Ticks from arrival to the flash. */
    static int lead() { return vision ? TimeIntroService.DETONATION_TICK : AvenueService.LIGHT_TICK; }

    /** Ticks since the flash, as the scene shows them: still while frozen, falling while rewinding. */
    public static float t(float partial) {
        if (!active) return Float.NEGATIVE_INFINITY;
        if (prologue) return NEVER;
        float now = now(partial);
        if (freeze >= 0) {
            float since = now - freeze;
            if (since < IntroBlast.FREEZE_TICKS) return frozenAt;
            float r = Mth.clamp((since - IntroBlast.FREEZE_TICKS) / IntroBlast.REWIND_TICKS, 0, 1);
            float eased = r * r * (3 - 2 * r);
            return Mth.lerp(eased, frozenAt, REWOUND);
        }
        if (detonation >= 0) return now - detonation;
        if (arrival >= 0) return now - arrival - lead();
        return Float.NEGATIVE_INFINITY;
    }

    /** Ticks since the prologue's light appeared, or -1 before it. */
    static float sinceLight(float partial) { return prologue && light >= 0 ? now(partial) - light : -1; }

    static boolean frozen(float partial) {
        return freeze >= 0 && now(partial) - freeze < IntroBlast.FREEZE_TICKS;
    }

    static boolean rewinding(float partial) {
        return freeze >= 0 && now(partial) - freeze >= IntroBlast.FREEZE_TICKS;
    }

    /** Time held or running backwards. */
    static boolean stopped() { return freeze >= 0; }
}
