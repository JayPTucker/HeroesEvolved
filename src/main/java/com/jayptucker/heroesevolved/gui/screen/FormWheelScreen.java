package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.FormLibraryPayload;
import com.jayptucker.heroesevolved.network.MorphChoicePayload;
import com.jayptucker.heroesevolved.client.MorphClient;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapeshifting's form wheel, opened by Shift + Morph: every remembered shape round the cursor, each drawn as a small
 * turning model of the creature, with the places still empty after them. Click a shape to take it, click the centre
 * to go back to your own body, or press Escape to leave things as they are. Drawn in the power's slate and brass.
 */
public final class FormWheelScreen extends Screen {
    /** The wheel's radii, fitted to the screen so its labels never fall off the top or bottom. */
    private int inner = 40, outer = 112;
    private static final int SEGMENT = 0xB01F2A3D, HOVER = 0xE03E6F86, EMPTY = 0x80101418, BRASS = 0xFFC29A5E;
    private static final int TEXT = 0xFFF1ECE2, MUTED = 0xFF8FA8A6;

    /** One place on the wheel: a remembered form, or an empty place. */
    private record Place(@Nullable ShapeshiftForm form) {
        String id() { return form == null ? "" : form.key(); }
    }

    private final FormLibraryPayload library;
    private final List<Place> places = new ArrayList<>();
    private final Map<String, LivingEntity> models = new HashMap<>();
    /** Set by the preview fixture: stays open with this place hovered (-2 is the centre). */
    private int pinned = -1;

    public FormWheelScreen(FormLibraryPayload library) {
        super(Component.translatable("screen.heroesevolved.form_wheel"));
        this.library = library;
        for (ShapeshiftForm form : library.forms()) places.add(new Place(form));
        for (int i = places.size(); i < library.capacity(); i++) places.add(new Place(null));
        if (places.isEmpty()) places.add(new Place(null));
    }

