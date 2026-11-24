package com.jayptucker.heroesevolved.network;

/** The local player's loadout and Power Level, as the server last sent them. */
public final class ClientLoadout {
    private static volatile LoadoutPayload state = LoadoutPayload.EMPTY;
    private static volatile boolean known;
    private static String lastWheelAction = "";

    private ClientLoadout() {}

    public static void update(LoadoutPayload payload) {
        if (!state.power().equals(payload.power()) || !payload.wheel().contains(lastWheelAction)) lastWheelAction = "";
        state = payload;
        known = true;
    }

    public static LoadoutPayload get() { return state; }

    public static boolean known() { return known; }

    public static void clear() { state = LoadoutPayload.EMPTY; known = false; lastWheelAction = ""; }

    public static String onWheel(int slot) {
        var wheel = state.wheel();
        return slot >= 1 && slot <= wheel.size() ? wheel.get(slot - 1) : "";
    }

    public static int wheelPosition(String actionKey) {
        return actionKey.isEmpty() ? 0 : state.wheel().indexOf(actionKey) + 1;
    }

    public static void rememberWheelAction(String actionKey) {
        lastWheelAction = actionKey;
    }

    public static LoadoutPayload.Entry quickCast() {
        String pinned = state.quickCast();
        return entry(pinned.isEmpty() ? lastWheelAction : pinned);
    }

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

    /** A skill this power has with no key (Flight's Barrel Roll), or null. */
    public static LoadoutPayload.Entry innate(String actionKey) {
        return state.innate().stream().filter(e -> e.actionKey().equals(actionKey)).findFirst().orElse(null);
    }
}
