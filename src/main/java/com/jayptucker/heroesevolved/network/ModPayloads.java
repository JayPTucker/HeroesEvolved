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
        event.registrar("132")
            .playToClient(FutureLookPayload.TYPE, FutureLookPayload.STREAM_CODEC, FutureLookPayload::handle)
            .playToClient(GuidanceStatePayload.TYPE, GuidanceStatePayload.STREAM_CODEC, GuidanceStatePayload::handle)
            .playToServer(GuidanceActionPayload.TYPE, GuidanceActionPayload.STREAM_CODEC, GuidanceActionPayload::handle)
            .playToClient(EndBattlePayload.TYPE, EndBattlePayload.STREAM_CODEC, EndBattlePayload::handle)
            .playToClient(GodModePayload.TYPE, GodModePayload.STREAM_CODEC, GodModePayload::handle)
            .playToClient(MobTimePayload.TYPE, MobTimePayload.STREAM_CODEC, MobTimePayload::handle)
            .playToClient(SuperchargedPayload.TYPE, SuperchargedPayload.STREAM_CODEC, SuperchargedPayload::handle)
            .playToClient(AmpStatePayload.TYPE, AmpStatePayload.STREAM_CODEC, AmpStatePayload::handle)
            .playToClient(AmpSightPayload.TYPE, AmpSightPayload.STREAM_CODEC, AmpSightPayload::handle)
            .playToClient(TimeFlowPayload.TYPE, TimeFlowPayload.STREAM_CODEC, TimeFlowPayload::handle)
            .playToClient(TemporalProjectilePayload.TYPE, TemporalProjectilePayload.STREAM_CODEC, TemporalProjectilePayload::handle)
            .playToClient(TemporalCatchStatePayload.TYPE, TemporalCatchStatePayload.STREAM_CODEC, TemporalCatchStatePayload::handle)
            .playToClient(SkyburstMotionPayload.TYPE, SkyburstMotionPayload.STREAM_CODEC, SkyburstMotionPayload::handle)
            .playToClient(BurnPayload.TYPE, BurnPayload.STREAM_CODEC, BurnPayload::handle)
            .playToClient(FrictionPayload.TYPE, FrictionPayload.STREAM_CODEC, FrictionPayload::handle)
            .playToClient(CycloneStatusPayload.TYPE, CycloneStatusPayload.STREAM_CODEC, CycloneStatusPayload::handle)
            .playToServer(CycloneSpeedInputPayload.TYPE, CycloneSpeedInputPayload.STREAM_CODEC, CycloneSpeedInputPayload::handle)
            .playToServer(SpeedSkidPayload.TYPE, SpeedSkidPayload.STREAM_CODEC, SpeedSkidPayload::handle)
            .playToClient(MimicMemoryPayload.TYPE, MimicMemoryPayload.STREAM_CODEC, MimicMemoryPayload::handle)
            .playToClient(TimeVeilPayload.TYPE, TimeVeilPayload.STREAM_CODEC, TimeVeilPayload::handle)
            .playToClient(VibeVisionPayload.TYPE, VibeVisionPayload.STREAM_CODEC, VibeVisionPayload::handle)
            .playToClient(VibeHologramPayload.TYPE, VibeHologramPayload.STREAM_CODEC, VibeHologramPayload::handle)
            .playToServer(MimicChoicePayload.TYPE, MimicChoicePayload.STREAM_CODEC, MimicChoicePayload::handle)
            .playToServer(CryoRideInputPayload.TYPE, CryoRideInputPayload.STREAM_CODEC, CryoRideInputPayload::handle)
            .playToClient(CryoRideStatePayload.TYPE, CryoRideStatePayload.STREAM_CODEC, CryoRideStatePayload::handle)
            .playToServer(CryoShellInputPayload.TYPE, CryoShellInputPayload.STREAM_CODEC, CryoShellInputPayload::handle)
            .playToClient(CryoShellStatePayload.TYPE, CryoShellStatePayload.STREAM_CODEC, CryoShellStatePayload::handle)
            .playToClient(OpticalStatePayload.TYPE, OpticalStatePayload.STREAM_CODEC, OpticalStatePayload::handle)
            .playToClient(PyroStatePayload.TYPE, PyroStatePayload.STREAM_CODEC, PyroStatePayload::handle)
            .playToServer(PyroFlightInputPayload.TYPE, PyroFlightInputPayload.STREAM_CODEC, PyroFlightInputPayload::handle)
            .playToServer(FightingStylePayload.TYPE, FightingStylePayload.STREAM_CODEC, FightingStylePayload::handle)
            .playToServer(FinisherPayload.TYPE, FinisherPayload.STREAM_CODEC, FinisherPayload::handle)
            .playToClient(FinisherStatePayload.TYPE, FinisherStatePayload.STREAM_CODEC, FinisherStatePayload::handle)
            .playToClient(FinisherRunPayload.TYPE, FinisherRunPayload.STREAM_CODEC, FinisherRunPayload::handle)
            .playToClient(UltimateCameraPayload.TYPE, UltimateCameraPayload.STREAM_CODEC, UltimateCameraPayload::handle)
            .playToClient(HydroStatePayload.TYPE, HydroStatePayload.STREAM_CODEC, HydroStatePayload::handle)
            .playToClient(MatterStatePayload.TYPE, MatterStatePayload.STREAM_CODEC, MatterStatePayload::handle)
            .playToClient(IllusionPayload.TYPE, IllusionPayload.STREAM_CODEC, IllusionPayload::handle)
            .playToClient(GiftedMobPayload.TYPE, GiftedMobPayload.STREAM_CODEC, GiftedMobPayload::handle)
            .playToClient(DistressPayload.TYPE, DistressPayload.STREAM_CODEC, DistressPayload::handle)
            .playToClient(OverloadPayload.TYPE, OverloadPayload.STREAM_CODEC, OverloadPayload::handle)
            .playToClient(MeteorPayload.TYPE, MeteorPayload.STREAM_CODEC, MeteorPayload::handle)
            .playToClient(StarfallPayload.TYPE, StarfallPayload.STREAM_CODEC, StarfallPayload::handle)
            .playToClient(CompanyAttentionPayload.TYPE, CompanyAttentionPayload.STREAM_CODEC, CompanyAttentionPayload::handle)
            .playToClient(MindStatePayload.TYPE, MindStatePayload.STREAM_CODEC, MindStatePayload::handle)
            .playToServer(MindInputPayload.TYPE, MindInputPayload.STREAM_CODEC, MindInputPayload::handle)
            .playBidirectional(MindTypingPayload.TYPE, MindTypingPayload.STREAM_CODEC, MindTypingPayload::handle)
            .playToServer(JournalRequestPayload.TYPE, JournalRequestPayload.STREAM_CODEC, JournalRequestPayload::handle)
            .playToServer(SpeedCatchPayload.TYPE, SpeedCatchPayload.STREAM_CODEC, SpeedCatchPayload::handle)
            .playToServer(SpeedStrideInputPayload.TYPE, SpeedStrideInputPayload.STREAM_CODEC, SpeedStrideInputPayload::handle)
            .playToClient(StoryJournalPayload.TYPE, StoryJournalPayload.STREAM_CODEC, StoryJournalPayload::handle)
            .playToClient(SonicVoicePayload.TYPE, SonicVoicePayload.STREAM_CODEC, SonicVoicePayload::handle)
            .playToClient(UnseenPayload.TYPE, UnseenPayload.STREAM_CODEC, UnseenPayload::handle)
            .playToClient(MorphStatePayload.TYPE, MorphStatePayload.STREAM_CODEC, MorphStatePayload::handle)
            .playToClient(SuspicionPayload.TYPE, SuspicionPayload.STREAM_CODEC, SuspicionPayload::handle)
            .playToClient(FormLibraryPayload.TYPE, FormLibraryPayload.STREAM_CODEC, FormLibraryPayload::handle)
            .playToServer(MorphChoicePayload.TYPE, MorphChoicePayload.STREAM_CODEC, MorphChoicePayload::handle)
            .playToClient(FrequencyListPayload.TYPE, FrequencyListPayload.STREAM_CODEC, FrequencyListPayload::handle)
            .playToServer(FrequencyChoicePayload.TYPE, FrequencyChoicePayload.STREAM_CODEC, FrequencyChoicePayload::handle)
            .playToClient(BreachViewPayload.TYPE, BreachViewPayload.STREAM_CODEC, BreachViewPayload::handle)
            .playToClient(BreachActorsPayload.TYPE, BreachActorsPayload.STREAM_CODEC, BreachActorsPayload::handle)
            .playToClient(RegenerationStatePayload.TYPE, RegenerationStatePayload.STREAM_CODEC, RegenerationStatePayload::handle)
            .playToClient(RegrowthPayload.TYPE, RegrowthPayload.STREAM_CODEC, RegrowthPayload::handle)
            .playToClient(ResonancePayload.TYPE, ResonancePayload.STREAM_CODEC, ResonancePayload::handle)
            .playToClient(MomentumPayload.TYPE, MomentumPayload.STREAM_CODEC, MomentumPayload::handle)
            .playToClient(DensityPayload.TYPE, DensityPayload.STREAM_CODEC, DensityPayload::handle)
            .playToClient(StrainPayload.TYPE, StrainPayload.STREAM_CODEC, StrainPayload::handle)
            .playToClient(BodyStatePayload.TYPE, BodyStatePayload.STREAM_CODEC, BodyStatePayload::handle)
            .playToClient(TimeGhostPayload.TYPE, TimeGhostPayload.STREAM_CODEC, TimeGhostPayload::handle)
            .playToClient(LoadoutPayload.TYPE, LoadoutPayload.STREAM_CODEC, LoadoutPayload::handle)
            .playToServer(ActionInputPayload.TYPE, ActionInputPayload.STREAM_CODEC, ActionInputPayload::handle)
            .playToServer(BindKeyPayload.TYPE, BindKeyPayload.STREAM_CODEC, BindKeyPayload::handle)
            .playToServer(BindWheelPayload.TYPE, BindWheelPayload.STREAM_CODEC, BindWheelPayload::handle)
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
            .playToClient(LastDayGuidePayload.TYPE, LastDayGuidePayload.STREAM_CODEC, LastDayGuidePayload::handle)
            .playToClient(DazePayload.TYPE, DazePayload.STREAM_CODEC, DazePayload::handle)
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
            .playToClient(StaminaPayload.TYPE, StaminaPayload.STREAM_CODEC, StaminaPayload::handle)
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
            )
            .playToServer(
                FlightRollPayload.TYPE,
                FlightRollPayload.STREAM_CODEC,
                FlightRollPayload::handle
            );
    }
}
