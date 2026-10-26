package com.jayptucker.heroesevolved.ability;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Shared by activation and the HUD. Multipliers preserve server-configured costs, including zero. */
public final class PowerBalance {
    public record Tuning(String power, double energy, double cooldown) {}
    private static final Map<String, Tuning> ACTIONS = load();
    private PowerBalance() {}
    private static Map<String, Tuning> load() {
        try (var stream = PowerBalance.class.getResourceAsStream("/data/heroesevolved/power_balance.json")) {
            if (stream == null) throw new IllegalStateException("Missing power balance");
            Map<String, Tuning> result = new HashMap<>();
            for (var element : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) {
                var o = element.getAsJsonObject();
                var tuning = new Tuning(o.get("power").getAsString(), o.get("energy").getAsDouble(), o.get("cooldown").getAsDouble());
                if (!Double.isFinite(tuning.energy) || !Double.isFinite(tuning.cooldown) || tuning.energy <= 0 || tuning.energy > 3
                        || tuning.cooldown <= 0 || tuning.cooldown > 3 || result.put(o.get("action").getAsString(), tuning) != null)
                    throw new IllegalStateException("Invalid power balance entry");
            }
            return Map.copyOf(result);
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static Map<String, Tuning> entries() { return ACTIONS; }
    public static int energy(ResourceLocation action, int configured) { return scale(action, configured, true); }
    public static int cooldown(ResourceLocation action, int configured) { return scale(action, configured, false); }
    private static int scale(ResourceLocation action, int configured, boolean energy) {
        if (configured <= 0) return 0;
        var tuning = action.getNamespace().equals("heroesevolved") ? ACTIONS.get(action.getPath()) : null;
        return tuning == null ? configured : Math.max(1, (int) Math.ceil(configured * (energy ? tuning.energy : tuning.cooldown)));
    }
}
