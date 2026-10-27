package com.jayptucker.heroesevolved.network;

import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Active screen tints on the client. Timed with the real-time clock rather
 * than world time, so nothing can carry over between worlds.
 */
public final class ClientScreenTintState {
    private static final int MAXIMUM_TINTS = 8;
    private static final List<Tint> TINTS = new ArrayList<>();

    private ClientScreenTintState() {
    }

    public static void add(ScreenTintPayload payload) {
        // A zero duration cancels a matching tint early (e.g. a paradox
        // resolved before its timer ran out).
        if (payload.durationTicks() <= 0) {
            TINTS.removeIf(tint -> tint.color == payload.color()
                    && tint.style == payload.style());
            return;
        }
        long now = Util.getMillis();
        long end = now + payload.durationTicks() * 50L;

        // Effects like Time Stop refresh their tint every few ticks while
        // you stay inside them. Extending the existing tint keeps it steady
        // instead of stacking copies that flicker as they overlap.
        for (int index = 0; index < TINTS.size(); index++) {
            Tint existing = TINTS.get(index);
            if (existing.color == payload.color()
                    && existing.style == payload.style()
                    && existing.endMillis > now) {
                TINTS.set(index, new Tint(existing.color, existing.style,
                        existing.startMillis, Math.max(existing.endMillis, end)));
                return;
            }
        }

        TINTS.add(new Tint(payload.color(), payload.style(), now, end));
        while (TINTS.size() > MAXIMUM_TINTS) {
            TINTS.removeFirst();
        }
    }

    public static void clear() {
        TINTS.clear();
    }

    /** Live tints with their current strength (0-1); expired ones are dropped. */
    public static List<ActiveTint> active() {
        long now = Util.getMillis();
        TINTS.removeIf(tint -> now >= tint.endMillis);

        List<ActiveTint> active = new ArrayList<>(TINTS.size());
        for (Tint tint : TINTS) {
            double life = (double) (now - tint.startMillis)
                    / Math.max(1L, tint.endMillis - tint.startMillis);
            double strength = switch (tint.style) {
                case FLASH, VIGNETTE -> 1.0D - life;
                // A held tint fades in quickly and out over its last 15%.
                case HOLD, EDGE -> Math.min(Math.min(1.0D, life * 8.0D),
                        (1.0D - life) / 0.15D);
                // With reduced flashing a pulse holds steady instead of throbbing.
                case PULSE -> (com.jayptucker.heroesevolved.client.VisualComfort.reduceFlashing()
                        ? 0.6D : 0.55D + 0.45D * Math.sin(now / 160.0D))
                        * Math.min(1.0D, (1.0D - life) / 0.15D);
            };
            active.add(new ActiveTint(tint.color, tint.style,
                    (float) Math.clamp(strength, 0.0D, 1.0D)));
        }
        return active;
    }

    public record ActiveTint(int color, ScreenTintPayload.Style style, float strength) {
    }

    private record Tint(
            int color,
            ScreenTintPayload.Style style,
            long startMillis,
            long endMillis
    ) {
    }
}
