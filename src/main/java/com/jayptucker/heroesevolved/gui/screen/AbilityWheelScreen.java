package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.ability.PowerBalance;
import com.jayptucker.heroesevolved.gui.AbilityIcons;
import com.jayptucker.heroesevolved.gui.PowerUiColors;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.ActionInputPayload;
import com.jayptucker.heroesevolved.network.ClientLoadout;
import com.jayptucker.heroesevolved.network.LoadoutPayload;
import com.jayptucker.heroesevolved.skills.WheelLoadout;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * The player's saved favorites, in eight fixed positions. Hold the wheel key, flick
 * toward an ability and let go to cast it. Holding the left mouse button on an ability casts it and
 * keeps it held (for channelled abilities) until the button is let go. Letting go in the middle casts
 * nothing. A tap of the wheel key casts the pinned favorite or the last pick.
 */
public final class AbilityWheelScreen extends Screen {
    private static final int INNER = 34, OUTER_MIN = 92;
    private final String power;
    private final PowerUiColors.Palette colors;
    private final boolean overexertAtOpen;
    private String channelling;
    private boolean done;
    /** Set by the UI preview fixture: stays open, showing this action as hovered. */
    private String pinned;

    public AbilityWheelScreen(boolean overexert) {
        super(Component.translatable("screen.heroesevolved.ability_wheel"));
        this.power = ClientLoadout.get().power();
        this.colors = PowerUiColors.forPower(power);
        this.overexertAtOpen = overexert;
    }

    /** Keeps the wheel open with {@code actionKey} shown as hovered. Used by the UI preview fixture. */
    public AbilityWheelScreen pin(String actionKey) { pinned = actionKey; return this; }

    /** Quick-casts the pinned favorite, or recasts the last pick. */
    public static void recast(boolean overexert) {
        var target = ClientLoadout.quickCast();
        if (target == null || !target.unlocked()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !ClientLoadout.get().power().isEmpty()) mc.player.displayClientMessage(Component.translatable("message.heroesevolved.wheel_hint",
                    ModKeyMappings.ABILITY_WHEEL.getTranslatedKeyMessage()), true);
            return;
        }
        tap(target.actionKey(), overexert);
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

    private int outer() { return Math.max(INNER + 22, Math.min(OUTER_MIN, Math.min(width, height) / 2 - 26)); }

