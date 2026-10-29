package com.jayptucker.heroesevolved.network;

/** The local player's Regrowth and downed state, for the HUD. */
public final class ClientRegenerationState {
    private static volatile int regrowth, recoveryTicks, giveUp = -1;
    private static volatile long upAt;

    private ClientRegenerationState() {}

    static void update(RegenerationStatePayload payload) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        regrowth = Math.clamp(payload.regrowth(), 0, 100);
        recoveryTicks = Math.max(0, payload.recoveryTicks());
        giveUp = payload.giveUp();
        upAt = payload.downedTicks() <= 0 ? 0 : (level == null ? 0 : level.getGameTime()) + payload.downedTicks();
    }

    public static int regrowth() { return regrowth; }

    /** How long a lethal blow would keep you down right now. */
    public static int recoveryTicks() { return recoveryTicks; }

    /** Ticks until you get up; 0 when standing. */
    public static long downedRemaining() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return upAt == 0 || level == null ? 0 : Math.max(0, upAt - level.getGameTime());
    }

    public static boolean downed() { return downedRemaining() > 0; }

    /** 0 to 60 while holding the give-up key; -1 when giving up isn't available yet. */
    public static int giveUp() { return giveUp; }

    public static void clear() { regrowth = 0; upAt = 0; giveUp = -1; }
}
