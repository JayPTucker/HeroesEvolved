package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.AbilitySnapshot;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class EnergyHudOverlay {
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 3;

    private EnergyHudOverlay() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "energy_bar"
                ),
                EnergyHudOverlay::render
        );
    }

    private static void render(
            GuiGraphics guiGraphics,
            DeltaTracker deltaTracker
    ) {
        PlayerPowerSyncPayload snapshot = ClientPowerState.getSnapshot();

        if (snapshot.maximumEnergy() <= 0 || snapshot.abilities().stream()
                .noneMatch(AbilitySnapshot::unlocked)) {
            return;
        }

        int currentEnergy = Math.min(
            snapshot.energy(),
            snapshot.maximumEnergy()
        );

        int filledWidth = Math.round(
            BAR_WIDTH * (
                currentEnergy / (float) snapshot.maximumEnergy()
            )
        );

        int barX = (guiGraphics.guiWidth() / 2) + 10;
        int barY = guiGraphics.guiHeight() - 50;

        guiGraphics.fill(
                barX - 1,
                barY - 1,
                barX + BAR_WIDTH + 1,
                barY + BAR_HEIGHT + 1,
                0xFF4A0A12
        );

        guiGraphics.fill(
                barX,
                barY,
                barX + BAR_WIDTH,
                barY + BAR_HEIGHT,
                0xB0120709
        );

        guiGraphics.fill(
                barX,
                barY,
                barX + filledWidth,
                barY + BAR_HEIGHT,
                0xFFC51E32
        );

        String energyText = currentEnergy + " / " + snapshot.maximumEnergy();

        guiGraphics.drawString(
            Minecraft.getInstance().font,
            energyText,
            barX,
            barY - 10,
            0xFFFFD7DC,
            true
        );
    }
}