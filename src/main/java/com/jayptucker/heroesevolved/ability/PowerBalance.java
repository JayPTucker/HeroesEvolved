package com.jayptucker.heroesevolved.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The stamina table ({@code data/heroesevolved/power_balance.json}): every power's actions and fighting-style
 * moves, with the stamina, cooldown and unlock tier they really have. {@code tools/balance.py} writes it from the
 * balance model and {@code docs/stamina-balance.md} explains it.
 *
 * <p>An entry gives an action's stamina ({@code energy}) and cooldown in ticks for the value the code computes at
 * the action's own tier ({@code base_energy}, {@code base_cooldown}). Other values the code computes (another tier,
 * another mode, a server's own config) are scaled in proportion, and zero stays zero, so free follow-ups and free
 * returns stay free. Optional fields: {@code unlock} (the tier the action opens at), {@code drain} (stamina per
 * second while it is held) and {@code per_block} (stamina per block travelled).</p>
 *
 * <p>The server config's {@code staminaCostScale} and {@code cooldownScale} multiply everything after the table.
 * Activation and the HUD use the same numbers.</p>
 */
public final class PowerBalance {
    public record Tuning(String power, int energy, int cooldown, int baseEnergy, int baseCooldown, int unlock,
                         double drain, double perBlock) {}
    private static final Map<String, Tuning> ACTIONS = load();
    private PowerBalance() {}

    private static Map<String, Tuning> load() {
        try (var stream = PowerBalance.class.getResourceAsStream("/data/heroesevolved/power_balance.json")) {
            if (stream == null) throw new IllegalStateException("Missing power balance");
            Map<String, Tuning> result = new HashMap<>();
            for (var element : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject o = element.getAsJsonObject();
                var tuning = new Tuning(o.get("power").getAsString(), integer(o, "energy", -1), integer(o, "cooldown", -1),
                        integer(o, "base_energy", 0), integer(o, "base_cooldown", 0), integer(o, "unlock", 0),
                        decimal(o, "drain"), decimal(o, "per_block"));
                if (tuning.energy > 1000 || tuning.cooldown > 20 * 60 * 30 || tuning.unlock < 0 || tuning.unlock > 5
                        || tuning.drain < 0 || tuning.perBlock < 0 || result.put(o.get("action").getAsString(), tuning) != null)
                    throw new IllegalStateException("Invalid power balance entry " + o);
            }
            return Map.copyOf(result);
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }

    private static int integer(JsonObject o, String key, int fallback) { return o.has(key) ? o.get(key).getAsInt() : fallback; }
    private static double decimal(JsonObject o, String key) {
        double value = o.has(key) ? o.get(key).getAsDouble() : 0;
        if (!Double.isFinite(value)) throw new IllegalStateException("Invalid power balance number " + key);
        return value;
    }

    public static Map<String, Tuning> entries() { return ACTIONS; }

    public static Tuning tuning(ResourceLocation action) {
        return action.getNamespace().equals("heroesevolved") ? ACTIONS.get(action.getPath()) : null;
    }

    public static Tuning tuning(String action) { return ACTIONS.get(action); }

    public static int energy(ResourceLocation action, int configured) { return energy(tuning(action), configured); }
    public static int cooldown(ResourceLocation action, int configured) { return cooldown(tuning(action), configured); }

    /** A fighting-style move's stamina: {@code style_<power>_<move>} in the table. */
    public static int styleEnergy(String power, String move, int configured) { return energy(ACTIONS.get("style_" + power + "_" + move), configured); }
    public static int styleCooldown(String power, String move, int configured) { return cooldown(ACTIONS.get("style_" + power + "_" + move), configured); }

    /** Any entry by its name in the table (a finisher's {@code finisher_<power>}). */
    public static int energy(String action, int configured) { return energy(ACTIONS.get(action), configured); }
    public static int cooldown(String action, int configured) { return cooldown(ACTIONS.get(action), configured); }

    private static int energy(Tuning tuning, int configured) {
        if (configured <= 0) return 0;
        // An entry with no stamina of its own passes the code's value through (Mimicry's Echoes already pay the
        // copied move's balanced cost).
        if (tuning != null && tuning.energy < 0) return configured;
        double value = tuning == null ? configured
                : tuning.baseEnergy > 0 ? tuning.energy * (double) configured / tuning.baseEnergy : tuning.energy;
        return Math.max(1, (int) Math.round(value * costScale()));
    }

    private static int cooldown(Tuning tuning, int configured) {
        if (configured <= 0) return 0;
        if (tuning != null && tuning.cooldown < 0) return configured;
        double value = tuning == null ? configured
                : tuning.baseCooldown > 0 ? tuning.cooldown * (double) configured / tuning.baseCooldown : tuning.cooldown;
        return Math.max(1, (int) Math.round(value * cooldownScale()));
    }

    /** The tier an action opens at: the table's, or the code's own when the table doesn't say. */
    public static int unlock(ResourceLocation action, int fallback) {
        Tuning tuning = tuning(action);
        return tuning != null && tuning.unlock > 0 ? tuning.unlock : fallback;
    }

    /** Stamina a second while an action is held (unscaled: {@link com.jayptucker.heroesevolved.energy.StaminaDrain} scales it). */
    public static double drain(String action, double fallback) {
        Tuning tuning = ACTIONS.get(action);
        return tuning != null && tuning.drain > 0 ? tuning.drain : fallback;
    }

    /** Stamina per block travelled. */
    public static double perBlock(String action, double fallback) {
        Tuning tuning = ACTIONS.get(action);
        return tuning != null && tuning.perBlock > 0 ? tuning.perBlock : fallback;
    }

    /** A per-second drain already scaled by the server's cost scale, for code that drains on its own. */
    public static double scaledDrain(String action, double fallback) {
        return drain(action, fallback) * costScale();
    }

    /**
     * How a cost reads in menus: "free", "18 stamina", "20 + 12/s stamina" for a held power, "4 + 0.3/block stamina"
     * for one that charges by distance. {@code energy} is the cost the server reported.
     */
    public static String describe(String action, int energy) {
        Tuning tuning = ACTIONS.get(action);
        double drain = tuning == null ? 0 : tuning.drain * costScale(), perBlock = tuning == null ? 0 : tuning.perBlock * costScale();
        if (energy <= 0 && drain <= 0 && perBlock <= 0) return "free";
        String base = String.valueOf(Math.max(0, energy));
        if (drain > 0) return base + " + " + trim(drain) + "/s stamina";
        if (perBlock > 0) return base + " + " + trim(perBlock) + "/block stamina";
        return base + " stamina";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public static double costScale() {
        try { return com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.staminaCostScale.get(); }
        catch (RuntimeException notLoaded) { return 1; }
    }

    public static double cooldownScale() {
        try { return com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.cooldownScale.get(); }
        catch (RuntimeException notLoaded) { return 1; }
    }
}
