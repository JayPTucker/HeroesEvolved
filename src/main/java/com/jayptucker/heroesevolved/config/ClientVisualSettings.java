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
    public static final ModConfigSpec.BooleanValue FIRE_STRIKES, FIRE_DODGES;

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
        FIRE_STRIKES = b.comment("Pyrokinesis: left-click with an empty hand to throw fire from your fists and feet instead of punching.")
                .define("firebendingStrikes", true);
        FIRE_DODGES = b.comment("Pyrokinesis: double-tap left, right or back to dodge on a jet of flame.")
                .define("firebendingDodges", true);
        SPEC = b.build();
    }

    private ClientVisualSettings() {
    }
}
