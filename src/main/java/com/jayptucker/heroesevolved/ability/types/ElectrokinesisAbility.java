package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.electro.ElectroService;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Electrokinesis, inspired by Elle Bishop.
 *
 * <ul>
 *   <li>Level 1, Chain Lightning: tap for a forked bolt that leaps between foes; hold for Live
 *   Current, a continuous stream that locks the target's muscles.</li>
 *   <li>Level 2, Ball Lightning: a homing orb that lashes what it passes; Shift + key bursts it.
 *   Static Touch also unlocks: melee hits shock and stun.</li>
 *   <li>Level 3, Thunderstrike: mark a spot and lightning strikes it a moment later.</li>
 *   <li>Level 4, Ride the Lightning: dash as a bolt, leaving a live scar on the ground.</li>
 *   <li>Level 5, Storm Surge: gather the storm, then release it at everything around you.</li>
 *   <li>Always: Charge builds as you fight and sprint; at full Charge the next power is
 *   Overcharged. Lightning recharges you, storms feed you, and water turns your Charge on you.</li>
 * </ul>
 */
public final class ElectrokinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, new AbilityAction() {
                private final AbilityActionDefinition definition = new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath("heroesevolved", "electro_chain_lightning"),
                        "action.heroesevolved.electro_chain_lightning", 1, 0, 0);
                @Override public AbilityActionDefinition definition() { return definition; }
                @Override public int energyCost(int level) { return COMMON.electroChainEnergyCost.get(); }
                @Override public int cooldownTicks(int level) { return COMMON.electroChainCooldownTicks.get(); }
                @Override public boolean canUse(AbilityUseContext c) { return !ElectroService.isHolding(c.player()); }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    // The key is down: a quick release fires Chain Lightning, holding becomes Live Current.
                    ElectroService.press(c.player(), c.abilityLevel());
                    return AbilityActivationResult.SUCCESS;
                }
                @Override public void release(AbilityUseContext c) { ElectroService.release(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) { return ElectroService.stopStream(c.player()); }
            },
            AbilitySlot.SECONDARY, SimpleAction.builder("electro_ball_lightning", 2)
                    .cost(COMMON.electroBallEnergyCost::get, COMMON.electroBallCooldownTicks::get)
                    .activate(c -> { ElectroService.ballLightning(c.player(), c.abilityLevel()); return true; })
                    .deactivate(c -> ElectroService.detonate(c.player()))
                    .build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("electro_thunderstrike", 3)
                    .cost(COMMON.electroThunderstrikeEnergyCost::get, COMMON.electroThunderstrikeCooldownTicks::get)
                    .activate(c -> ElectroService.thunderstrike(c.player(), c.abilityLevel()))
                    .build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("electro_ride_the_lightning", 4)
                    .cost(COMMON.electroRideEnergyCost::get, COMMON.electroRideCooldownTicks::get)
                    .canUse(c -> !c.player().isPassenger())
                    .activate(c -> { ElectroService.rideTheLightning(c.player(), c.abilityLevel()); return true; })
                    .build(),
            AbilitySlot.QUINARY, SimpleAction.builder("electro_storm_surge", 5)
                    .cost(COMMON.electroStormEnergyCost::get, COMMON.electroStormCooldownTicks::get)
                    .canUse(c -> !ElectroService.isSurging(c.player()))
                    .activate(c -> ElectroService.startSurge(c.player(), c.abilityLevel()))
                    .deactivate(c -> ElectroService.cancelSurge(c.player()))
                    .build()
    );

    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }
    @Override public void onRevoked(AbilityUseContext context) { ElectroService.forget(context.player()); }
    @Override public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }
    @Override public void onOverexerted(AbilityUseContext context) { ElectroService.uncontrolledDischarge(context.player()); }
}
