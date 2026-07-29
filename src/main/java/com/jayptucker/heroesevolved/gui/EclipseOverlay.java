package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ClientEclipseState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Adds a restrained blue-black tint while the server reports an Eclipse. */
@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class EclipseOverlay {
    private static final int ECLIPSE_TINT = 0x80030A1A;

    private EclipseOverlay() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(
                        HeroesEvolved.MOD_ID,
                        "eclipse_tint"
                ),
                EclipseOverlay::render
        );
    }

    private static void render(
            GuiGraphics guiGraphics,
            net.minecraft.client.DeltaTracker deltaTracker
    ) {
        if (!ClientEclipseState.isEclipseActive()) {
            return;
        }

        guiGraphics.fill(
                0,
                0,
                guiGraphics.guiWidth(),
                guiGraphics.guiHeight(),
                ECLIPSE_TINT
        );
    }
}
