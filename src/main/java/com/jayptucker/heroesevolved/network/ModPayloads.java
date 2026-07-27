package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(
    modid = HeroesEvolved.MOD_ID,
    bus = EventBusSubscriber.Bus.MOD
)
public final class ModPayloads {
    private ModPayloads() {
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(
        RegisterPayloadHandlersEvent event
    ) {
        event.registrar("2")
            .playToClient(
                PlayerPowerSyncPayload.TYPE,
                PlayerPowerSyncPayload.STREAM_CODEC,
                PlayerPowerSyncPayload::handle
            )
            .playToClient(
                OverexertionEffectPayload.TYPE,
                OverexertionEffectPayload.STREAM_CODEC,
                OverexertionEffectPayload::handle
            )
            .playToServer(
                ActivateAbilitySlotPayload.TYPE,
                ActivateAbilitySlotPayload.STREAM_CODEC,
                ActivateAbilitySlotPayload::handle
            );
    }
}