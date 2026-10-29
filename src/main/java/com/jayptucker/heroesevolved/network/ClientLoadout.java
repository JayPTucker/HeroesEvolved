package com.jayptucker.heroesevolved.network;

import java.util.List;

/** The local player's loadout and Power Level, as the server last sent them. */
public final class ClientLoadout {
    private static volatile LoadoutPayload state = new LoadoutPayload(1, 0, 1, false, "", 0, 1, 0, List.of(), List.of());
    private static volatile boolean known;

    private ClientLoadout() {}

    public static void update(LoadoutPayload payload) { state = payload; known = true; }

    public static LoadoutPayload get() { return state; }

    public static boolean known() { return known; }

    public static void clear() { state = new LoadoutPayload(1, 0, 1, false, "", 0, 1, 0, List.of(), List.of()); known = false; }

    /** The action key on key {@code index} (1 to 8), or "". */
    public static String onKey(int index) {
        var keys = state.keys();
        return index >= 1 && index <= keys.size() ? keys.get(index - 1) : "";
    }

    /** Which key (1 to 8) an action is on, or 0. */
    public static int keyOf(String actionKey) {
        var keys = state.keys();
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).equals(actionKey)) return i + 1;
        return 0;
    }

    public static LoadoutPayload.Entry entry(String actionKey) {
        return state.entries().stream().filter(e -> e.actionKey().equals(actionKey)).findFirst().orElse(null);
    }
}
