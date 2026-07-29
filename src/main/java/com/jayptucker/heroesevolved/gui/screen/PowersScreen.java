package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.AbilitySnapshot;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

public final class PowersScreen extends Screen {
    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_HEIGHT = 202;
    private static final int MASTERY_BAR_WIDTH = 190;
    private static final int MASTERY_BAR_HEIGHT = 8;

    public PowersScreen() {
        super(Component.translatable("screen.heroesevolved.powers"));
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        addRenderableWidget(
                Button.builder(
                                CommonComponents.GUI_DONE,
                                button -> onClose()
                        )
                        .bounds(left + 75, top + 170, 80, 20)
                        .build()
        );
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        guiGraphics.fill(0, 0, width, height, 0x40000000);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        guiGraphics.fill(
                left - 1,
                top - 1,
                left + PANEL_WIDTH + 1,
                top + PANEL_HEIGHT + 1,
                0xFF6B101D
        );

        guiGraphics.fill(
                left,
                top,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0xFF16090D
        );

        guiGraphics.drawCenteredString(
                font,
                title,
                width / 2,
                top + 12,
                0xFFFFD7DC
        );

        PlayerPowerSyncPayload snapshot = ClientPowerState.getSnapshot();

        guiGraphics.drawString(
                font,
                Component.translatable(
                        "screen.heroesevolved.energy",
                        snapshot.energy(),
                        snapshot.maximumEnergy()
                ),
                left + 14,
                top + 35,
                0xFFC51E32,
                false
        );

        int abilityY = top + 58;

        if (snapshot.abilities().isEmpty()) {
            guiGraphics.drawString(
                    font,
                    Component.translatable(
                            "screen.heroesevolved.no_abilities"
                    ),
                    left + 14,
                    abilityY,
                    0xFFB89A9F,
                    false
            );
        } else {
            for (AbilitySnapshot ability : snapshot.abilities()) {
                drawAbility(
                        guiGraphics,
                        ability,
                        left + 14,
                        abilityY,
                        mouseX,
                        mouseY
                );
                abilityY += 52;
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawAbility(
            GuiGraphics guiGraphics,
            AbilitySnapshot ability,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        String translationKey = "ability."
                + ability.abilityId().getNamespace()
                + "."
                + ability.abilityId().getPath();

        int statusColor = ability.unlocked()
                ? 0xFF92D66C
                : 0xFFC47C7C;

        guiGraphics.drawString(
                font,
                Component.translatable(translationKey),
                x,
                y,
                0xFFFFD7DC,
                false
        );

        guiGraphics.drawString(
                font,
                Component.translatable(
                        ability.unlocked()
                                ? "screen.heroesevolved.unlocked"
                                : "screen.heroesevolved.dormant"
                ),
                x + 130,
                y,
                statusColor,
                false
        );

        guiGraphics.drawString(
                font,
                Component.translatable(
                        "screen.heroesevolved.ability_details",
                        ability.level(),
                        ability.mastery()
                ),
                x,
                y + 12,
                0xFFB89A9F,
                false
        );

        drawMasteryBar(guiGraphics, ability, x, y + 27, mouseX, mouseY);
    }

    private void drawMasteryBar(
            GuiGraphics guiGraphics,
            AbilitySnapshot ability,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        guiGraphics.fill(
                x,
                y,
                x + MASTERY_BAR_WIDTH,
                y + MASTERY_BAR_HEIGHT,
                0xFF3A2028
        );

        float progress = masteryProgress(ability);
        int filledWidth = Math.round(MASTERY_BAR_WIDTH * progress);

        if (filledWidth > 0) {
            guiGraphics.fill(
                    x,
                    y,
                    x + filledWidth,
                    y + MASTERY_BAR_HEIGHT,
                    ability.unlocked() ? 0xFFC51E32 : 0xFF6B3A45
            );
        }

        boolean maximumLevel = ability.level() >= ability.maximumLevel();
        Component progressText = maximumLevel
                ? Component.translatable("screen.heroesevolved.mastery_complete")
                : Component.translatable(
                        "screen.heroesevolved.mastery_progress",
                        ability.mastery(),
                        ability.masteryRequiredForNextLevel()
                );

        guiGraphics.drawString(
                font,
                progressText,
                x,
                y + MASTERY_BAR_HEIGHT + 3,
                0xFFB89A9F,
                false
        );

        if (mouseX >= x && mouseX < x + MASTERY_BAR_WIDTH
                && mouseY >= y && mouseY < y + MASTERY_BAR_HEIGHT) {
            guiGraphics.renderTooltip(
                    font,
                    masteryTooltip(ability),
                    Optional.empty(),
                    mouseX,
                    mouseY
            );
        }
    }

    private static float masteryProgress(AbilitySnapshot ability) {
        if (ability.level() >= ability.maximumLevel()) {
            return 1.0F;
        }

        long range = ability.masteryRequiredForNextLevel()
                - ability.masteryRequiredForCurrentLevel();

        if (range <= 0) {
            return 0.0F;
        }

        long earnedInLevel = ability.mastery()
                - ability.masteryRequiredForCurrentLevel();
        return Math.clamp(earnedInLevel / (float) range, 0.0F, 1.0F);
    }

    private static List<Component> masteryTooltip(AbilitySnapshot ability) {
        if (!ability.unlocked()) {
            return List.of(Component.translatable(
                    "screen.heroesevolved.mastery_dormant"
            ));
        }

        return List.of(
                Component.translatable("screen.heroesevolved.mastery_tooltip_title"),
                Component.translatable("screen.heroesevolved.mastery_tooltip_power"),
                Component.translatable("screen.heroesevolved.mastery_tooltip_combat"),
                Component.translatable("screen.heroesevolved.mastery_tooltip_active")
        );
    }

    @Override
    public void renderBackground(
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
    // Intentionally empty: the Powers screen should not blur the game world.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
