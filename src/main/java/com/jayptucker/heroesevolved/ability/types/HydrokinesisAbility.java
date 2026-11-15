package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.intro.PowerIntroService;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Hydrokinesis, inspired by the sea's own child of myth and by the waterbenders of the Water Tribes.
 *
 * <ul>
 *   <li>Level 1, Water Whip: crack a whip of water along the aim; with Shift, snare the first foe and haul them in.</li>
 *   <li>Level 1, Crashing Wave: raise a wave and roll it forward, carrying what it catches and putting out fires.</li>
 *   <li>Level 2, Octopus Form: wrap yourself in water with eight arms that lash and swallow shots; press again to throw
 *   every arm out at once, Shift to let it fall.</li>
 *   <li>Level 3, Wave Rider: surf a cresting wave over land and water; press again to leap off the crest.</li>
 *   <li>Level 5, Maelstrom: open a whirlpool where you aim and raise it as a waterspout you steer with your look; Shift
 *   brings it down early as a Deluge.</li>
 *   <li>Always: carry water (filled by water, rain and nearby water; dried by the Nether); water heals you, lets you
 *   breathe and swim, and strengthens every move. An empty-handed left click fights as a waterbender.</li>
 * </ul>
 */
public final class HydrokinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("hydro_water_whip", 1)
                    .cost(COMMON.hydroWhipEnergyCost::get, COMMON.hydroWhipCooldownTicks::get)
                    .canUse(c -> HydroService.canStartCast(c.player()))
                    .activate(HydroService::whip).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("hydro_crashing_wave", 1)
                    .cost(COMMON.hydroWaveEnergyCost::get, COMMON.hydroWaveCooldownTicks::get)
                    .canUse(c -> HydroService.canStartCast(c.player()))
                    .activate(HydroService::crashingWave).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("hydro_octopus_form", 2)
                    .cost(COMMON.hydroOctopusEnergyCost::get, COMMON.hydroOctopusCooldownTicks::get)
                    .canUse(c -> HydroService.canStartCast(c.player()))
                    .followUp(HydroService::octopusBurst)
                    .deactivate(c -> HydroService.endOctopus(c.player()))
                    .activate(HydroService::startOctopus).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("hydro_wave_rider", 3)
                    .cost(COMMON.hydroRiderEnergyCost::get, COMMON.hydroRiderCooldownTicks::get)
                    .canUse(c -> HydroService.canStartCast(c.player()) && !c.player().isPassenger())
                    .followUp(HydroService::leap)
                    .deactivate(c -> HydroService.endRide(c.player()))
                    .activate(HydroService::startRide).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("hydro_maelstrom", 5)
                    .cost(COMMON.hydroMaelstromEnergyCost::get, COMMON.hydroMaelstromCooldownTicks::get)
                    .canUse(c -> HydroService.canStartCast(c.player()))
                    .deactivate(c -> HydroService.endMaelstrom(c.player()))
                    .activate(HydroService::startMaelstrom).build());

    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { HydroService.forget(c.player()); }
}
