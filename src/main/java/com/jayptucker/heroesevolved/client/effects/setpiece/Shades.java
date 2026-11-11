package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.intro.CloudMesh;
import net.minecraft.util.Mth;

/** How billows of cloud are coloured: smoke and dust lit by the sun, fire by its own heat, mist and glow flat. */
public final class Shades {
    private Shades() {}

    /** Glowing colour for a temperature from 0 (cold) to 1 (white hot), as the vision's fireball burns. */
    public static void heat(float h, float[] out) {
        if (h > 0.75F) { float k = (h - 0.75F) / 0.25F; out[0] = 1; out[1] = Mth.lerp(k, 0.82F, 0.98F); out[2] = Mth.lerp(k, 0.42F, 0.9F); }
        else if (h > 0.45F) { float k = (h - 0.45F) / 0.3F; out[0] = 1; out[1] = Mth.lerp(k, 0.45F, 0.82F); out[2] = Mth.lerp(k, 0.1F, 0.42F); }
        else if (h > 0.15F) { float k = (h - 0.15F) / 0.3F; out[0] = Mth.lerp(k, 0.55F, 1); out[1] = Mth.lerp(k, 0.1F, 0.45F); out[2] = Mth.lerp(k, 0.03F, 0.1F); }
        else { float k = h / 0.15F; out[0] = 0.55F * k; out[1] = 0.1F * k; out[2] = 0.03F * k; }
    }

    /**
     * Smoke, dust or storm cloud of colour {@code base}, lit by the sun from above and the sky around, with
     * {@code heat} (0 to 1) glowing through its underside and its hollows like the vision's fireball.
     */
    public static CloudMesh.Shade smoke(Canvas canvas, float[] base, float heat) {
        float[] glow = new float[3];
        float day = canvas.daylight;
        return (nx, ny, nz, bump, wx, wy, wz, rgb) -> {
            float sun = Math.max(0, (nx * 0.36F + ny * 0.84F + nz * 0.41F + 0.2F) / 1.2F);
            float cavity = Mth.clamp(0.78F + bump * 1.1F, 0.42F, 1.18F);
            float light = (0.3F + 0.3F * day + 0.18F * ny * day + 0.62F * sun * day) * cavity;
            float h = Mth.clamp(heat * (0.7F + 0.55F * Math.max(0, -ny) + bump * 0.9F), 0, 1);
            heat(h, glow);
            rgb[0] = Mth.clamp(base[0] * light * (1 - h * 0.85F) + glow[0] * h * 1.3F, 0, 1);
            rgb[1] = Mth.clamp(base[1] * light * (1 - h * 0.85F) + glow[1] * h * 1.3F, 0, 1);
            rgb[2] = Mth.clamp(base[2] * light * (1 - h * 0.85F) + glow[2] * h * 1.3F, 0, 1);
        };
    }

    /** Flame: white-hot at its heart, falling through yellow and orange to red at the rim. */
    public static CloudMesh.Shade fire(float heat) {
        float[] glow = new float[3];
        return (nx, ny, nz, bump, wx, wy, wz, rgb) -> {
            float h = Mth.clamp(heat * (0.75F + bump * 1.2F + 0.2F * ny), 0, 1);
            heat(h, glow);
            rgb[0] = glow[0]; rgb[1] = glow[1]; rgb[2] = glow[2];
        };
    }

    /** One flat colour, a little brighter where the surface faces up. */
    public static CloudMesh.Shade flat(float[] c) {
        return (nx, ny, nz, bump, wx, wy, wz, rgb) -> {
            float k = 0.85F + 0.15F * ny + bump * 0.4F;
            rgb[0] = Mth.clamp(c[0] * k, 0, 1); rgb[1] = Mth.clamp(c[1] * k, 0, 1); rgb[2] = Mth.clamp(c[2] * k, 0, 1);
        };
    }
}
