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

import java.util.Map;

/**
 * Displays whether the player is in the present or the past, and for the story's past places,
 * when. The city and the annex are Odessa on the Last Day; a snapshot is the player's own recording.
 */
@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class TemporalStatusHudOverlay {
    private static final Map<ResourceLocation, String> PAST_LABELS = Map.of(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "temporal_snapshot"),
            "hud.heroesevolved.past",
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_fracture"),
            "hud.heroesevolved.past_last_day",
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "unwritten_annex"),
            "hud.heroesevolved.past_annex"
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

        String pastLabel = PAST_LABELS.get(minecraft.level.dimension().location());
        boolean inPast = pastLabel != null;
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
                ? pastLabel
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
