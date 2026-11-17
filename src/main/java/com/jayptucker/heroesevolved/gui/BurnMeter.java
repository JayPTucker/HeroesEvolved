package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.client.BurnClient;
import com.jayptucker.heroesevolved.speed.SpeedFriction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The burn meter, drawn under Momentum in the ability HUD whenever there's anything to show:
 * <ul>
 *   <li><b>Friction</b>: a speedster's heat, 0 to 100, with notches where the skin starts to scorch (50) and where it
 *   starts to burn them (70). Amber, then orange, then a flashing red "Burning up!" past the second notch.</li>
 *   <li><b>Skin</b>: how much of their skin has burnt away, from anything (friction, a meltdown, radiation), in ember red
 *   while it burns and green as it grows back.</li>
 * </ul>
 */
public final class BurnMeter {
    private static final int WIDTH = 100, LINE = 12;
    private static final int AMBER = 0xFFFFC23A, ORANGE = 0xFFFF8A1E, RED = 0xFFFF3B1E, PALE = 0xFFFFF6D8,
            EMBER = 0xFFE2541E, GROWING = 0xFF7AD66A, TRACK = 0x80302018, NOTCH = 0xC0FFF6D8;
    private static float friction;

    private BurnMeter() {}

    /** The server's word on this speedster's friction heat. */
    public static void friction(float heat) { friction = Math.max(0, Math.min(SpeedFriction.MAX, heat)); }

    /** Draws the meter at {@code x, y} if there is anything to show; returns the y below it. */
    public static int draw(GuiGraphics g, int x, int y) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return y;
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        if (friction > 0.5F) {
            boolean burning = friction >= SpeedFriction.BURN, scorching = friction >= SpeedFriction.SCORCH;
            int color = burning ? ((time / 3) % 2 == 0 ? PALE : RED) : scorching ? ORANGE : AMBER;
            Component label = burning ? Component.translatable("hud.heroesevolved.burning_up")
                    : Component.translatable("hud.heroesevolved.friction", Math.round(friction));
            g.drawString(mc.font, label, x, y, color, true);
            int fill = Math.round(friction / SpeedFriction.MAX * WIDTH);
            g.fill(x, y + 10, x + WIDTH, y + 13, TRACK);
            g.fill(x, y + 10, x + fill, y + 13, burning ? RED : scorching ? ORANGE : AMBER);
            for (float mark : new float[] {SpeedFriction.SCORCH, SpeedFriction.BURN}) {
                int at = x + Math.round(mark / SpeedFriction.MAX * WIDTH);
                g.fill(at, y + 9, at + 1, y + 14, NOTCH);
            }
            y += LINE + 6;
        }
        float skin = BurnClient.level(mc.player, 1);
        if (skin > 0.01F) {
            boolean growing = BurnClient.regrowing(mc.player);
            int percent = Math.round(skin * 100);
            g.drawString(mc.font, Component.translatable(growing ? "hud.heroesevolved.skin_regrowing" : "hud.heroesevolved.skin_burnt", percent),
                    x, y, growing ? GROWING : EMBER, true);
            g.fill(x, y + 10, x + WIDTH, y + 13, TRACK);
            g.fill(x, y + 10, x + Math.round(skin * WIDTH), y + 13, growing ? GROWING : EMBER);
            y += LINE + 6;
        }
        return y;
    }
}
