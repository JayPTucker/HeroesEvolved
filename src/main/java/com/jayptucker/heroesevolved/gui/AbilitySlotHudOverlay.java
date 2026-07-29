package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.AbilityActionSnapshot;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
public final class AbilitySlotHudOverlay {
    private static final int START_X = 8;
    private static final int START_Y = 8;
    private static final int LINE_HEIGHT = 12;

    private AbilitySlotHudOverlay() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(
                        HeroesEvolved.MOD_ID,
                        "ability_slots"
                ),
                AbilitySlotHudOverlay::render
        );
    }

    private static void render(
            GuiGraphics guiGraphics,
            net.minecraft.client.DeltaTracker deltaTracker
    ) {
        // Dormant powers are intentionally unknown to the player. Do not
        // reveal their actions or controls until the awakening occurs.
        if (!hasUnlockedAbility()) {
            return;
        }

        int y = START_Y;

        for (AbilityActionSnapshot action
                : ClientPowerState.getSnapshot().actions()) {
            boolean onCooldown = isOnCooldown(action);

            int textColor = getTextColor(action, onCooldown);
            String text = buildDisplayText(action, onCooldown);

            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    text,
                    START_X,
                    y,
                    textColor,
                    true
            );

            y += LINE_HEIGHT;
        }

        if (!hasUnlockedFlightBoost()) {
            return;
        }

        // Flight Boost is a Flight-only control hint, not a universal action.
        KeyMapping sprintKey = Minecraft.getInstance().options.keySprint;
        KeyMapping forwardKey = Minecraft.getInstance().options.keyUp;

        String boostText = sprintKey.getTranslatedKeyMessage().getString()
                + " + "
                + forwardKey.getTranslatedKeyMessage().getString()
                + "  Flight Boost";

        guiGraphics.drawString(
                Minecraft.getInstance().font,
                boostText,
                START_X,
                y,
                0xFFFFD7DC,
                true
        );
    }

    private static boolean hasUnlockedFlightBoost() {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                ability -> ability.unlocked()
                        && ability.abilityId().equals(ModAbilities.FLIGHT_ID)
                        && ability.level() >= 2
        );
    }

    private static boolean hasUnlockedAbility() {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                ability -> ability.unlocked()
        );
    }

    private static boolean isOnCooldown(
            AbilityActionSnapshot action
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        return minecraft.level != null
                && action.cooldownEndGameTime()
                > minecraft.level.getGameTime();
    }

    private static int getTextColor(
            AbilityActionSnapshot action,
            boolean onCooldown
    ) {
        // Locked actions and actions on cooldown intentionally appear gray.
        if (!action.unlocked() || onCooldown) {
            return 0xFF777777;
        }

        return 0xFFFFD7DC;
    }

    private static String buildDisplayText(
            AbilityActionSnapshot action,
            boolean onCooldown
    ) {
        String keyName = getKeyMapping(action.slot())
                .getTranslatedKeyMessage()
                .getString();

        String actionName = Component.translatable(
                action.displayNameKey()
        ).getString();

        if (!action.unlocked()) {
            return keyName + "  " + actionName + " [Locked]";
        }

        if (onCooldown) {
            long remainingTicks = action.cooldownEndGameTime()
                    - Minecraft.getInstance().level.getGameTime();

            long remainingSeconds = (long) Math.ceil(
                    remainingTicks / 20.0D
            );

            return keyName + "  " + actionName
                    + " (" + remainingSeconds + "s)";
        }

        return keyName + "  " + actionName;
    }

    private static KeyMapping getKeyMapping(AbilitySlot slot) {
        return switch (slot) {
            case PRIMARY -> ModKeyMappings.ABILITY_1;
            case SECONDARY -> ModKeyMappings.ABILITY_2;
            case TERTIARY -> ModKeyMappings.ABILITY_3;
        };
    }
}
