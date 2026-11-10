package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Cinematic captions for awakenings and trips into the past, and the instruction line that says what
 * to do now. A caption stays up as long as its words take to read; one that arrives while another is
 * still being read waits its turn. The instruction stays at the top of the screen until the next one,
 * the end of the scene, or its own time runs out.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TimeIntroCaption {
    /** A caption: what it says, how long it stays, how long before another may replace it. */
    private record Caption(String key, int duration, int minimum) {}
    private static Caption current;
    private static int started;
    private static final ArrayDeque<Caption> QUEUE = new ArrayDeque<>();
    /** Captions that must not wait long behind another, such as time stopping. */
    private static final int URGENT_MINIMUM = 40;

    private static Component hint;
    private static int hintStarted, hintUntil = -1;
    private static ResourceKey<Level> hintWhere;

    private TimeIntroCaption() {}

    public static void show(int stage) {
        String key = switch (stage) {
            case TimeIntroPayload.ARRIVAL -> "intro.heroesevolved.arrival";
            case TimeIntroPayload.DETONATION -> "intro.heroesevolved.blast";
            case TimeIntroPayload.RETURN -> "intro.heroesevolved.return";
            case TimeIntroPayload.AVENUE_ARRIVAL -> "intro.heroesevolved.avenue";
            case TimeIntroPayload.AVENUE_LIGHT -> "intro.heroesevolved.avenue_light";
            case TimeIntroPayload.AVENUE_ALL -> "intro.heroesevolved.avenue_all";
            case TimeIntroPayload.AVENUE_SOME -> "intro.heroesevolved.avenue_some";
            case TimeIntroPayload.AVENUE_NONE -> "intro.heroesevolved.avenue_none";
            case TimeIntroPayload.FREEZE -> "intro.heroesevolved.freeze";
            default -> null;
        };
        if (key == null) return;
        // A new place or coming home starts fresh: nothing from before waits in front of it.
        boolean scene = stage == TimeIntroPayload.ARRIVAL || stage == TimeIntroPayload.AVENUE_ARRIVAL || stage == TimeIntroPayload.RETURN
                || stage == TimeIntroPayload.AVENUE_ALL || stage == TimeIntroPayload.AVENUE_SOME || stage == TimeIntroPayload.AVENUE_NONE;
        if (scene) clear();
        showKey(key, stage == TimeIntroPayload.FREEZE);
    }

    public static void showKey(String translationKey) { showKey(translationKey, false); }

    /** Shows a caption now, or as soon as the one on screen has been read. */
    public static void showKey(String translationKey, boolean urgent) {
        Caption next = measure(translationKey);
        if (current == null) { start(next); return; }
        int shown = Minecraft.getInstance().gui.getGuiTicks() - started;
        if (QUEUE.isEmpty() && shown >= (urgent ? Math.min(URGENT_MINIMUM, current.minimum) : current.minimum)) { start(next); return; }
        if (urgent) QUEUE.clear();
        QUEUE.add(next);
        while (QUEUE.size() > 2) QUEUE.removeFirst();
    }

    /** How long a caption needs: about fourteen characters a second, with a moment either side. */
    private static Caption measure(String key) {
        int length = I18n.get(key).length() + (I18n.exists(key + "_subtitle") ? I18n.get(key + "_subtitle").length() : 0);
        int read = 40 + Math.round(length * 1.4F);
        return new Caption(key, Mth.clamp(read + 30, 110, 340), Mth.clamp(30 + Math.round(length * 1.1F), 60, 240));
    }

    private static void start(Caption caption) {
        current = caption;
        started = Minecraft.getInstance().gui.getGuiTicks();
    }

    public static void clear() {
        current = null;
        QUEUE.clear();
    }

    // ------------------------------------------------------------------
    // The instruction line
    // ------------------------------------------------------------------

    /** What the player should do now; stays until replaced, cleared or they leave this place. */
    public static void hint(Component text) { hint(text, -1); }

    /** As {@link #hint(Component)}, gone by itself after {@code ticks}. */
    public static void hint(Component text, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        hint = text;
        hintStarted = mc.gui.getGuiTicks();
        hintUntil = ticks < 0 ? -1 : hintStarted + ticks;
        hintWhere = mc.level == null ? null : mc.level.dimension();
    }

    public static void clearHint() { hint = null; }

    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID, "time_intro_caption"), TimeIntroCaption::render);
    }

    /** Where the stack of text ends: above the energy bar and the usual few lines of chat. */
    private static final float BOTTOM = 0.70F;
    /** The instruction line eases to its place when a caption comes or goes beneath it. */
    private static float hintY = -1;

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int bottom = Math.round(graphics.guiHeight() * BOTTOM);
        int captionTop = renderCaption(graphics, mc, partial, bottom);
        renderHint(graphics, mc, partial, captionTop < 0 ? bottom : captionTop - 8);
    }

    /** Draws the caption ending at {@code bottom}; returns where its band starts, or -1 if there is none. */
    private static int renderCaption(GuiGraphics graphics, Minecraft mc, float partial, int bottom) {
        if (current == null) return -1;
        float age = mc.gui.getGuiTicks() - started + partial;
        // Move on when this one has been read and another is waiting, or when its time is up.
        if (!QUEUE.isEmpty() && age >= current.minimum || age >= current.duration || age < 0) {
            Caption next = QUEUE.pollFirst();
            if (next == null) { current = null; return -1; }
            start(next);
            age = partial;
        }
        float fadeOut = QUEUE.isEmpty() ? Mth.clamp((current.duration - age) / 25, 0, 1) : 1;
        float fade = Math.min(Mth.clamp(age / 10, 0, 1), fadeOut);
        int alpha = (int) (fade * 255);
        if (alpha < 5) return -1;
        int center = graphics.guiWidth() / 2;
        int width = Math.min(450, graphics.guiWidth() - 32);
        List<FormattedCharSequence> title = mc.font.split(Component.translatable(current.key), width);
        List<FormattedCharSequence> subtitle = I18n.exists(current.key + "_subtitle")
                ? mc.font.split(Component.translatable(current.key + "_subtitle"), (int) (width / 0.85F)) : List.of();
        int widest = 0;
        for (FormattedCharSequence line : title) widest = Math.max(widest, mc.font.width(line));
        for (FormattedCharSequence line : subtitle) widest = Math.max(widest, Math.round(mc.font.width(line) * 0.85F));
        int height = title.size() * 11 + (subtitle.isEmpty() ? 0 : 3 + Math.round(subtitle.size() * 11 * 0.85F));
        int top = bottom - height;
        // A soft dark band behind the words, so they read against a white flash or a bright sky.
        graphics.fill(center - widest / 2 - 8, top - 5, center + widest / 2 + 8, bottom + 3, (int) (fade * 0x88) << 24);
        graphics.pose().pushPose();
        graphics.pose().translate(center, top, 0);
        int y = 0;
        for (FormattedCharSequence line : title) {
            graphics.drawString(mc.font, line, -mc.font.width(line) / 2, y, alpha << 24 | 0xF5EADC, true);
            y += 11;
        }
        graphics.pose().translate(0, y + 3, 0);
        graphics.pose().scale(0.85F, 0.85F, 1);
        y = 0;
        for (FormattedCharSequence line : subtitle) {
            graphics.drawString(mc.font, line, -mc.font.width(line) / 2, y, alpha << 24 | 0xD4D2CD, true);
            y += 11;
        }
        graphics.pose().popPose();
        return top - 5;
    }

    /** Draws the instruction line ending at {@code bottom}, easing there from wherever it was. */
    private static void renderHint(GuiGraphics graphics, Minecraft mc, float partial, int bottom) {
        if (hint == null) { hintY = -1; return; }
        if (hintWhere != null && !mc.level.dimension().equals(hintWhere)) { hint = null; return; }
        float age = mc.gui.getGuiTicks() - hintStarted + partial;
        if (hintUntil >= 0 && mc.gui.getGuiTicks() >= hintUntil) { hint = null; return; }
        float fade = Mth.clamp(age / 12, 0, 1);
        if (hintUntil >= 0) fade = Math.min(fade, Mth.clamp((hintUntil - mc.gui.getGuiTicks() - partial) / 20, 0, 1));
        int alpha = (int) (fade * 255);
        if (alpha < 5) return;
        int center = graphics.guiWidth() / 2;
        int width = Math.min(340, graphics.guiWidth() - 40);
        List<FormattedCharSequence> lines = mc.font.split(Component.literal("» ").append(hint), width);
        int widest = 0;
        for (FormattedCharSequence line : lines) widest = Math.max(widest, mc.font.width(line));
        float target = bottom - lines.size() * 11;
        hintY = hintY < 0 ? target : Mth.lerp(0.18F, hintY, target);
        int top = Math.round(hintY);
        graphics.fill(center - widest / 2 - 7, top - 4, center + widest / 2 + 7, top + lines.size() * 11 + 2, (int) (fade * 0x99) << 24);
        int y = top;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(mc.font, line, center - mc.font.width(line) / 2, y, alpha << 24 | 0xFFE3A8, true);
            y += 11;
        }
    }
}
