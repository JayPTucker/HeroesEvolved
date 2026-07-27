package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.AbilitySnapshot;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class PowersScreen extends Screen {
    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_HEIGHT = 180;

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
                        .bounds(left + 75, top + 148, 80, 20)
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
                drawAbility(guiGraphics, ability, left + 14, abilityY);
                abilityY += 34;
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawAbility(
            GuiGraphics guiGraphics,
            AbilitySnapshot ability,
            int x,
            int y
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