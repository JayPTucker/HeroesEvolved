package com.jayptucker.heroesevolved.client.effects.setpiece;

/**
 * Invisibility's one palette: a lens of bent light made solid. Every part of the power reads it, so the force fields
 * ({@code ForceArt}, the force fighting style in {@code StyleArt}), the Fade's refraction strokes
 * ({@code InvisibilityEffects}) and the cloaked body and hands ({@code OpticalClient}) are plainly one power.
 *
 * <ul>
 *   <li>{@link #FIELD} {@code 9CC8FF}: the body of every sheet, and the main stroke of a fade or a step.</li>
 *   <li>{@link #EDGE} {@code E8F4FF}: rims, facet seams and the bright core of a stroke.</li>
 *   <li>{@link #DEEP} {@code 3F6FD8}: halos, and the soft outer layer of a three-layer line.</li>
 *   <li>{@link #SHEEN} {@code B6A2FF}: the violet iridescence on bubbles, pressure and the veil.</li>
 *   <li>{@link #STRESS} {@code 1E2A44}: cracks, drawn with normal blending.</li>
 * </ul>
 */
public final class ForcePalette {
    public static final int FIELD = 0x9CC8FF, EDGE = 0xE8F4FF, DEEP = 0x3F6FD8, SHEEN = 0xB6A2FF, STRESS = 0x1E2A44;

    private ForcePalette() {}

    /** {@code colour} with an alpha byte in front, for vertex tints that take ARGB. */
    public static int argb(int alpha, int colour) { return (alpha & 0xFF) << 24 | colour & 0xFFFFFF; }

    /** One channel of {@code colour} as 0 to 1 ({@code shift} 16 red, 8 green, 0 blue). */
    public static float channel(int colour, int shift) { return (colour >> shift & 0xFF) / 255F; }
}
