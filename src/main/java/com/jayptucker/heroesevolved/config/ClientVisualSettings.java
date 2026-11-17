package com.jayptucker.heroesevolved.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-player comfort and accessibility settings for power effects. They
 * change only what this client sees; gameplay is unaffected. Editable in
 * game from Mods -> Heroes Evolved -> Config, or the Powers screen.
 *
 * <p>This class is also loaded on dedicated servers (the config is
 * registered by the main mod class), so it must not touch client classes;
 * see {@code client.VisualComfort} for the helpers that do.</p>
 */
public final class ClientVisualSettings {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SHAKE, FLASH, FOG;
    public static final ModConfigSpec.BooleanValue REDUCE_FLASHING, SEVERED_LIMBS;
    /** Pyrokinesis controls: empty-handed left clicks throw fire, and double-tapping left, right or back dodges. */
    public static final ModConfigSpec.BooleanValue STYLE_STRIKES, STYLE_DODGES;
    /** Finishers: the gold glisten on foes low enough to finish, and the camera that pulls back to frame the move. */
    public static final ModConfigSpec.BooleanValue FINISHER_GLISTEN, FINISHER_CAMERA;

    static {
        var b = new ModConfigSpec.Builder();
        SHAKE = b.comment("Personal camera shake intensity; zero disables shake.").defineInRange("cameraShake", 1D, 0, 1);
        FLASH = b.comment("Personal power flash intensity; zero disables full-screen flashes and white-outs.").defineInRange("screenFlashes", 1D, 0, 1);
        FOG = b.comment("Cold fog intensity. A minimum preserves the storm's combat cue.").defineInRange("coldFog", 1D, .25, 1);
        REDUCE_FLASHING = b.comment("Photosensitivity: cap full-screen flashes at a soft wash and stop pulsing edges from throbbing. "
                        + "Also enabled automatically by Minecraft's 'Hide Lightning Flashes' accessibility option.")
                .define("reduceFlashing", false);
        SEVERED_LIMBS = b.comment("Show Regeneration's severed limbs growing back from the bone. Off: the limb fades out and back in.")
                .define("showSeveredLimbs", true);
        STYLE_STRIKES = b.comment("Fighting styles (Pyrokinesis, Electrokinesis, Hydrokinesis): left-click with an empty hand to",
                        "strike with your power (fire, lightning or water) instead of punching.")
                .define("fightingStyleStrikes", true);
        STYLE_DODGES = b.comment("Fighting styles: double-tap left, right or back to dodge (a jet of flame, a flash of lightning,",
                        "a slide on water).")
                .define("fightingStyleDodges", true);
        FINISHER_GLISTEN = b.comment("Finishers: a gold shimmer and a rising glint on foes low enough to finish. Off: only the",
                        "key prompt under the crosshair shows.")
                .define("finisherGlisten", true);
        FINISHER_CAMERA = b.comment("Finishers: the camera pulls back to a still side view while your finisher plays. Off: the",
                        "camera stays where it is.")
                .define("finisherCamera", true);
        SPEC = b.build();
    }

    private ClientVisualSettings() {
    }
}
