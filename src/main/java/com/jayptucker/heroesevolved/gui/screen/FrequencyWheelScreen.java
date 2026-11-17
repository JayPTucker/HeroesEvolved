package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.FrequencyChoicePayload;
import com.jayptucker.heroesevolved.network.FrequencyListPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Vibe's frequency wheel, opened by the Breach key (or Glimpse): every frequency the viber has tuned round the
 * cursor, the slots still empty after them, and one locked slot that says which Power Level opens it. Click a
 * frequency to breach there (or glimpse it), click an empty slot to tune where you stand, click the centre to breach
 * where you look. Shift-click re-tunes a slot to here, right-click forgets it and R renames it. Drawn in the breach's
 * deep blue and ice.
 */
public final class FrequencyWheelScreen extends Screen {
    private int inner = 40, outer = 112;
    private static final int SEGMENT = 0xB0071A38, HOVER = 0xE0183B80, EMPTY = 0x800A1020, LOCKED = 0x60060A14;
    private static final int ICE = 0xFF90E6FF, BLUE = 0xFF3096FF, TEXT = 0xFFEDFAFF, MUTED = 0xFF8AA6C8, WARN = 0xFFFFB27A;

    /** One place on the wheel: a frequency, an empty slot, or a slot the viber's level hasn't opened. */
    private record Place(@Nullable FrequencyListPayload.Entry entry, int slot, boolean locked) {}

    private final FrequencyListPayload list;
    private final List<Place> places = new ArrayList<>();
    private @Nullable EditBox naming;
    private int namingSlot = -1;
    /** Set by the preview fixture: stays open with this place hovered (-2 is the centre). */
    private int pinned = -1;

    public FrequencyWheelScreen(FrequencyListPayload list) {
        super(Component.translatable("screen.heroesevolved.frequency_wheel"));
        this.list = list;
        for (int slot = 0; slot < list.slots(); slot++) {
            int s = slot;
            places.add(new Place(list.entries().stream().filter(e -> e.slot() == s).findFirst().orElse(null), slot, false));
        }
        if (list.slots() < list.most()) places.add(new Place(null, list.slots(), true));
    }

