package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Compact, wrapped cinematic captions at normal HUD font size, scoped to this intro. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TimeIntroCaption {
    private static String key;
    private static int started;
    private TimeIntroCaption() {}

    public static void show(int stage) {
        showKey(switch (stage) {
            case TimeIntroPayload.ARRIVAL -> "intro.heroesevolved.arrival";
            case TimeIntroPayload.DETONATION -> "intro.heroesevolved.blast";
            case TimeIntroPayload.RETURN -> "intro.heroesevolved.return";
            default -> null;
        });
    }

    public static void showKey(String translationKey) {
        key = translationKey;
        started = Minecraft.getInstance().gui.getGuiTicks();
    }

    public static void clear() { key = null; }

    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID, "time_intro_caption"), TimeIntroCaption::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (key == null || mc.level == null || mc.options.hideGui) return;
        float age = mc.gui.getGuiTicks() - started + delta.getGameTimeDeltaPartialTick(false);
        if (age >= 100 || age < 0) { clear(); return; }
        float fade = Math.min(Mth.clamp(age / 10, 0, 1), Mth.clamp((100 - age) / 20, 0, 1));
        int alpha = (int) (fade * 255);
        if (alpha < 5) return;
        int center = graphics.guiWidth() / 2;
        int top = Math.round(graphics.guiHeight() * 0.72F);
        int width = Math.min(450, graphics.guiWidth() - 32);
        graphics.pose().pushPose();
        graphics.pose().translate(center, top, 0);
        int y = 0;
        for (FormattedCharSequence line : mc.font.split(Component.translatable(key), width)) {
            graphics.drawString(mc.font, line, -mc.font.width(line) / 2, y, alpha << 24 | 0xF5EADC, true);
            y += 11;
        }
        graphics.pose().translate(0, y + 3, 0);
        graphics.pose().scale(0.85F, 0.85F, 1);
        y = 0;
        for (FormattedCharSequence line : mc.font.split(Component.translatable(key + "_subtitle"), (int) (width / 0.85F))) {
            graphics.drawString(mc.font, line, -mc.font.width(line) / 2, y, alpha << 24 | 0xD4D2CD, true);
            y += 11;
        }
        graphics.pose().popPose();
    }
}
