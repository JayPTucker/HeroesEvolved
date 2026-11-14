package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * The story's narration for awakenings and trips into the past: where you are and what is happening (a caption), and what
 * to do now (an instruction). Both go to chat, once each, so nothing is laid over the scene: a caption is one line with
 * its subtitle in grey after it; an instruction is a line starting {@code »}, written only when it changes.
 */
public final class TimeIntroCaption {
    private static final TextColor CAPTION = TextColor.fromRgb(0xF5EADC), SUBTITLE = TextColor.fromRgb(0xA9A6A0),
            INSTRUCTION = TextColor.fromRgb(0xFFE3A8);
    /** The same caption again this soon is a repeat (a re-sent cue), not news. */
    private static final int REPEAT_TICKS = 200;

    private static String lastCaption;
    private static int lastCaptionAt = -REPEAT_TICKS;
    private static String lastHint;

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
        if (key != null) showKey(key);
    }

    public static void showKey(String translationKey) { showKey(translationKey, false); }

    /** Writes a caption to chat: its words, then its subtitle (if it has one) in grey. {@code urgent} is kept for callers. */
    public static void showKey(String translationKey, boolean urgent) {
        Minecraft mc = Minecraft.getInstance();
        int now = mc.gui.getGuiTicks();
        if (translationKey.equals(lastCaption) && now - lastCaptionAt < REPEAT_TICKS) return;
        lastCaption = translationKey;
        lastCaptionAt = now;
        MutableComponent line = Component.translatable(translationKey).withStyle(Style.EMPTY.withColor(CAPTION).withBold(true));
        if (I18n.exists(translationKey + "_subtitle"))
            line.append(Component.literal("  ").append(Component.translatable(translationKey + "_subtitle")
                    .withStyle(Style.EMPTY.withColor(SUBTITLE).withBold(false).withItalic(true))));
        mc.gui.getChat().addMessage(line);
    }

    /** Forgets the last caption, so a new scene's first one is always written. */
    public static void clear() { lastCaption = null; }

    /** What the player should do now, written to chat once (the same instruction again is not repeated). */
    public static void hint(Component text) { hint(text, -1); }

    /** As {@link #hint(Component)}; {@code ticks} is kept for callers (an instruction in chat doesn't expire). */
    public static void hint(Component text, int ticks) {
        String plain = text.getString();
        if (plain.equals(lastHint)) return;
        lastHint = plain;
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("» ").append(text)
                .withStyle(Style.EMPTY.withColor(INSTRUCTION)));
    }

    /** The current instruction is done with: the next one is written even if it reads the same. */
    public static void clearHint() { lastHint = null; }
}
