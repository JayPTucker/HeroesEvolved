package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Displays whether the player is currently viewing the present or past. */
@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class TemporalStatusHudOverlay {
    private static final ResourceLocation TEMPORAL_DIMENSION =
            ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "temporal_snapshot"
            );

    private TemporalStatusHudOverlay() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(
                        HeroesEvolved.MOD_ID,
                        "temporal_status"
                ),
                TemporalStatusHudOverlay::render
        );
    }

    private static void render(
            GuiGraphics guiGraphics,
            net.minecraft.client.DeltaTracker deltaTracker
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        boolean inPast = minecraft.level.dimension().location()
                .equals(TEMPORAL_DIMENSION);
        boolean hasTimeManipulation = ClientPowerState.getSnapshot()
                .abilities()
                .stream()
                .anyMatch(ability -> ability.unlocked()
                        && ability.abilityId().equals(
                        ModAbilities.TIME_MANIPULATION_ID
                ));

        // Visitors without the power still need to know that they are in the
        // past, while the PRESENT label is useful only to Time users.
        if (!inPast && !hasTimeManipulation) {
            return;
        }

        Component label = Component.translatable(inPast
                ? "hud.heroesevolved.past"
                : "hud.heroesevolved.present");
        int width = minecraft.font.width(label);
        int color = inPast ? 0xFFB8D8FF : 0xFFFFD7DC;

        guiGraphics.drawString(
                minecraft.font,
                label,
                guiGraphics.guiWidth() - width - 8,
                8,
                color,
                true
        );
    }
}
