package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.client.BurnClient;
import com.jayptucker.heroesevolved.network.ClientMomentum;
import com.jayptucker.heroesevolved.network.ClientOverrun;
import com.jayptucker.heroesevolved.network.OverrunStatusPayload;
import com.jayptucker.heroesevolved.speed.SpeedFriction;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The ability HUD's readouts as one compact row of ring gauges under the ability keys (wrapping if it ever runs out of
 * room). Each power adds its rings with {@link Gauge}; each ring is shown only while it has something to say. A ring
 * fills clockwise from the top (or both ways, for a two-sided dial), carries notches at the thresholds that matter, its
 * value in the middle, and a tiny label or a row of pips (for a setting) under it.
 * <p>
 * This class also builds the rings that aren't tied to one power: a speedster's Momentum, run speed and Overrun
 * ({@link #speed}), and anyone's friction heat and burnt skin ({@link #burn}).
 */
public final class HudGauges {
    /** Ring size and spacing, in GUI pixels. */
    public static final int SIZE = 22, STEP = 28, THICK = 3, ROW = SIZE + 11;
    public static final int LABEL = 0xFFCFC6B8;
    private static final int TRACK = 0x90201810, NOTCH = 0xE0FFF6D8;
    private static final int GOLD = 0xFFFFC23A, ORANGE = 0xFFFF8A1E, PALE = 0xFFFFF6D8, VIOLET = 0xFFB49CFF,
            VIOLET_DIM = 0xFF6A5C9A, AMBER = 0xFFFFC23A, RED = 0xFFFF3B1E, EMBER = 0xFFE2541E, GROWING = 0xFF7AD66A;
    private static float friction;

    private HudGauges() {}

    /** One ring. Build with {@link #ring} (or {@link #dial}) and the setters, which return the gauge. */
    public static final class Gauge {
        float from, to;
        int color, middleColor, labelColor = LABEL, pips, pipsLit, pipColor;
        String middle = "", label = "";
        float[] notches = {};
        boolean rainbow;

        private Gauge() {}

        /** A ring filled {@code fill} of the way round (0 to 1), clockwise from the top. */
        public static Gauge ring(float fill, int color) {
            Gauge g = new Gauge();
            g.to = Mth.clamp(fill, 0, 1);
            g.color = color;
            g.middleColor = color;
            return g;
        }

        /** A two-sided dial: negative fills anticlockwise from the top, positive clockwise (-1 to 1, half the ring each way). */
        public static Gauge dial(float value, int color) {
            Gauge g = ring(0, color);
            float v = Mth.clamp(value, -1, 1) * 0.5F;
            g.from = Math.min(0, v);
            g.to = Math.max(0, v);
            return g;
        }

        public Gauge middle(String text) { middle = text; return this; }
        public Gauge middle(String text, int color) { middle = text; middleColor = color; return this; }
        public Gauge label(String text) { label = text; return this; }
        public Gauge label(String text, int color) { label = text; labelColor = color; return this; }
        /** Notches at these fractions of the way round (negative for the anticlockwise side of a dial). */
        public Gauge notches(float... at) { notches = at; return this; }
        /** Pips under the ring for a setting ({@code lit} of {@code count}), with the label drawn small beneath them. */
        public Gauge pips(int count, int lit, int color) { pips = count; pipsLit = lit; pipColor = color; return this; }
        /** The filled arc runs through every colour, shifting with time. */
        public Gauge rainbow() { rainbow = true; return this; }
    }

    /** The server's word on this speedster's friction heat. */
    public static void friction(float heat) { friction = Math.max(0, Math.min(SpeedFriction.MAX, heat)); }

    /** Whether to flash this tick (for readouts at their limit). */
    public static boolean flash() {
        var level = Minecraft.getInstance().level;
        return level != null && (level.getGameTime() / 3) % 2 == 0;
    }

    /** A time in ticks as m:ss. */
    public static String clock(long ticks) {
        long seconds = (ticks + 19) / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    // ------------------------------------------------------------------
    // The rings not tied to one power
    // ------------------------------------------------------------------

    /** A speedster's Momentum (with run-speed pips) and, when there's news, Overrun. */
    public static void speed(List<Gauge> gauges) {
        boolean flash = flash();
        int momentum = ClientMomentum.get();
        int color = momentum >= 100 ? (flash ? PALE : GOLD) : momentum >= 50 ? GOLD : ORANGE;
        int max = ClientMomentum.maximumSpeedStep();
        gauges.add(Gauge.ring(momentum / 100F, color).middle(Integer.toString(momentum))
                .label(max > 0 ? "SPEED " + ClientMomentum.speedStep() : "MOMENTUM")
                .notches(0.3F, 0.5F, 0.6F, 0.8F).pips(max, ClientMomentum.speedStep(), GOLD));
        if (com.jayptucker.heroesevolved.client.ClientCyclone.active()) {
            // Your Cyclone: its speed in pips (scroll to change), and what it is at that speed.
            int speed = com.jayptucker.heroesevolved.client.ClientCyclone.speed(), cmax = com.jayptucker.heroesevolved.client.ClientCyclone.maximum();
            int mode = com.jayptucker.heroesevolved.client.ClientCyclone.mode();
            String word = switch (mode) {
                case com.jayptucker.heroesevolved.network.CycloneStatusPayload.VACUUM -> "VACUUM";
                case com.jayptucker.heroesevolved.network.CycloneStatusPayload.TORNADO -> "TORNADO";
                case com.jayptucker.heroesevolved.network.CycloneStatusPayload.REACTOR -> "NO LIFT";
                default -> "WIND";
            };
            int c = mode == com.jayptucker.heroesevolved.network.CycloneStatusPayload.VACUUM ? 0xFFDCE6EC
                    : mode == com.jayptucker.heroesevolved.network.CycloneStatusPayload.REACTOR ? 0xFFB8E04A : GOLD;
            gauges.add(Gauge.ring(cmax <= 0 ? 0 : speed / (float) cmax, c).middle(Integer.toString(speed)).label(word, c)
                    .pips(cmax, speed, c));
        }
        int state = ClientOverrun.state(), value = ClientOverrun.value();
        switch (state) {
            case OverrunStatusPayload.LOCKED -> {
                // Not yet: Overrun needs run speed 5.
                if (momentum >= 90) gauges.add(Gauge.ring(0, VIOLET_DIM).middle("S5").label("OVERRUN"));
            }
            case OverrunStatusPayload.GATHERING, OverrunStatusPayload.COOLDOWN -> {
                int left = ClientOverrun.secondsLeft();
                gauges.add(Gauge.ring(0, VIOLET_DIM).middle(left >= 60 ? left / 60 + "m" : left + "s", VIOLET).label("OVERRUN"));
            }
            case OverrunStatusPayload.READY, OverrunStatusPayload.BREACH -> {
                boolean breach = state == OverrunStatusPayload.BREACH;
                float fill = breach ? 1 : value / 1000F;
                int c = breach ? (flash ? PALE : VIOLET) : VIOLET;
                gauges.add(Gauge.ring(fill, c).middle(breach ? "GO" : fill > 0 ? Integer.toString(Math.round(fill * 100)) : "OK").label("OVERRUN"));
            }
            default -> { }
        }
    }

    /** Friction heat, while there is any, and burnt skin, while any is burnt. */
    public static void burn(List<Gauge> gauges) {
        var mc = Minecraft.getInstance();
        boolean flash = flash();
        if (friction > 0.5F) {
            boolean burning = friction >= SpeedFriction.BURN, scorching = friction >= SpeedFriction.SCORCH;
            int color = burning ? (flash ? PALE : RED) : scorching ? ORANGE : AMBER;
            gauges.add(Gauge.ring(friction / SpeedFriction.MAX, color).middle(Math.round(friction) + "%")
                    .label(burning ? "EASE OFF!" : "HEAT").notches(SpeedFriction.SCORCH / SpeedFriction.MAX, SpeedFriction.BURN / SpeedFriction.MAX));
        }
        float skin = mc.player == null ? 0 : BurnClient.level(mc.player, 1);
        if (skin > 0.01F) {
            boolean growing = BurnClient.regrowing(mc.player);
            gauges.add(Gauge.ring(skin, growing ? GROWING : EMBER).middle(Math.round(skin * 100) + "%").label(growing ? "REGROW" : "SKIN"));
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /** Draws the rings in a row at {@code x, y}, wrapping at the screen's edge; returns the y below them. */
    public static int row(GuiGraphics g, int x, int y, List<Gauge> gauges) {
        if (gauges.isEmpty()) return y;
        int perRow = Math.max(1, (g.guiWidth() - 2 * x) / STEP);
        for (int i = 0; i < gauges.size(); i++)
            draw(g, gauges.get(i), x + (i % perRow) * STEP, y + (i / perRow) * ROW);
        return y + ((gauges.size() - 1) / perRow + 1) * ROW;
    }

    /** One ring on its own, its top-left corner at {@code x, y} (for a readout centred on the screen). */
    public static void at(GuiGraphics g, int x, int y, Gauge gauge) { draw(g, gauge, x, y); }

    private static void draw(GuiGraphics g, Gauge gauge, int x, int y) {
        var mc = Minecraft.getInstance();
        float cx = x + SIZE / 2F, cy = y + SIZE / 2F, outer = SIZE / 2F, inner = outer - THICK;
        Matrix4f pose = g.pose().last().pose();
        VertexConsumer out = g.bufferSource().getBuffer(RenderType.gui());
        // A dark disc behind, so the number reads over any sky.
        arc(out, pose, cx, cy, 0, inner, 0, 1, 0x70000000);
        arc(out, pose, cx, cy, inner, outer, 0, 1, TRACK);
        if (gauge.to - gauge.from > 0.001F) {
            if (gauge.rainbow) {
                long time = mc.level == null ? 0 : mc.level.getGameTime();
                int steps = Math.max(1, (int) Math.ceil((gauge.to - gauge.from) * 24));
                for (int i = 0; i < steps; i++) {
                    float a = gauge.from + (gauge.to - gauge.from) * i / steps, b = gauge.from + (gauge.to - gauge.from) * (i + 1) / steps;
                    arc(out, pose, cx, cy, inner, outer, a, b, 0xFF000000 | Mth.hsvToRgb(((a + time / 200F) % 1F + 1F) % 1F, 0.6F, 1F));
                }
            } else arc(out, pose, cx, cy, inner, outer, gauge.from, gauge.to, gauge.color);
        }
        for (float notch : gauge.notches) arc(out, pose, cx, cy, inner - 0.5F, outer + 0.5F, notch - 0.006F, notch + 0.006F, NOTCH);
        g.flush();
        // The value in the middle, small enough to fit.
        if (!gauge.middle.isEmpty()) {
            float scale = mc.font.width(gauge.middle) > 14 ? 0.5F : 0.75F;
            small(g, gauge.middle, cx, cy - 4 * scale, scale, gauge.middleColor);
        }
        // Under it, pips for a setting and a label.
        float under = y + SIZE + 2;
        if (gauge.pips > 0) {
            int w = 3, gap = 1, total = gauge.pips * (w + gap) - gap;
            int px = Math.round(cx - total / 2F);
            for (int i = 0; i < gauge.pips; i++)
                g.fill(px + i * (w + gap), (int) under + 1, px + i * (w + gap) + w, (int) under + 3,
                        i < gauge.pipsLit ? gauge.pipColor : 0x60FFFFFF);
            small(g, gauge.label, cx, under + 4, 0.5F, gauge.labelColor);
        } else {
            small(g, gauge.label, cx, under + 1, 0.5F, gauge.labelColor);
        }
    }

    /** Text centred on {@code cx}, at {@code scale}. */
    private static void small(GuiGraphics g, String text, float cx, float y, float scale, int color) {
        if (text.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popPose();
    }

    /** A ring segment from {@code from} to {@code to} of the way round (clockwise from the top), between two radii. */
    private static void arc(VertexConsumer out, Matrix4f pose, float cx, float cy, float r0, float r1, float from, float to, int color) {
        int segments = Math.max(2, (int) Math.ceil((to - from) * 48));
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * (from + (to - from) * i / segments) - Mth.HALF_PI;
            float a1 = Mth.TWO_PI * (from + (to - from) * (i + 1) / segments) - Mth.HALF_PI;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            out.addVertex(pose, cx + c0 * r0, cy + s0 * r0, 0).setColor(color);
            out.addVertex(pose, cx + c1 * r0, cy + s1 * r0, 0).setColor(color);
            out.addVertex(pose, cx + c1 * r1, cy + s1 * r1, 0).setColor(color);
            out.addVertex(pose, cx + c0 * r1, cy + s0 * r1, 0).setColor(color);
        }
    }
}
