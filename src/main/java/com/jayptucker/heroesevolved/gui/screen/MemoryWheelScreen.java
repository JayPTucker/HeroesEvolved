package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.network.ClientMimicMemory;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.MimicChoicePayload;
import com.jayptucker.heroesevolved.network.MimicMemoryPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Empathic Mimicry's memory wheel: every power the empath carries, around the cursor. The set they are near sits at
 * the top; kept sets follow, then the empty places still to fill (or the Level that opens them). Hold the memory
 * key, flick toward a set and let go to channel it; right-click a kept set to let it go. Each set shows its power,
 * the face (or kind) of whoever it came from, and a rim in that power's colour.
 */
public final class MemoryWheelScreen extends Screen {
    private static final int INNER = 38, OUTER = 104;
    private static final int SEGMENT = 0xB0181410, HOVER = 0xE0403018, EMPTY = 0x90101010, GOLD = 0xFFE8C27A;
    private static final int TEXT = 0xFFFFF4E0, MUTED = 0xFFA89A88;

    /** One place on the wheel: a carried set, or an empty place with a reason. */
    private record Place(MimicMemoryPayload.Slot slot, int index, String empty) {}

    private final List<Place> places = new ArrayList<>();
    private final MimicMemoryPayload memory;
    private boolean done;
    /** Set by the preview fixture: stays open with this place hovered. */
    private int pinned = -1;

    public MemoryWheelScreen() {
        super(Component.translatable("screen.heroesevolved.memory_wheel"));
        memory = ClientMimicMemory.get();
        boolean hasNear = !memory.slots().isEmpty() && !memory.slots().getFirst().kept();
        if (!hasNear) places.add(new Place(null, -1, "Stand near someone with a power"));
        for (int i = 0; i < memory.slots().size(); i++) places.add(new Place(memory.slots().get(i), i, null));
        int kept = (int) memory.slots().stream().filter(MimicMemoryPayload.Slot::kept).count();
        if (memory.capacity() == 0) {
            places.add(new Place(null, -1, "Keep a power by touch at Level 6"));
            places.add(new Place(null, -1, "Remember whole sets at Level 15"));
        } else for (int i = kept; i < memory.capacity(); i++) places.add(new Place(null, -1, "Touch someone to keep their power"));
    }

    public MemoryWheelScreen pin(int place) { pinned = place; return this; }

    /** Whether the local player is an Empathic Mimic. */
    public static boolean isMimic() {
        return ClientPowerState.getSnapshot().abilities().stream()
                .anyMatch(a -> a.unlocked() && a.abilityId().getPath().equals("empathic_mimicry"));
    }

