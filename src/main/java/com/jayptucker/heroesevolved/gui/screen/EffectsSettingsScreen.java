package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.client.VisualComfort;
import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-player comfort settings for power effects: camera shake, flashes,
 * cold fog and photosensitivity. Only affects what this player sees.
 */
public final class EffectsSettingsScreen extends Screen {
    private static final double[] STEPS = {1.0D, 0.75D, 0.5D, 0.25D, 0.0D};
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 190;

    private final Screen parent;

    public EffectsSettingsScreen(Screen parent) {
        super(Component.literal("Effects Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int x = left + 20;
        int buttonWidth = PANEL_WIDTH - 40;

        addRenderableWidget(cycle(x, top + 34, buttonWidth, "Camera shake",
                ClientVisualSettings.SHAKE, 0.0D));
        addRenderableWidget(cycle(x, top + 58, buttonWidth, "Screen flashes",
                ClientVisualSettings.FLASH, 0.0D));
        addRenderableWidget(cycle(x, top + 82, buttonWidth, "Cold fog",
                ClientVisualSettings.FOG, 0.25D));
        addRenderableWidget(Button.builder(reduceLabel(), button -> {
                    ClientVisualSettings.REDUCE_FLASHING.set(!ClientVisualSettings.REDUCE_FLASHING.get());
                    ClientVisualSettings.SPEC.save();
                    button.setMessage(reduceLabel());
                })
                .bounds(x, top + 106, buttonWidth, 20)
                .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + PANEL_WIDTH / 2 - 40, top + PANEL_HEIGHT - 28, 80, 20)
                .build());
    }

    /** A button that steps a 0-100% setting down through fixed values. */
    private static Button cycle(
            int x,
            int y,
            int width,
            String name,
            ModConfigSpec.DoubleValue value,
            double minimum
    ) {
        return Button.builder(label(name, value.get()), button -> {
                    double next = next(value.get(), minimum);
                    value.set(next);
                    ClientVisualSettings.SPEC.save();
                    button.setMessage(label(name, next));
                })
                .bounds(x, y, width, 20)
                .build();
    }

    private static double next(double current, double minimum) {
        for (double step : STEPS) {
            if (step < current - 0.001D && step >= minimum) {
                return step;
            }
        }
        return STEPS[0];
    }

    private static Component label(String name, double value) {
        return Component.literal(name + ": " + (value <= 0.0D
                ? "Off" : Math.round(value * 100) + "%"));
    }

    private static Component reduceLabel() {
        return Component.literal("Reduce flashing: " + (ClientVisualSettings.REDUCE_FLASHING.get()
                ? "On" : VisualComfort.reduceFlashing() ? "On (Minecraft setting)" : "Off"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x60000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF6B101D);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF16090D);
        graphics.drawCenteredString(font, title, width / 2, top + 12, 0xFFFFD7DC);
        graphics.drawCenteredString(font, Component.literal(
                        "Only changes what you see. Click to cycle."),
                width / 2, top + 134, 0xFFB89A9F);
        graphics.drawCenteredString(font, Component.literal(
                        "Minecraft's Hide Lightning Flashes and Distortion"),
                width / 2, top + 146, 0xFF8A7075);
        graphics.drawCenteredString(font, Component.literal(
                        "Effects options are respected too."),
                width / 2, top + 156, 0xFF8A7075);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The panel draws its own backdrop.
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
