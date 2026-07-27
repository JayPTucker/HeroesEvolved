package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.ActivateAbilitySlotPayload;
import com.jayptucker.heroesevolved.network.FlightForwardInputPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT
)
public final class ClientKeyEvents {
    private static boolean ability1WasDown;
    private static boolean ability2WasDown;
    private static boolean ability3WasDown;
    private static boolean flightForwardWasDown;
    private static int forwardInputResendTicks;

    private ClientKeyEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (ModKeyMappings.OPEN_POWERS.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new PowersScreen());
            }
        }

        if (minecraft.player == null || minecraft.screen != null) {
            syncFlightForwardInput(false);
            return;
        }

        syncFlightForwardInput(minecraft.options.keyUp.isDown());

        boolean allowOverexertion =
                minecraft.options.keyShift.isDown();

        ability1WasDown = sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_1,
                AbilitySlot.PRIMARY,
                allowOverexertion,
                ability1WasDown
        );

        ability2WasDown = sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_2,
                AbilitySlot.SECONDARY,
                allowOverexertion,
                ability2WasDown
        );

        ability3WasDown = sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_3,
                AbilitySlot.TERTIARY,
                allowOverexertion,
                ability3WasDown
        );
    }

    private static boolean sendAbilityRequestIfPressed(
            net.minecraft.client.KeyMapping keyMapping,
            AbilitySlot slot,
            boolean allowOverexertion,
            boolean wasDown
    ) {
        boolean isDown = keyMapping.isDown();

        // Key-repeat events must not activate a toggle more than once.
        // A request is sent only when the key changes from up to down.
        if (isDown && !wasDown) {
            PacketDistributor.sendToServer(
                    new ActivateAbilitySlotPayload(
                            slot,
                            allowOverexertion
                    )
            );
        }

        return isDown;
    }

    private static void syncFlightForwardInput(boolean movingForward) {
        boolean stateChanged = flightForwardWasDown != movingForward;
        boolean shouldResend = movingForward
                && ++forwardInputResendTicks >= 5;

        if (!stateChanged && !shouldResend) {
            return;
        }

        flightForwardWasDown = movingForward;
        forwardInputResendTicks = 0;
        PacketDistributor.sendToServer(
                new FlightForwardInputPayload(movingForward)
        );
    }
}
