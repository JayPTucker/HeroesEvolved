package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import net.minecraft.client.Minecraft;

/**
 * Combines this mod's comfort settings with Minecraft's own accessibility
 * options, so players who already turned those on are covered automatically.
 */
public final class VisualComfort {
    /** Highest alpha a full-screen wash may reach with reduced flashing. */
    private static final int REDUCED_FLASH_ALPHA = 80;

    private VisualComfort() {
    }

    /** True if this mod's option or vanilla's "Hide Lightning Flashes" asks for it. */
    public static boolean reduceFlashing() {
        Minecraft minecraft = Minecraft.getInstance();
        return ClientVisualSettings.REDUCE_FLASHING.get()
                || (minecraft.options != null && minecraft.options.hideLightningFlash().get());
    }

    /** Camera shake scale, also honouring vanilla's Distortion Effects slider. */
    public static float shakeScale() {
        Minecraft minecraft = Minecraft.getInstance();
        float vanilla = minecraft.options == null ? 1.0F
                : minecraft.options.screenEffectScale().get().floatValue();
        return ClientVisualSettings.SHAKE.get().floatValue() * vanilla;
    }

    /** Adjusts a full-screen wash's alpha for the flash settings. */
    public static int flashAlpha(int alpha) {
        int scaled = Math.round(alpha * ClientVisualSettings.FLASH.get().floatValue());
        return reduceFlashing() ? Math.min(scaled, REDUCED_FLASH_ALPHA) : scaled;
    }
}
