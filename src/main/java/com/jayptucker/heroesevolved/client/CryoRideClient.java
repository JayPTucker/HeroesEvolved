package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.CryoRideInputPayload;
import com.jayptucker.heroesevolved.network.CryoRideStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;

/** The skating pose is shared with observers; raw controls move only the server-owned ribbon. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class CryoRideClient {
    private record View(int step, float speed, long seen) {}
    private static final Map<Integer, View> RIDERS = new HashMap<>();
    private static double scroll;
    private CryoRideClient() {}
    public static void update(CryoRideStatePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Float.isFinite(p.speed())) return;
        if (p.active()) RIDERS.put(p.entityId(), new View(Math.clamp(p.speedStep(), 1, 3), Math.clamp(p.speed(), 0, 3), mc.level.getGameTime()));
        else RIDERS.remove(p.entityId());
    }
    public static boolean isRiding(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        View view = RIDERS.get(entityId);
        return mc.level != null && view != null && mc.level.getGameTime() - view.seen <= 20;
    }
    public static boolean active() {
        var mc = Minecraft.getInstance();
        return mc.player != null && isRiding(TelepathyClient.bodyId());
    }
    public static float speed(int id) { View v = RIDERS.get(id); return v == null ? 0 : v.speed; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { RIDERS.clear(); scroll = 0; return; }
        RIDERS.values().removeIf(v -> mc.level.getGameTime() - v.seen > 40);
        if (!active() || mc.player.tickCount % 2 != 0) return;
        boolean allowed = mc.screen == null && !mc.isPaused() && (!TelepathyClient.immobilized() || TelepathyClient.role() == 2);
        PacketDistributor.sendToServer(new CryoRideInputPayload(
                allowed ? axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown()) : 0,
                allowed ? axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown()) : 0,
                allowed && mc.options.keyJump.isDown(), !allowed || mc.options.keyShift.isDown(),
                allowed && mc.options.keySprint.isDown(), 0));
    }
    private static float axis(boolean positive, boolean negative) { return (positive ? 1 : 0) - (negative ? 1 : 0); }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
        if (!active()) return;
        event.getInput().forwardImpulse = 0; event.getInput().leftImpulse = 0;
        event.getInput().jumping = false; event.getInput().shiftKeyDown = false;
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.screen != null || mc.isPaused()) { scroll = 0; return; }
        double delta = event.getScrollDeltaY();
        if (!Double.isFinite(delta) || delta == 0) return;
        event.setCanceled(true);
        if (Math.signum(delta) != Math.signum(scroll)) scroll = 0;
        scroll += delta;
        int notches = Math.min(3, (int) Math.abs(scroll));
        for (int i = 0; i < notches; i++) PacketDistributor.sendToServer(new CryoRideInputPayload(0, 0, false, true, false, delta > 0 ? 1 : -1));
        scroll = Math.copySign(Math.abs(scroll) % 1, delta);
    }
    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Hud {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "glacier_ride"), (gui, delta) -> {
                var mc = Minecraft.getInstance();
                if (!active() || mc.options.hideGui) return;
                View view = RIDERS.get(TelepathyClient.bodyId());
                gui.drawCenteredString(mc.font, Component.literal("GLACIER RIDE  ·  WASD steer  ·  Space rise  ·  Shift brake"), gui.guiWidth() / 2, 34, 0xC1EEFF);
                gui.drawCenteredString(mc.font, Component.literal("Scroll speed: " + view.step + "  ·  Sprint carve  ·  Ride key: stop"), gui.guiWidth() / 2, 46, 0xAACDDF);
            });
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel().isClientSide()) RIDERS.clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { RIDERS.clear(); scroll = 0; }
}
