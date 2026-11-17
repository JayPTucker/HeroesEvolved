package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.CycloneSpeedInputPayload;
import com.jayptucker.heroesevolved.network.CycloneStatusPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Your own Cyclone, as the server last told it: while one runs, the scroll wheel sets how fast you run it (slow, a
 * vacuum; fast, the wind that rises into a tornado), and the HUD shows its ring.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientCyclone {
    private static volatile CycloneStatusPayload state = new CycloneStatusPayload(0, 0, 0);
    private static double remainder;

    private ClientCyclone() {}

    public static void set(CycloneStatusPayload payload) { state = payload; }

    public static boolean active() { return state.speed() > 0; }
    public static int speed() { return state.speed(); }
    public static int maximum() { return state.maximum(); }
    public static int mode() { return state.mode(); }

    /** Ahead of the other scroll handlers: while you run a Cyclone, the wheel is its speed. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || mc.screen != null) { remainder = 0; return; }
        double delta = event.getScrollDeltaY();
        if (!Double.isFinite(delta) || delta == 0) return;
        event.setCanceled(true);
        if (Math.signum(delta) != Math.signum(remainder)) remainder = 0;
        remainder += delta;
        int notches = Math.min(3, (int) Math.abs(remainder));
        for (int i = 0; i < notches; i++) PacketDistributor.sendToServer(new CycloneSpeedInputPayload(delta > 0 ? 1 : -1));
        remainder = Math.copySign(Math.abs(remainder) % 1, delta);
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { state = new CycloneStatusPayload(0, 0, 0); }
}
