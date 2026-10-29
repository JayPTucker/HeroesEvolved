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
        event.registrar("38")
            .playToServer(com.jayptucker.heroesevolved.expansion.IncidentActionPayload.TYPE, com.jayptucker.heroesevolved.expansion.IncidentActionPayload.STREAM_CODEC, com.jayptucker.heroesevolved.expansion.IncidentActionPayload::handle)
            .playToServer(JournalRequestPayload.TYPE, JournalRequestPayload.STREAM_CODEC, JournalRequestPayload::handle)
            .playToClient(StoryJournalPayload.TYPE, StoryJournalPayload.STREAM_CODEC, StoryJournalPayload::handle)
            .playToClient(SonicVoicePayload.TYPE, SonicVoicePayload.STREAM_CODEC, SonicVoicePayload::handle)
            .playToClient(UnseenPayload.TYPE, UnseenPayload.STREAM_CODEC, UnseenPayload::handle)
            .playToClient(RegenerationStatePayload.TYPE, RegenerationStatePayload.STREAM_CODEC, RegenerationStatePayload::handle)
            .playToClient(BodyStatePayload.TYPE, BodyStatePayload.STREAM_CODEC, BodyStatePayload::handle)
            .playToClient(TimeGhostPayload.TYPE, TimeGhostPayload.STREAM_CODEC, TimeGhostPayload::handle)
            .playToServer(GiveUpPayload.TYPE, GiveUpPayload.STREAM_CODEC, GiveUpPayload::handle)
            .playBidirectional(DeathRewindPayload.TYPE, DeathRewindPayload.STREAM_CODEC, new net.neoforged.neoforge.network.handling.DirectionalPayloadHandler<>(DeathRewindPayload::handleClient, DeathRewindPayload::handleServer))
            .playToServer(SkillTreeActionPayload.TYPE, SkillTreeActionPayload.STREAM_CODEC, SkillTreeActionPayload::handle)
            .playToClient(SkillTreeStatePayload.TYPE, SkillTreeStatePayload.STREAM_CODEC, SkillTreeStatePayload::handle)
            .playToServer(FlightSpeedInputPayload.TYPE, FlightSpeedInputPayload.STREAM_CODEC, FlightSpeedInputPayload::handle)
            .playToClient(NuclearCloudPayload.TYPE, NuclearCloudPayload.STREAM_CODEC, NuclearCloudPayload::handle)
            .playToClient(RadiationStatePayload.TYPE, RadiationStatePayload.STREAM_CODEC, RadiationStatePayload::handle)
            .playToClient(BlizzardStatePayload.TYPE, BlizzardStatePayload.STREAM_CODEC, BlizzardStatePayload::handle)
            .playToClient(FrozenEntityPayload.TYPE, FrozenEntityPayload.STREAM_CODEC, FrozenEntityPayload::handle)
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
