package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.ActionInputPayload;
import com.jayptucker.heroesevolved.network.ClientLoadout;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.LoadoutPayload;
import com.jayptucker.heroesevolved.skills.Loadout;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The ability wheel: every ability the power can cast, around the cursor. Hold the wheel key, flick
 * toward an ability and let go to cast it. Holding the left mouse button on an ability casts it and
 * keeps it held (for channelled abilities) until the button is let go. Letting go in the middle casts
 * nothing. A tap of the wheel key (handled in ClientKeyEvents) casts the last wheel ability again.
 */
public final class AbilityWheelScreen extends Screen {
    private static final int INNER = 34, OUTER_MIN = 92;
    private static final int SEGMENT = 0xB0141018, HOVER = 0xE0582632, LOCKED = 0xB0101010, EDGE = 0xFFE2A0AC;
    private static final int TEXT = 0xFFFFE6EA, MUTED = 0xFF9A8A8E, GOLD = 0xFFFFC85A;

    /** The last action cast from the wheel, for a quick recast. */
    private static String last = "";

    private final List<LoadoutPayload.Entry> entries;
    private final boolean overexertAtOpen;
    private String channelling;
    private boolean done;
    /** Set by the UI preview fixture: stays open, showing this action as hovered. */
    private String pinned;

    public AbilityWheelScreen(boolean overexert) {
        super(Component.translatable("screen.heroesevolved.ability_wheel"));
        this.entries = ClientLoadout.get().entries();
        this.overexertAtOpen = overexert;
    }

    /** Keeps the wheel open with {@code actionKey} shown as hovered. Used by the UI preview fixture. */
    public AbilityWheelScreen pin(String actionKey) { pinned = actionKey; return this; }

