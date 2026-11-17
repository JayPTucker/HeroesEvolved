package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.flight.FlightSpeedControl;
import com.jayptucker.heroesevolved.skills.Loadout;
import com.jayptucker.heroesevolved.network.ActionInputPayload;
import com.jayptucker.heroesevolved.network.FlightSpeedInputPayload;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.PyroFlightInputPayload;
import com.jayptucker.heroesevolved.network.PyroStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Raw controls drive the server's jets; suppress vanilla creative-flight acceleration. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class PyroFlightClient {
    private static PyroStatePayload state = new PyroStatePayload(false, false, false, 0, false);
    private static long seen;
    private static boolean jumpWasDown;
    private static long lastJumpPress = Long.MIN_VALUE;
    private static double scrollRemainder;
    private PyroFlightClient() {}
    public static void update(PyroStatePayload payload) {
        state = payload;
        Minecraft mc = Minecraft.getInstance();
        seen = mc.level == null ? 0 : mc.level.getGameTime();
    }
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && state.flying() && mc.level.getGameTime() - seen <= 20;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            state = new PyroStatePayload(false, false, false, 0, false);
            jumpWasDown = false; lastJumpPress = Long.MIN_VALUE; scrollRemainder = 0;
            return;
        }
        handleDoubleTap(mc);
        if (!active() || mc.player.tickCount % 2 != 0) return;
        boolean input = mc.screen == null && !mc.isPaused() && (!TelepathyClient.immobilized() || TelepathyClient.role() == 2);
        PacketDistributor.sendToServer(new PyroFlightInputPayload(
                input ? axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown()) : 0,
                input ? axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown()) : 0,
                input && mc.options.keyJump.isDown(), input && mc.options.keyShift.isDown(), input && mc.options.keySprint.isDown()));
    }
    private static int pyroLevel() {
        return ClientPowerState.getSnapshot().abilities().stream()
                .filter(a -> a.unlocked() && a.abilityId().equals(ModAbilities.PYROKINESIS_ID))
                .mapToInt(a -> a.level()).findFirst().orElse(0);
    }
    private static void handleDoubleTap(Minecraft mc) {
        if (mc.screen != null || mc.isPaused() || pyroLevel() < 3
                || TelepathyClient.immobilized() && TelepathyClient.role() != 2) {
            jumpWasDown = false; lastJumpPress = Long.MIN_VALUE;
            return;
        }
        boolean down = mc.options.keyJump.isDown();
        if (down && !jumpWasDown) {
            long now = mc.level.getGameTime();
            if (lastJumpPress != Long.MIN_VALUE && now - lastJumpPress <= 7) {
                // Resolve the base action directly, even when a learned technique occupies X.
                PacketDistributor.sendToServer(new ActionInputPayload(
                        0, Loadout.BASE + AbilitySlot.QUATERNARY.name(), true, false));
                lastJumpPress = Long.MIN_VALUE;
            } else lastJumpPress = now;
        }
        jumpWasDown = down;
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.screen != null || mc.isPaused() || state.diving()
                || !mc.player.isAlive() || mc.player.isSpectator()
                || TelepathyClient.immobilized() && TelepathyClient.role() != 2) {
            scrollRemainder = 0;
            return;
        }
        var actor = mc.level.getEntity(TelepathyClient.bodyId());
        if (actor == null || actor.onGround()) { scrollRemainder = 0; return; }
        double delta = event.getScrollDeltaY();
        if (!Double.isFinite(delta) || delta == 0) return;
        event.setCanceled(true);
        if (Math.signum(delta) != Math.signum(scrollRemainder)) scrollRemainder = 0;
        scrollRemainder += delta;
        int notches = Math.min(4, (int) Math.abs(scrollRemainder));
        for (int i = 0; i < notches; i++)
            PacketDistributor.sendToServer(new FlightSpeedInputPayload(delta > 0 ? 1 : -1));
        scrollRemainder = Math.copySign(Math.abs(scrollRemainder) % 1, delta);
    }
    private static float axis(boolean positive, boolean negative) { return (positive ? 1 : 0) - (negative ? 1 : 0); }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
        if (!active()) return;
        event.getInput().forwardImpulse = 0;
        event.getInput().leftImpulse = 0;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }
    private static void render(GuiGraphics gui, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !state.pyro()
                || mc.level.getGameTime() - seen > 20 || ClientPowerState.getSnapshot().abilities().stream()
                .noneMatch(a -> a.unlocked() && a.abilityId().equals(ModAbilities.PYROKINESIS_ID))) return;
        // Heat and flight Momentum are rings in the ability HUD's row (gauges); only the controls stay as words.
        int center = gui.guiWidth() / 2;
        int color = state.blue() ? 0xFF68C5FF : 0xFFFF943D;
        if (active()) {
            gui.drawCenteredString(mc.font, Component.translatable(state.diving() ? "pyro.heroesevolved.meteor" : "pyro.heroesevolved.flight_controls"), center, 34, color);
            gui.drawCenteredString(mc.font, Component.translatable("pyro.heroesevolved.flight_stop"), center, 46, 0xE8D5B8);
        }
    }

    /** Heat (blue when the flames are), and in flame flight its Momentum with pips for the speed setting. */
    public static void gauges(java.util.List<com.jayptucker.heroesevolved.gui.HudGauges.Gauge> gauges) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !state.pyro() || mc.level.getGameTime() - seen > 20
                || ClientPowerState.getSnapshot().abilities().stream().noneMatch(a -> a.unlocked() && a.abilityId().equals(ModAbilities.PYROKINESIS_ID)))
            return;
        int heat = Math.clamp(state.heat(), 0, 100);
        int color = state.blue() ? 0xFF68C5FF : 0xFFFF943D;
        gauges.add(com.jayptucker.heroesevolved.gui.HudGauges.Gauge.ring(heat / 100F, color).middle(Integer.toString(heat))
                .label(state.blue() ? "BLUE HEAT" : "HEAT"));
        if (active()) {
            int maximum = FlightSpeedControl.maximumStep(pyroLevel()), step = Math.clamp(state.speedStep(), 0, maximum);
            int momentum = Math.clamp(state.momentum(), 0, 100);
            gauges.add(com.jayptucker.heroesevolved.gui.HudGauges.Gauge.ring(momentum / 100F, color).middle(Integer.toString(momentum))
                    .label(state.boosting() ? "BOOST" : "FLIGHT " + step).pips(maximum, step, color));
        }
    }
    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Hud {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "pyro_heat"), PyroFlightClient::render);
        }
    }
}
