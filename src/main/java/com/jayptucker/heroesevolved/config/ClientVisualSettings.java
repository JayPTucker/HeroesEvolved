package com.jayptucker.heroesevolved.config;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class ClientVisualSettings {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SHAKE, FLASH, FOG;
    static {
        var b = new ModConfigSpec.Builder();
        SHAKE = b.comment("Personal camera shake intensity; zero disables shake.").defineInRange("cameraShake", 1D, 0, 1);
        FLASH = b.comment("Personal power flash intensity; zero disables full-screen flashes.").defineInRange("screenFlashes", 1D, 0, 1);
        FOG = b.comment("Cold fog intensity. A minimum preserves the storm's combat cue.").defineInRange("coldFog", 1D, .25, 1);
        SPEC = b.build();
    }
}
