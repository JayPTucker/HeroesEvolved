package com.jayptucker.heroesevolved.network;

/**
 * The client's picture of its own stamina, for the HUD. Packets set it; between them it runs forward on the
 * server's rates each client tick, so the bar fills and drains smoothly without a packet a tick. It also keeps
 * what the HUD animates: a trailing "just spent" value and when the last spend happened.
 */
public final class ClientStamina {
    private static float stamina, shown, trail, maximum, regen, drain;
    private static int flags, pause, sinceSpend = 1000, sinceGain = 1000, sinceRefusal = 1000;
    private static boolean known;

    private ClientStamina() {}

    static void receive(StaminaPayload payload) {
        float before = stamina;
        stamina = Math.min(payload.stamina(), payload.maximum());
        maximum = payload.maximum();
        regen = payload.regen();
        drain = payload.drain() < .5F ? 0 : payload.drain();
        flags = payload.flags();
        pause = payload.pauseTicks();
        if (!known) { shown = trail = stamina; known = true; }
        // A held power's steady drain isn't a spend: only a drop beyond what the drain explains starts the trail.
        float explained = drain / 20F * 12;
        if (stamina < before - .75F - explained) sinceSpend = 0;
        if (stamina > before + 4F) sinceGain = 0;
        if ((payload.flags() & StaminaPayload.REFUSED) != 0) sinceRefusal = 0;
    }

    /** A full power sync also carries whole-point stamina; it only corrects the value if it drifted. */
    static void correct(int energy, int max) {
        maximum = max;
        if (!known || Math.abs(energy - stamina) >= 1) {
            if (known && energy < stamina - .75F) sinceSpend = 0;
            stamina = Math.min(energy, max);
            if (!known) { shown = trail = stamina; known = true; }
        }
    }

    /** The HUD flashes the bar when a move is refused for lack of stamina. */
    public static void refused() { sinceRefusal = 0; }

    /** Once a client tick. */
    public static void tick() {
        if (!known || maximum <= 0) return;
        if (pause > 0) pause--;
        float rate = (pause > 0 ? 0 : regen) - drain;
        if ((flags & StaminaPayload.UNLIMITED) != 0) rate = 0;
        stamina = Math.clamp(stamina + rate / 20F, 0, maximum);
        // The drawn value eases toward the real one; the trail waits a moment and then follows, which draws
        // what was just spent as a pale chunk that shrinks away.
        shown += (stamina - shown) * .45F;
        if (shown > trail) trail = shown;
        else if (sinceSpend > 8) trail += (shown - trail) * (drain > 0 ? .5F : .18F);
        sinceSpend++; sinceGain++; sinceRefusal++;
    }

    public static boolean known() { return known && maximum > 0; }
    public static float stamina() { return stamina; }
    public static float shown() { return shown; }
    public static float trail() { return trail; }
    public static float maximum() { return maximum; }
    public static float regen() { return regen; }
    public static float drain() { return drain; }
    public static boolean paused() { return pause > 0 || (flags & StaminaPayload.PAUSED) != 0 && regen <= 0; }
    public static boolean calm() { return (flags & StaminaPayload.CALM) != 0; }
    public static boolean held() { return (flags & StaminaPayload.HELD) != 0 || drain > 0; }
    public static boolean stillWorld() { return (flags & StaminaPayload.STILL_WORLD) != 0; }
    public static boolean betweenSeconds() { return (flags & StaminaPayload.BETWEEN_SECONDS) != 0; }
    public static boolean unlimited() { return (flags & StaminaPayload.UNLIMITED) != 0; }
    public static int sinceSpend() { return sinceSpend; }
    public static int pauseTicks() { return pause; }
    public static int sinceGain() { return sinceGain; }
    public static int sinceRefusal() { return sinceRefusal; }

    public static void reset() { known = false; stamina = shown = trail = maximum = regen = drain = 0; flags = pause = 0; }
}