    /** Casts the last wheel ability again (a tap of the wheel key). */
    public static void recast(boolean overexert) {
        if (last.isEmpty() || ClientLoadout.entry(last) == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("message.heroesevolved.wheel_hint",
                    ModKeyMappings.ABILITY_WHEEL.getTranslatedKeyMessage()), true);
            return;
        }
        tap(last, overexert);
    }

    private static void tap(String actionKey, boolean overexert) {
        PacketDistributor.sendToServer(new ActionInputPayload(0, actionKey, true, overexert));
        PacketDistributor.sendToServer(new ActionInputPayload(0, actionKey, false, false));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0x30000000, 0x50000000);
    }

    private int outer() { return Math.max(OUTER_MIN, entries.size() * 11); }

    /** The entry under the cursor, or -1 in the middle. */
    private int hovered(double mouseX, double mouseY) {
        if (entries.isEmpty()) return -1;
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < INNER * INNER * 0.5) return -1;
        // Segment 0 is centred straight up; angles run clockwise on screen.
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / entries.size();
        angle = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return Math.min(entries.size() - 1, (int) (angle / (Math.PI * 2 / entries.size())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2, cy = height / 2, outer = outer(), n = entries.size();
        int hover = pinned != null ? indexOf(pinned) : channelling != null ? indexOf(channelling) : hovered(mouseX, mouseY);
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        double span = Math.PI * 2 / Math.max(1, n);

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < n; i++) {
            LoadoutPayload.Entry entry = entries.get(i);
            double from = -Math.PI / 2 - span / 2 + i * span + 0.02, to = from + span - 0.04;
            int color = !entry.unlocked() ? LOCKED : i == hover ? HOVER : SEGMENT;
            int grow = i == hover ? 6 : 0;
            sector(buffer, pose, cx, cy, INNER, outer + grow, from, to, color);
            sector(buffer, pose, cx, cy, outer + grow - 2, outer + grow, from, to, i == hover ? EDGE : 0x60E2A0AC);
            // Cooldown: the segment fills back in clockwise as it recovers.
            long left = entry.cooldownEnd() - now;
            if (entry.unlocked() && left > 0 && entry.cooldownTicks() > 0) {
                double part = Math.min(1.0, left / (double) entry.cooldownTicks());
                sector(buffer, pose, cx, cy, INNER, outer + grow - 2, from, from + (to - from) * part, 0x90000000);
            }
        }
        sector(buffer, pose, cx, cy, INNER - 3, INNER - 1, 0, Math.PI * 2, 0x80E2A0AC);
        graphics.flush();

        String power = ClientPowerState.getSnapshot().abilities().stream().filter(a -> a.unlocked())
                .map(a -> a.abilityId().getPath()).findFirst().orElse("");
        for (int i = 0; i < n; i++) {
            LoadoutPayload.Entry entry = entries.get(i);
            double mid = -Math.PI / 2 + i * span;
            int radius = (INNER + outer) / 2 + (i == hover ? 3 : 0);
            int x = cx + (int) Math.round(Math.cos(mid) * radius), y = cy + (int) Math.round(Math.sin(mid) * radius);
            graphics.renderItem(icon(power, entry), x - 8, y - 10);
            if (!entry.unlocked()) {
                graphics.fill(x - 8, y - 10, x + 8, y + 6, 0xA0000000);
                centred(graphics, "Lv " + entry.unlockLevel(), x, y + 8, MUTED);
                continue;
            }
            long left = entry.cooldownEnd() - now;
            if (left > 0) centred(graphics, seconds(left), x, y + 8, 0xFFB0B0B0);
            else pips(graphics, x, y + 9, entry.rank());
            int key = ClientLoadout.keyOf(entry.actionKey());
            if (key > 0) {
                var mapping = ModKeyMappings.ABILITY_KEYS[key - 1];
                String label = mapping.isUnbound() ? String.valueOf(key) : mapping.getTranslatedKeyMessage().getString();
                if (label.length() > 3) label = label.substring(0, 3);
                graphics.drawString(font, label, x + 7, y - 13, GOLD, true);
            }
        }

        // The centre: what is selected, or how to use the wheel.
        if (hover >= 0) {
            LoadoutPayload.Entry entry = entries.get(hover);
            centred(graphics, Component.translatable(entry.nameKey()).getString(), cx, cy - 13, TEXT);
            String detail = !entry.unlocked() ? "Unlocks at Level " + entry.unlockLevel()
                    : entry.energy() + " stamina" + (entry.rank() > 1 ? "  ·  Rank " + roman(entry.rank()) : "");
            centred(graphics, detail, cx, cy - 2, entry.unlocked() ? MUTED : 0xFFE07070);
            if (channelling != null) centred(graphics, "Holding", cx, cy + 9, GOLD);
        } else {
            centred(graphics, "Release to", cx, cy - 8, MUTED);
            centred(graphics, "cancel", cx, cy + 2, MUTED);
        }
        LoadoutPayload state = ClientLoadout.get();
        String level = "Level " + state.level() + " · "
                + com.jayptucker.heroesevolved.progression.PowerLevels.stage(state.level()).title;
        centred(graphics, level, cx, cy + outer + 12, TEXT);
        centred(graphics, "Release to cast  ·  hold left click to channel", cx, cy + outer + 24, MUTED);
    }

    private ItemStack icon(String power, LoadoutPayload.Entry entry) {
        if (entry.actionKey().startsWith(Loadout.BASE)) return PowerIcons.action(power, entry.path());
        return SkillCatalog.find(power, entry.actionKey()).map(PowerIcons::node).orElseGet(() -> PowerIcons.action(power, entry.path()));
    }

    private void pips(GuiGraphics graphics, int x, int y, int rank) {
        for (int r = 0; r < 3; r++) {
            int px = x - 7 + r * 5;
            graphics.fill(px, y, px + 3, y + 3, r < rank ? GOLD : 0x60FFFFFF);
        }
    }

    private void centred(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(font, text, x - font.width(text) / 2, y, color, true);
    }

    private static String seconds(long ticks) {
        long s = (ticks + 19) / 20;
        return s >= 60 ? (s + 59) / 60 + "m" : s + "s";
    }

    private static String roman(int rank) { return rank >= 3 ? "III" : rank == 2 ? "II" : "I"; }

    /** A ring segment from angle {@code from} to {@code to}, as thin quads. */
    private static void sector(VertexConsumer buffer, Matrix4f pose, int cx, int cy, int inner, int outer,
                               double from, double to, int color) {
        int steps = Math.max(1, (int) Math.ceil((to - from) / 0.08));
        for (int s = 0; s < steps; s++) {
            double a0 = from + (to - from) * s / steps, a1 = from + (to - from) * (s + 1) / steps;
            float c0 = Mth.cos((float) a0), s0 = Mth.sin((float) a0), c1 = Mth.cos((float) a1), s1 = Mth.sin((float) a1);
            // Wound the same way GuiGraphics.fill winds its quads.
            buffer.addVertex(pose, cx + c0 * inner, cy + s0 * inner, 0).setColor(color);
            buffer.addVertex(pose, cx + c1 * inner, cy + s1 * inner, 0).setColor(color);
            buffer.addVertex(pose, cx + c1 * outer, cy + s1 * outer, 0).setColor(color);
            buffer.addVertex(pose, cx + c0 * outer, cy + s0 * outer, 0).setColor(color);
        }
    }

    private int indexOf(String actionKey) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).actionKey().equals(actionKey)) return i;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = hovered(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && channelling == null && index >= 0 && entries.get(index).unlocked()) {
            channelling = entries.get(index).actionKey();
            last = channelling;
            PacketDistributor.sendToServer(new ActionInputPayload(0, channelling, true, overexert()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && channelling != null) {
            finish(mouseX, mouseY, false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ModKeyMappings.ABILITY_WHEEL.matches(keyCode, scanCode)) {
            if (channelling == null) finish(mouseX(), mouseY(), true);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        // A release the screen never saw (focus lost, a mouse-bound wheel key) still ends the wheel.
        if (pinned == null && channelling == null && !wheelKeyDown()) finish(mouseX(), mouseY(), true);
    }

    @Override
    public void onClose() {
        if (channelling != null) {
            PacketDistributor.sendToServer(new ActionInputPayload(0, channelling, false, false));
            channelling = null;
        }
        super.onClose();
    }

    /** Ends the wheel: lets go of a held ability, or casts the hovered one. */
    private void finish(double mouseX, double mouseY, boolean cast) {
        if (done) return;
        done = true;
        if (channelling != null) {
            PacketDistributor.sendToServer(new ActionInputPayload(0, channelling, false, false));
            channelling = null;
        } else if (cast) {
            int index = hovered(mouseX, mouseY);
            if (index >= 0 && entries.get(index).unlocked()) {
                last = entries.get(index).actionKey();
                tap(last, overexert());
            }
        }
        onClose();
    }

    private boolean overexert() { return overexertAtOpen || hasShiftDown(); }

    private boolean wheelKeyDown() {
        InputConstants.Key key = ModKeyMappings.ABILITY_WHEEL.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
    }

    private double mouseX() {
        return minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
    }

    private double mouseY() {
        return minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
    }
}
