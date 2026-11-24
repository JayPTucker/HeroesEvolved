package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.guidance.GuidanceData;
import com.jayptucker.heroesevolved.guidance.GuidanceData.Hint;
import com.jayptucker.heroesevolved.gui.screen.GuideScreen;
import com.jayptucker.heroesevolved.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** A small, non-modal field note. No queues of stale advice, repeated nags, or story overlays. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class PlayerGuidance {
    private static GuidanceData data = GuidanceData.empty();
    private static boolean known;
    private static boolean needsRest;
    private static int quietTicks, remaining;
    private static Hint current;

    private PlayerGuidance() {}
    public static void accept(GuidanceData next) { data = next; known = true; if (!next.enabled()) current = null; }
    public static boolean enabled() { return data.enabled(); }
    public static boolean known() { return known; }
    public static void toggle() {
        if (!known || Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new GuidanceActionPayload(data.enabled() ? 1 : 2, -1));
        accept(data.enabled(!data.enabled()));
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        data = GuidanceData.empty(); known = false; needsRest = false; quietTicks = 0; remaining = 0; current = null;
    }

    /** Shared by selection and rendering: suppressed in scenes, menus, flight, danger, and with F1. */
    private static boolean readable(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && !mc.isPaused() && !mc.options.hideGui
                && mc.level.dimension().equals(Level.OVERWORLD) && mc.player.isAlive() && !mc.player.isSpectator()
                && !mc.player.isSleeping() && mc.player.onGround() && !mc.player.isInWater() && !mc.player.isOnFire()
                && mc.player.hurtTime == 0 && TelepathyClient.role() == 0
                && (!ClientStamina.known() || ClientStamina.calm()) && !DazeView.active()
                && !com.jayptucker.heroesevolved.gui.TimeIntroCaption.recent()
                && !ClientScreenTintState.blackedOut() && TempestCamera.shot(1) == null;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (known && ClientStamina.known() && ClientStamina.maximum() > 0
                && ClientStamina.stamina() < ClientStamina.maximum() * .2F) needsRest = true;
        if (!known || !data.enabled() || !readable(mc)) return;
        if (current != null && --remaining <= 0) current = null;
        if (++quietTicks < 200 || current != null) return;
        Hint hint = choose(mc);
        if (hint == null) return;
        current = hint; remaining = 240; quietTicks = -600; // ten quiet seconds first; forty between notes.
    }

    private static Hint choose(Minecraft mc) {
        // Contextual blockers take priority over general introductions.
        boolean portal = mc.hitResult instanceof BlockHitResult hit
                && mc.level.getBlockState(hit.getBlockPos()).is(Blocks.END_PORTAL_FRAME)
                || mc.level.getBlockState(mc.player.blockPosition().below()).is(Blocks.END_PORTAL_FRAME);
        boolean awake = ClientPowerState.getSnapshot().abilities().stream().anyMatch(a -> a.unlocked());
        return data.next(new GuidanceData.Context(portal, awake, needsRest, ClientDistress.any(),
                ClientSkillTreeState.get().awakened(), FightingStyleClient.styled() && mc.player.getMainHandItem().isEmpty(),
                ClientLoadout.known() && ClientLoadout.get().level() >= 19 && !ClientLoadout.get().evolved()));
    }

    /** Also used by the render-only preview to photograph the exact in-game card. */
    public static void draw(GuiGraphics g, Hint hint) {
        Minecraft mc = Minecraft.getInstance();
        int width = Math.min(240, g.guiWidth() - 16), x = g.guiWidth() - width - 8, y = 8;
        var lines = mc.font.split(GuideScreen.text(hint.key()), width - 18);
        var footer = mc.font.split(GuideScreen.text("guide.heroesevolved.more"), width - 18);
        int height = 28 + (lines.size() + footer.size()) * 10;
        g.fill(x, y, x + width, y + height, 0xF018100C);
        g.fill(x, y, x + 2, y + height, 0xFFF2D27A);
        g.drawString(mc.font, Component.translatable("guide.heroesevolved.note").append(" · ")
                .append(Component.translatable("guide.heroesevolved." + hint.topic + ".title")), x + 9, y + 7, 0xFFF2D27A, false);
        int lineY = y + 21;
        for (var line : lines) { g.drawString(mc.font, line, x + 9, lineY, 0xFFEDE3D6, false); lineY += 10; }
        for (var line : footer) { g.drawString(mc.font, line, x + 9, lineY + 5, 0xFFB8A99A, false); lineY += 10; }
    }

    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent e) {
            e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "field_notes"), (g, delta) -> {
                if (known && data.enabled() && current != null && readable(Minecraft.getInstance())) {
                    draw(g, current);
                    // Only a rendered note is a receipt; menus, hidden HUDs and canceled layers cannot consume it.
                    if (!data.hasSeen(current)) {
                        data = data.mark(current);
                        PacketDistributor.sendToServer(new GuidanceActionPayload(0, current.id));
                    }
                }
            });
        }
    }
}