    public static void open(FrequencyListPayload list) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.screen == null || mc.screen instanceof FrequencyWheelScreen)) mc.setScreen(new FrequencyWheelScreen(list));
    }

    public FrequencyWheelScreen pin(int place) { pinned = place; return this; }

    private boolean glimpse() { return list.mode() == FrequencyListPayload.GLIMPSE; }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        outer = Math.min(116, Math.min(width / 2 - 14, height / 2 - 50));
        inner = Math.round(outer * .38F);
        if (namingSlot >= 0) startNaming(namingSlot);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0x30000814, 0x60000814);
    }

    /** The place under the cursor: an index, -2 for the centre, or -1 for nothing. */
    private int hovered(double mouseX, double mouseY) {
        int n = places.size();
        double dx = mouseX - width / 2, dy = mouseY - height / 2, d2 = dx * dx + dy * dy;
        if (d2 <= (inner - 3) * (inner - 3)) return -2;
        if (d2 < inner * inner || d2 > (outer + 6) * (outer + 6)) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / n;
        angle = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return Math.min(n - 1, (int) (angle / (Math.PI * 2 / n)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2, cy = height / 2, n = places.size();
        int hover = naming != null ? namingPlace() : pinned != -1 ? pinned : hovered(mouseX, mouseY);
        double span = Math.PI * 2 / n;
        float clock = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < n; i++) {
            Place place = places.get(i);
            double from = -Math.PI / 2 - span / 2 + i * span + .025, to = from + span - .05;
            boolean filled = place.entry != null;
            int grow = i == hover && !place.locked ? 6 : 0;
            int fill = place.locked ? LOCKED : !filled ? (i == hover ? 0x90122850 : EMPTY) : i == hover ? HOVER : SEGMENT;
            sector(buffer, pose, cx, cy, inner, outer + grow, from, to, fill);
            if (filled) {
                // An iris lip round each tuned slot, brighter under the cursor; unreachable ones stay dim.
                int alpha = !place.entry.reachable() ? 70 : i == hover ? 235 : 140;
                sector(buffer, pose, cx, cy, outer + grow - 3, outer + grow, from, to, alpha << 24 | (i == hover ? 0x90E6FF : 0x3096FF));
            }
        }
        sector(buffer, pose, cx, cy, 0, inner - 6, 0, Math.PI * 2, hover == -2 ? HOVER : SEGMENT);
        float pulse = .5F + .5F * Mth.sin(clock * .2F);
        sector(buffer, pose, cx, cy, inner - 6, inner - 3, 0, Math.PI * 2, (int) (120 + 100 * pulse) << 24 | 0x3096FF);
        graphics.flush();

        for (int i = 0; i < n; i++) {
            Place place = places.get(i);
            double mid = -Math.PI / 2 + i * span;
            int radius = (inner + outer) / 2 + 4 + (i == hover ? 3 : 0);
            int x = cx + (int) Math.round(Math.cos(mid) * radius), y = cy + (int) Math.round(Math.sin(mid) * radius);
            if (place.locked) {
                centred(graphics, list.nextLevel() > 0 ? "Lv " + list.nextLevel() : "Skill", x, y - 4, MUTED);
                continue;
            }
            if (place.entry == null) {
                centred(graphics, "+", x, y - 4, i == hover ? ICE : MUTED);
                continue;
            }
            graphics.renderItem(icon(place.entry), x - 8, y - 14);
            String label = fit(name(place.entry), Math.min(65, outer * 3 / 5));
            centred(graphics, label, x, y + 4, place.entry.reachable() ? TEXT : MUTED);
        }
        centred(graphics, glimpse() ? "Cancel" : "Aim", cx, cy - 4, hover == -2 ? ICE : TEXT);

        int top = cy - outer - 30;
        if (hover >= 0) {
            Place place = places.get(hover);
            if (place.locked) {
                centred(graphics, list.nextLevel() > 0 ? "Another frequency opens at Power Level " + list.nextLevel()
                        : "World Frequency holds two more", cx, top + 4, MUTED);
            } else if (place.entry == null) {
                centred(graphics, "Frequency " + (place.slot + 1) + ": empty", cx, top, TEXT);
                centred(graphics, "Click to tune the spot where you stand", cx, top + 11, MUTED);
            } else {
                FrequencyListPayload.Entry e = place.entry;
                centred(graphics, name(e), cx, top - 6, TEXT);
                String where = e.x() + ", " + e.y() + ", " + e.z() + " · " + world(e.dimension());
                if (e.distance() >= 0) where += " · " + e.distance() + " blocks";
                centred(graphics, where, cx, top + 5, MUTED);
                String action = !e.reachable() ? (e.distance() < 0 && !list.worlds() ? "Needs World Frequency to reach another world"
                        : e.distance() >= 0 && e.distance() < 5 ? "You're standing on it" : "Out of reach from here")
                        : glimpse() ? "Click to look through" : "Click to open a breach";
                centred(graphics, action, cx, top + 16, e.reachable() ? ICE : WARN);
            }
        } else if (hover == -2) {
            centred(graphics, glimpse() ? "Close the wheel" : "Open a breach where you look, up to " + list.aimRange() + " blocks",
                    cx, top + 4, TEXT);
        }
        int used = (int) places.stream().filter(p -> p.entry != null).count();
        int footer = cy + outer + 13;
        if (naming != null) {
            centred(graphics, "Enter to save · Esc to cancel", cx, footer, TEXT);
        } else {
            centred(graphics, (glimpse() ? "Glimpse" : "Breach") + " · " + used + "/" + list.slots() + " frequencies", cx, footer, TEXT);
            String move = "Shift-click to move", edit = "R to rename · Right-click to forget";
            String controls = move + " · " + edit;
            if (font.width(controls) <= width - 16) {
                centred(graphics, controls, cx, footer + 12, MUTED);
            } else {
                centred(graphics, move, cx, footer + 12, MUTED);
                centred(graphics, edit, cx, footer + 23, MUTED);
            }
        }
    }

    private int namingPlace() {
        for (int i = 0; i < places.size(); i++) if (places.get(i).slot == namingSlot && !places.get(i).locked) return i;
        return -1;
    }

    private static ItemStack icon(FrequencyListPayload.Entry entry) {
        return new ItemStack(switch (entry.dimension()) {
            case "minecraft:overworld" -> Items.GRASS_BLOCK;
            case "minecraft:the_nether" -> Items.NETHERRACK;
            case "minecraft:the_end" -> Items.END_STONE;
            default -> Items.ENDER_EYE;
        });
    }

    private static String name(FrequencyListPayload.Entry entry) {
        if (!entry.name().isEmpty()) return entry.name();
        String biome = entry.biome().isEmpty() ? "" : Component.translatable(entry.biome()).getString();
        return (biome.isEmpty() || biome.equals(entry.biome()) ? "Frequency" : biome) + " " + (entry.slot() + 1);
    }

    private static String world(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "the Overworld";
            case "minecraft:the_nether" -> "the Nether";
            case "minecraft:the_end" -> "the End";
            default -> dimension;
        };
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    private void centred(GuiGraphics graphics, String text, int x, int y, int color) {
        if (text.isEmpty()) return;
        text = fit(text, width - 16);
        graphics.drawString(font, text, x - font.width(text) / 2, y, color, true);
    }

    private static void sector(VertexConsumer buffer, Matrix4f pose, int cx, int cy, int inner, int outer,
                               double from, double to, int color) {
        int steps = Math.max(1, (int) Math.ceil((to - from) / .08));
        for (int s = 0; s < steps; s++) {
            double a0 = from + (to - from) * s / steps, a1 = from + (to - from) * (s + 1) / steps;
            float c0 = Mth.cos((float) a0), s0 = Mth.sin((float) a0), c1 = Mth.cos((float) a1), s1 = Mth.sin((float) a1);
            buffer.addVertex(pose, cx + c0 * inner, cy + s0 * inner, 0).setColor(color);
            buffer.addVertex(pose, cx + c1 * inner, cy + s1 * inner, 0).setColor(color);
            buffer.addVertex(pose, cx + c1 * outer, cy + s1 * outer, 0).setColor(color);
            buffer.addVertex(pose, cx + c0 * outer, cy + s0 * outer, 0).setColor(color);
        }
    }

    private void startNaming(int slot) {
        namingSlot = slot;
        if (naming != null) removeWidget(naming);
        naming = new EditBox(font, width / 2 - 70, height / 2 + outer + 26, 140, 16, Component.literal("Frequency name"));
        naming.setMaxLength(32);
        FrequencyListPayload.Entry entry = list.entries().stream().filter(e -> e.slot() == slot).findFirst().orElse(null);
        naming.setValue(entry == null ? "" : entry.name());
        addRenderableWidget(naming);
        setInitialFocus(naming);
        setFocused(naming);
    }

    private void stopNaming() {
        if (naming != null) removeWidget(naming);
        naming = null;
        namingSlot = -1;
    }

    private static void send(int op, int slot, String name) {
        PacketDistributor.sendToServer(new FrequencyChoicePayload(op, slot, name));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (naming != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                send(FrequencyChoicePayload.RENAME, namingSlot, naming.getValue());
                stopNaming();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                stopNaming();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        if (key == GLFW.GLFW_KEY_R && minecraft != null) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            int index = pinned != -1 ? pinned : hovered(mx, my);
            if (index >= 0 && places.get(index).entry != null) {
                startNaming(places.get(index).slot);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (naming != null) return super.mouseClicked(mouseX, mouseY, button);
        int index = hovered(mouseX, mouseY);
        if (index == -2) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            if (!glimpse()) send(FrequencyChoicePayload.BREACH, -1, "");
            onClose();
            return true;
        }
        if (index < 0) return super.mouseClicked(mouseX, mouseY, button);
        Place place = places.get(index);
        if (place.locked) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (place.entry != null) send(FrequencyChoicePayload.FORGET, place.slot, "");
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (place.entry == null || hasShiftDown()) {
            send(FrequencyChoicePayload.MARK, place.slot, "");
            return true;
        }
        send(glimpse() ? FrequencyChoicePayload.GLIMPSE : FrequencyChoicePayload.BREACH, place.slot, "");
        onClose();
        return true;
    }
}
