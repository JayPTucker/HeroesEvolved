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
    static long arrival = -1, detonation = -1, freeze = -1;
    static float frozenAt;
    /** Where the rewind ends: a second before the flash. */
    static final float REWOUND = -20;

    private IntroScene() {}

    static void reset() {
        active = false;
        vision = false;
        arrival = detonation = freeze = -1;
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

    static boolean frozen(float partial) {
        return freeze >= 0 && now(partial) - freeze < IntroBlast.FREEZE_TICKS;
    }

    static boolean rewinding(float partial) {
        return freeze >= 0 && now(partial) - freeze >= IntroBlast.FREEZE_TICKS;
    }

    /** Time held or running backwards. */
    static boolean stopped() { return freeze >= 0; }
}