    public static void open(FormLibraryPayload library) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.screen == null || mc.screen instanceof FormWheelScreen)) mc.setScreen(new FormWheelScreen(library));
    }

    public FormWheelScreen pin(int place) { pinned = place; return this; }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        outer = Math.max(60, Math.min(112, height / 2 - 40));
        inner = Math.round(outer * .36F);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0x30000000, 0x58000000);
    }

    /** The place under the cursor: an index, -2 for the centre, or -1 for nothing. */
    private int hovered(double mouseX, double mouseY) {
        int n = places.size();
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0, d2 = dx * dx + dy * dy;
        if (d2 < (inner - 4) * (inner - 4)) return -2;
        if (d2 > (outer + 30) * (outer + 30)) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / n;
        angle = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return Math.min(n - 1, (int) (angle / (Math.PI * 2 / n)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2, cy = height / 2, n = places.size();
        int hover = pinned != -1 ? pinned : hovered(mouseX, mouseY);
        double span = Math.PI * 2 / n;
        float clock = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        float pulse = .5F + .5F * Mth.sin(clock * .2F);

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < n; i++) {
            Place place = places.get(i);
            double from = -Math.PI / 2 - span / 2 + i * span + .025, to = from + span - .05;
            boolean filled = place.form != null, worn = filled && place.id().equals(library.current());
            int grow = i == hover && filled ? 6 : 0;
            sector(buffer, pose, cx, cy, inner, outer + grow, from, to, !filled ? EMPTY : i == hover ? HOVER : SEGMENT);
            if (filled) {
                // A brass rim, like a plate's underside; the worn form's rim breathes.
                int alpha = worn ? (int) (170 + 85 * pulse) : i == hover ? 230 : 120;
                sector(buffer, pose, cx, cy, outer + grow - (worn ? 5 : 3), outer + grow, from, to, alpha << 24 | 0xC29A5E);
            }
        }
        boolean ownBody = library.current().isEmpty();
        sector(buffer, pose, cx, cy, 0, inner - 6, 0, Math.PI * 2, hover == -2 ? HOVER : SEGMENT);
        sector(buffer, pose, cx, cy, inner - 6, inner - 3, 0, Math.PI * 2, ownBody ? (int) (170 + 85 * pulse) << 24 | 0xC29A5E : 0x90C29A5E);
        graphics.flush();

        for (int i = 0; i < n; i++) {
            Place place = places.get(i);
            double mid = -Math.PI / 2 + i * span;
            int radius = (inner + outer) / 2 + 4 + (i == hover ? 3 : 0);
            int x = cx + (int) Math.round(Math.cos(mid) * radius), y = cy + (int) Math.round(Math.sin(mid) * radius);
            if (place.form == null) { centred(graphics, "·", x, y - 4, MUTED); continue; }
            LivingEntity model = model(place.form);
            if (model != null) creature(graphics, model, x, y, i == hover ? 30 : 24, i == hover ? clock * 4 : 25);
        }
        centred(graphics, "You", cx, cy - 4, ownBody ? BRASS : TEXT);

        if (hover >= 0 && places.get(hover).form != null) {
            Place place = places.get(hover);
            String name = place.form.name();
            centred(graphics, name, cx, cy - outer - 26, TEXT);
            centred(graphics, place.id().equals(library.current()) ? "Wearing" : "Click to take this shape", cx, cy - outer - 15,
                    place.id().equals(library.current()) ? BRASS : MUTED);
        } else if (hover >= 0) {
            centred(graphics, "Study a creature to remember its shape", cx, cy - outer - 20, MUTED);
        } else if (hover == -2) {
            centred(graphics, ownBody ? "Your own body" : "Click to go back to your own body", cx, cy - outer - 20, ownBody ? BRASS : TEXT);
        }
        int remembered = (int) places.stream().filter(p -> p.form != null).count();
        centred(graphics, "Level " + library.level() + "  ·  Remembered " + remembered + " of " + library.capacity(), cx, cy + outer + 16, TEXT);
        centred(graphics, "Click a shape  ·  the centre for yourself  ·  Esc to cancel", cx, cy + outer + 28, MUTED);
    }

    /** A cached, unspawned example of a form to draw on the wheel. */
    private @Nullable LivingEntity model(ShapeshiftForm form) {
        if (minecraft.level == null) return null;
        return models.computeIfAbsent(form.key(), key -> {
            if (form.person()) return MorphClient.person(minecraft.level, form.personOrNull());
            Entity made;
            try { made = form.type().create(minecraft.level); } catch (RuntimeException e) { return null; }
            if (!(made instanceof LivingEntity living)) return null;
            if (living instanceof Mob mob) mob.setNoAi(true);
            return living;
        });
    }

    /** A small model of the creature, sized to fit {@code size} pixels and turned to {@code yaw}. */
    private static void creature(GuiGraphics graphics, LivingEntity model, int x, int y, int size, float yaw) {
        float extent = Math.max(model.getBbHeight(), model.getBbWidth() * 1.2F);
        float scale = size / Math.max(.4F, extent);
        model.yBodyRot = model.yBodyRotO = 180 + yaw;
        model.setYRot(180 + yaw);
        model.yHeadRot = model.yHeadRotO = 180 + yaw;
        model.setXRot(0);
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).mul(new Quaternionf().rotateX(-12 * Mth.DEG_TO_RAD));
        InventoryScreen.renderEntityInInventory(graphics, x, y, scale, new Vector3f(0, model.getBbHeight() / 2, 0), pose, null, model);
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
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        int index = hovered(mouseX, mouseY);
        if (index == -2) {
            if (!library.current().isEmpty()) PacketDistributor.sendToServer(new MorphChoicePayload(""));
            onClose();
        } else if (index >= 0) {
            Place place = places.get(index);
            if (place.form == null) return true;
            if (!place.id().equals(library.current())) PacketDistributor.sendToServer(new MorphChoicePayload(place.id()));
            onClose();
        }
        return true;
    }
}
