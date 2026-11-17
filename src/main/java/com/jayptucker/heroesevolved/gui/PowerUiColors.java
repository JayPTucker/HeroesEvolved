package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.mimicry.MimicColors;
import net.minecraft.util.FastColor.ARGB32;

/** Shared colors for the stamina bar and the controls for the power it fuels. */
public final class PowerUiColors {
    public static final int FRAME = 0xFF0B0D12;
    public static final int TRACK = 0xFF14171D;
    public static final int TEXT = 0xFFF4F0E8;
    public static final int MUTED_TEXT = 0xFFB9BFC9;
    public static final int MUTED_EDGE = 0xFF59606D;

    private PowerUiColors() {}

    public record Palette(int accent, int highlight, int edge, int surface, int selected, int hover) {}

    public static int fill(String power) {
        return ARGB32.opaque(MimicColors.color(power));
    }

    public static Palette forPower(String power) {
        int accent = fill(power);
        return new Palette(accent, ARGB32.lerp(.45F, accent, 0xFFFFFFFF), ARGB32.lerp(.57F, TRACK, accent),
                ARGB32.lerp(.07F, TRACK, accent), ARGB32.lerp(.28F, TRACK, accent), ARGB32.lerp(.42F, TRACK, accent));
    }
}