    /** The entry under the cursor, or -1 in the middle. */
    private int hovered(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy <= INNER * INNER) return -1;
        // Segment 0 is centred straight up; angles run clockwise on screen.
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / WheelLoadout.SLOTS;
        angle = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return Math.min(WheelLoadout.SLOTS - 1, (int) (angle / (Math.PI * 2 / WheelLoadout.SLOTS)));
    }

    private LoadoutPayload.Entry entryAt(int index) {
        return index < 0 || !power.equals(ClientLoadout.get().power()) ? null
                : ClientLoadout.entry(ClientLoadout.onWheel(index + 1));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2, cy = height / 2, outer = outer();
        int hover = pinned != null ? ClientLoadout.wheelPosition(pinned) - 1
                : channelling != null ? ClientLoadout.wheelPosition(channelling) - 1 : hovered(mouseX, mouseY);
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        double span = Math.PI * 2 / WheelLoadout.SLOTS;
        String quickCast = ClientLoadout.get().quickCast();

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < WheelLoadout.SLOTS; i++) {
            LoadoutPayload.Entry entry = entryAt(i);
            double from = -Math.PI / 2 - span / 2 + i * span + 0.02, to = from + span - 0.04;
            int color = entry == null ? PowerUiColors.TRACK : i == hover ? colors.hover() : colors.selected();
            int grow = i == hover && entry != null ? 4 : 0;
            sector(buffer, pose, cx, cy, INNER, outer + grow, from, to, color);
            int edge = i == hover ? colors.highlight() : entry == null ? PowerUiColors.MUTED_EDGE
                    : entry.actionKey().equals(quickCast) ? colors.accent() : colors.edge();
            sector(buffer, pose, cx, cy, outer + grow - 2, outer + grow, from, to, edge);
            if (entry == null) continue;
            // Cooldown: the segment fills back in clockwise as it recovers.
            long left = entry.cooldownEnd() - now;
            if (entry.unlocked() && left > 0 && entry.cooldownTicks() > 0) {
                double part = Math.min(1.0, left / (double) entry.cooldownTicks());
                sector(buffer, pose, cx, cy, INNER, outer + grow - 2, from, from + (to - from) * part, 0x90000000);
            }
        }
        sector(buffer, pose, cx, cy, INNER - 3, INNER - 1, 0, Math.PI * 2, colors.edge());
        graphics.flush();

        for (int i = 0; i < WheelLoadout.SLOTS; i++) {
            LoadoutPayload.Entry entry = entryAt(i);
            double mid = -Math.PI / 2 + i * span;
            int radius = (INNER + outer) / 2 + (i == hover ? 2 : 0);
            int x = cx + (int) Math.round(Math.cos(mid) * radius), y = cy + (int) Math.round(Math.sin(mid) * radius);
            if (entry == null) {
                graphics.fill(x - 2, y - 1, x + 3, y, i == hover ? PowerUiColors.MUTED_TEXT : PowerUiColors.MUTED_EDGE);
                continue;
            }
            AbilityIcons.draw(graphics, power, entry.path(), x - 11, y - 12, 22);
            long left = entry.cooldownEnd() - now;
            if (left > 0) centred(graphics, seconds(left), x, y + 8, PowerUiColors.TEXT);
            if (entry.actionKey().equals(quickCast)) graphics.fill(x + 8, y - 11, x + 11, y - 8, colors.highlight());
        }

        LoadoutPayload.Entry selected = entryAt(hover);
        if (selected != null) {
            int labelY = cy + outer + 5;
            centred(graphics, Component.translatable(selected.nameKey()).getString(), cx, labelY, PowerUiColors.TEXT);
            String detail = PowerBalance.describe(selected.path(), selected.energy())
                    + (selected.rank() > 1 ? "  ·  Rank " + roman(selected.rank()) : "");
            if (selected.actionKey().equals(quickCast)) detail += "  ·  Tap " + ModKeyMappings.ABILITY_WHEEL.getTranslatedKeyMessage().getString();
            centred(graphics, detail, cx, labelY + 11, PowerUiColors.MUTED_TEXT);
            centred(graphics, channelling != null ? "Holding" : "Release", cx, cy - 4,
                    channelling != null ? colors.highlight() : PowerUiColors.TEXT);
        } else {
            centred(graphics, hover < 0 ? "Cancel" : "Empty", cx, cy - 4, PowerUiColors.MUTED_TEXT);
            String help = ClientLoadout.get().wheel().stream().allMatch(String::isEmpty)
                    ? "Assign favorites in Powers → Loadout" : "Release to cast  ·  hold left click to channel";
            centred(graphics, help, cx, cy + outer + 8, PowerUiColors.MUTED_TEXT);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        LoadoutPayload.Entry entry = entryAt(hovered(mouseX, mouseY));
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && channelling == null && entry != null && entry.unlocked()) {
            channelling = entry.actionKey();
            ClientLoadout.rememberWheelAction(channelling);
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
        if (pinned != null) return;
        if (!minecraft.isWindowActive() || !power.equals(ClientLoadout.get().power())) onClose();
        // Mouse-bound wheel keys do not pass through keyReleased.
        else if (channelling == null && !wheelKeyDown(minecraft)) finish(mouseX(), mouseY(), true);
    }

    @Override
    public void removed() {
        if (channelling != null) {
            if (minecraft.getConnection() != null && power.equals(ClientLoadout.get().power()))
                PacketDistributor.sendToServer(new ActionInputPayload(0, channelling, false, false));
            channelling = null;
        }
        super.removed();
    }

    /** Ends the wheel: lets go of a held ability, or casts the hovered one. */
    private void finish(double mouseX, double mouseY, boolean cast) {
        if (done) return;
        done = true;
        if (channelling == null && cast && minecraft.isWindowActive()) {
            var entry = entryAt(hovered(mouseX, mouseY));
            if (entry != null && entry.unlocked()) {
                ClientLoadout.rememberWheelAction(entry.actionKey());
                tap(entry.actionKey(), overexert());
            }
        }
        onClose();
    }

    private boolean overexert() { return overexertAtOpen || keyDown(minecraft, minecraft.options.keyShift); }

    public static boolean wheelKeyDown(Minecraft minecraft) {
        return keyDown(minecraft, ModKeyMappings.ABILITY_WHEEL);
    }

    private static boolean keyDown(Minecraft minecraft, net.minecraft.client.KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.SCANCODE) return mapping.isDown();
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
