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
        event.registrar("17")
            .playToClient(PowerIntroPayload.TYPE, PowerIntroPayload.STREAM_CODEC, PowerIntroPayload::handle)
            .playToClient(TimeIntroPayload.TYPE, TimeIntroPayload.STREAM_CODEC, TimeIntroPayload::handle)
            .playToServer(
                ReleaseAbilitySlotPayload.TYPE,
                ReleaseAbilitySlotPayload.STREAM_CODEC,
                ReleaseAbilitySlotPayload::handle
            )
            .playToClient(
                ArmPosePayload.TYPE,
                ArmPosePayload.STREAM_CODEC,
                ArmPosePayload::handle
            )
            .playToClient(
                AbilityEffectPayload.TYPE,
                AbilityEffectPayload.STREAM_CODEC,
                AbilityEffectPayload::handle
            )
            .playToClient(
                ScreenTintPayload.TYPE,
                ScreenTintPayload.STREAM_CODEC,
                ScreenTintPayload::handle
            )
            .playToClient(
                AdminStatePayload.TYPE,
                AdminStatePayload.STREAM_CODEC,
                AdminStatePayload::handle
            )
            .playToServer(
                AdminActionPayload.TYPE,
                AdminActionPayload.STREAM_CODEC,
                AdminActionPayload::handle
            )
            .playToClient(
                CameraShakePayload.TYPE,
                CameraShakePayload.STREAM_CODEC,
                CameraShakePayload::handle
            )
            .playToClient(
                PlayerPowerSyncPayload.TYPE,
                PlayerPowerSyncPayload.STREAM_CODEC,
                PlayerPowerSyncPayload::handle
            )
            .playToClient(
                EclipseStatePayload.TYPE,
                EclipseStatePayload.STREAM_CODEC,
                EclipseStatePayload::handle
            )
            .playToClient(
                OverexertionEffectPayload.TYPE,
                OverexertionEffectPayload.STREAM_CODEC,
                OverexertionEffectPayload::handle
            )
            .playToClient(
                FlightVisualStatePayload.TYPE,
                FlightVisualStatePayload.STREAM_CODEC,
                FlightVisualStatePayload::handle
            )
            .playToServer(
                ActivateAbilitySlotPayload.TYPE,
                ActivateAbilitySlotPayload.STREAM_CODEC,
                ActivateAbilitySlotPayload::handle
            )
            .playToServer(
                FlightForwardInputPayload.TYPE,
                FlightForwardInputPayload.STREAM_CODEC,
                FlightForwardInputPayload::handle
            );
    }
}
