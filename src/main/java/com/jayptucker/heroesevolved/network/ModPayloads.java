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
        event.registrar("57")
            .playToServer(CryoRideInputPayload.TYPE, CryoRideInputPayload.STREAM_CODEC, CryoRideInputPayload::handle)
            .playToClient(CryoRideStatePayload.TYPE, CryoRideStatePayload.STREAM_CODEC, CryoRideStatePayload::handle)
            .playToServer(CryoShellInputPayload.TYPE, CryoShellInputPayload.STREAM_CODEC, CryoShellInputPayload::handle)
            .playToClient(CryoShellStatePayload.TYPE, CryoShellStatePayload.STREAM_CODEC, CryoShellStatePayload::handle)
            .playToClient(OpticalStatePayload.TYPE, OpticalStatePayload.STREAM_CODEC, OpticalStatePayload::handle)
            .playToClient(OpticalDecoyPayload.TYPE, OpticalDecoyPayload.STREAM_CODEC, OpticalDecoyPayload::handle)
            .playToServer(DecoyInteractPayload.TYPE, DecoyInteractPayload.STREAM_CODEC, DecoyInteractPayload::handle)
            .playToClient(PyroStatePayload.TYPE, PyroStatePayload.STREAM_CODEC, PyroStatePayload::handle)
            .playToServer(PyroFlightInputPayload.TYPE, PyroFlightInputPayload.STREAM_CODEC, PyroFlightInputPayload::handle)
            .playToClient(IllusionPayload.TYPE, IllusionPayload.STREAM_CODEC, IllusionPayload::handle)
            .playToClient(MindStatePayload.TYPE, MindStatePayload.STREAM_CODEC, MindStatePayload::handle)
            .playToServer(MindInputPayload.TYPE, MindInputPayload.STREAM_CODEC, MindInputPayload::handle)
            .playBidirectional(MindTypingPayload.TYPE, MindTypingPayload.STREAM_CODEC, MindTypingPayload::handle)
            .playToServer(JournalRequestPayload.TYPE, JournalRequestPayload.STREAM_CODEC, JournalRequestPayload::handle)
            .playToServer(SpeedCatchPayload.TYPE, SpeedCatchPayload.STREAM_CODEC, SpeedCatchPayload::handle)
            .playToClient(StoryJournalPayload.TYPE, StoryJournalPayload.STREAM_CODEC, StoryJournalPayload::handle)
            .playToClient(SonicVoicePayload.TYPE, SonicVoicePayload.STREAM_CODEC, SonicVoicePayload::handle)
            .playToClient(UnseenPayload.TYPE, UnseenPayload.STREAM_CODEC, UnseenPayload::handle)
            .playToClient(RegenerationStatePayload.TYPE, RegenerationStatePayload.STREAM_CODEC, RegenerationStatePayload::handle)
            .playToClient(ClarityPayload.TYPE, ClarityPayload.STREAM_CODEC, ClarityPayload::handle)
            .playToClient(ResonancePayload.TYPE, ResonancePayload.STREAM_CODEC, ResonancePayload::handle)
            .playToClient(MomentumPayload.TYPE, MomentumPayload.STREAM_CODEC, MomentumPayload::handle)
            .playToClient(DensityPayload.TYPE, DensityPayload.STREAM_CODEC, DensityPayload::handle)
            .playToClient(StrainPayload.TYPE, StrainPayload.STREAM_CODEC, StrainPayload::handle)
            .playToClient(BodyStatePayload.TYPE, BodyStatePayload.STREAM_CODEC, BodyStatePayload::handle)
            .playToClient(TimeGhostPayload.TYPE, TimeGhostPayload.STREAM_CODEC, TimeGhostPayload::handle)
            .playToClient(LoadoutPayload.TYPE, LoadoutPayload.STREAM_CODEC, LoadoutPayload::handle)
            .playToServer(ActionInputPayload.TYPE, ActionInputPayload.STREAM_CODEC, ActionInputPayload::handle)
            .playToServer(BindKeyPayload.TYPE, BindKeyPayload.STREAM_CODEC, BindKeyPayload::handle)
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
            .playToClient(GridFieldPayload.TYPE, GridFieldPayload.STREAM_CODEC, GridFieldPayload::handle)
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
            .playToClient(SetPiecePayload.TYPE, SetPiecePayload.STREAM_CODEC, SetPiecePayload::handle)
            .playToClient(SpeedCyclonePayload.TYPE, SpeedCyclonePayload.STREAM_CODEC, SpeedCyclonePayload::handle)
            .playToClient(OverrunPayload.TYPE, OverrunPayload.STREAM_CODEC, OverrunPayload::handle)
            .playToClient(OverrunStatusPayload.TYPE, OverrunStatusPayload.STREAM_CODEC, OverrunStatusPayload::handle)
            .playToClient(RewindTravelPayload.TYPE, RewindTravelPayload.STREAM_CODEC, RewindTravelPayload::handle)
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