    /** A tap of the memory key: channel the next set carried. */
    public static void stepNext() {
        MimicMemoryPayload memory = ClientMimicMemory.get();
        if (memory.slots().size() < 2) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("mimicry.heroesevolved.empty"), true);
            return;
        }
        PacketDistributor.sendToServer(new MimicChoicePayload(Math.floorMod(memory.selected() + 1, memory.slots().size()), false));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0x30000000, 0x58000000);
    }

    private int hovered(double mouseX, double mouseY) {
        int n = places.size();
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < INNER * INNER * .5) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / n;
        angle = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return Math.min(n - 1, (int) (angle / (Math.PI * 2 / n)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2, cy = height / 2, n = places.size();
        int hover = pinned >= 0 ? pinned : hovered(mouseX, mouseY);
        double span = Math.PI * 2 / n;
        float pulse = .5F + .5F * Mth.sin((minecraft.level == null ? 0 : minecraft.level.getGameTime() + partialTick) * .2F);

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < n; i++) {
            Place place = places.get(i);
            double from = -Math.PI / 2 - span / 2 + i * span + .03, to = from + span - .06;
            boolean filled = place.slot != null, selected = filled && place.index == memory.selected();
            int grow = i == hover && filled ? 6 : 0;
            sector(buffer, pose, cx, cy, INNER, OUTER + grow, from, to, !filled ? EMPTY : i == hover ? HOVER : SEGMENT);
            if (filled) {
                // The rim takes the borrowed power's colour; the channeled set's rim breathes.
                int tint = MimicColors.color(place.slot.power());
                int alpha = selected ? (int) (170 + 85 * pulse) : i == hover ? 230 : 150;
                sector(buffer, pose, cx, cy, OUTER + grow - (selected ? 5 : 3), OUTER + grow, from, to, alpha << 24 | tint);
                if (selected) sector(buffer, pose, cx, cy, INNER, INNER + 3, from, to, 0xFF000000 | 0xE8C27A);
            }
        }
        sector(buffer, pose, cx, cy, INNER - 4, INNER - 1, 0, Math.PI * 2, 0x90E8C27A);
        graphics.flush();

        for (int i = 0; i < n; i++) {
            Place place = places.get(i);
            double mid = -Math.PI / 2 + i * span;
            int radius = (INNER + OUTER) / 2 + (i == hover ? 3 : 0);
            int x = cx + (int) Math.round(Math.cos(mid) * radius), y = cy + (int) Math.round(Math.sin(mid) * radius);
            if (place.slot == null) {
                centred(graphics, "·", x, y - 4, MUTED);
                continue;
            }
            graphics.renderItem(PowerIcons.emblem(place.slot.power()), x - 8, y - 14);
            face(graphics, place.slot, x - 6, y + 3);
            if (!place.slot.kept()) centred(graphics, "Near", x, y - 24, GOLD);
        }

        if (hover >= 0) {
            Place place = places.get(hover);
            if (place.slot == null) {
                centred(graphics, place.empty, cx, cy - 4, MUTED);
            } else {
                MimicMemoryPayload.Slot slot = place.slot;
                centred(graphics, Component.translatable("ability.heroesevolved." + slot.power()).getString(), cx, cy - 18, TEXT);
                String from = slot.name().isEmpty() ? "a memory" : slot.name();
                centred(graphics, "from " + from, cx, cy - 7, MUTED);
                centred(graphics, slot.kept() ? "Kept" : "Near you", cx, cy + 4, GOLD);
                if (place.index == memory.selected()) centred(graphics, "Channeling", cx, cy + 15, 0xFFFFD27A);
                else if (slot.kept()) centred(graphics, "Right-click to let go", cx, cy + 15, 0xFF8A7A6A);
            }
        } else {
            centred(graphics, "Release to", cx, cy - 8, MUTED);
            centred(graphics, "keep channeling", cx, cy + 2, MUTED);
        }
        int kept = (int) memory.slots().stream().filter(MimicMemoryPayload.Slot::kept).count();
        String footer = "Level " + memory.level() + (memory.capacity() > 0 ? "  ·  Kept " + kept + " of " + memory.capacity() : "  ·  One power at a time");
        centred(graphics, footer, cx, cy + OUTER + 14, TEXT);
        centred(graphics, "Release to channel  ·  tap the key to step to the next", cx, cy + OUTER + 26, MUTED);
    }

    /** Who a set came from: their face if they are a player this client knows, otherwise a mark for what they are. */
    private void face(GuiGraphics graphics, MimicMemoryPayload.Slot slot, int x, int y) {
        if (slot.kind().equals("player") && !slot.origin().isEmpty() && minecraft.getConnection() != null) {
            try {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(UUID.fromString(slot.origin()));
                if (info != null) {
                    PlayerFaceRenderer.draw(graphics, info.getSkin(), x, y, 12);
                    return;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        ItemStack mark = switch (slot.kind()) {
            case "villager" -> new ItemStack(Items.EMERALD);
            case "sylar" -> new ItemStack(Items.CLOCK);
            case "elle" -> new ItemStack(Items.LIGHTNING_ROD);
            case "player" -> new ItemStack(Items.PLAYER_HEAD);
            default -> new ItemStack(Items.AMETHYST_SHARD);
        };
        graphics.pose().pushPose();
        graphics.pose().translate(x - 1, y - 1, 0);
        graphics.pose().scale(.85F, .85F, 1);
        graphics.renderItem(mark, 0, 0);
        graphics.pose().popPose();
    }

    private void centred(GuiGraphics graphics, String text, int x, int y, int color) {
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = hovered(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && index >= 0) {
            Place place = places.get(index);
            if (place.slot != null && place.slot.kept()) {
                PacketDistributor.sendToServer(new MimicChoicePayload(place.index, true));
                done = true;
                onClose();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ModKeyMappings.MEMORY_WHEEL.matches(keyCode, scanCode)) {
            finish(mouseX(), mouseY());
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (pinned < 0 && !keyDown()) finish(mouseX(), mouseY());
    }

    private void finish(double mouseX, double mouseY) {
        if (done) return;
        done = true;
        int index = hovered(mouseX, mouseY);
        if (index >= 0) {
            Place place = places.get(index);
            if (place.slot != null && place.index != memory.selected())
                PacketDistributor.sendToServer(new MimicChoicePayload(place.index, false));
        }
        onClose();
    }

    private boolean keyDown() {
        InputConstants.Key key = ModKeyMappings.MEMORY_WHEEL.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
    }

    private double mouseX() { return minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth(); }

    private double mouseY() { return minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight(); }
}
