package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.ActivateAbilitySlotPayload;
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
            return;
        }

        boolean allowOverexertion =
                minecraft.options.keyShift.isDown();

        sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_1,
                AbilitySlot.PRIMARY,
                allowOverexertion
        );

        sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_2,
                AbilitySlot.SECONDARY,
                allowOverexertion
        );

        sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_3,
                AbilitySlot.TERTIARY,
                allowOverexertion
        );
    }

    private static void sendAbilityRequestIfPressed(
            net.minecraft.client.KeyMapping keyMapping,
            AbilitySlot slot,
            boolean allowOverexertion
    ) {
        while (keyMapping.consumeClick()) {
            PacketDistributor.sendToServer(
                    new ActivateAbilitySlotPayload(
                            slot,
                            allowOverexertion
                    )
            );
        }
    }
}