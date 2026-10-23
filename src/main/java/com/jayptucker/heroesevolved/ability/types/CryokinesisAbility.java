package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.cryo.CryoService;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Cryokinesis, inspired by Tracy Strauss.
 *
 * <ul>
 *   <li>Level 1, Frost Bolt: a lance of cold that chills, and freezes water.</li>
 *   <li>Level 2, Flash Freeze: touch a creature to freeze it solid.</li>
 *   <li>Level 3, Ice Wall: a wall of packed ice rises in front of you.</li>
 *   <li>Level 4, Glacial Spikes: a line of ice spikes bursts from the ground.</li>
 *   <li>Level 5, Blizzard: a dense whiteout follows you; sustained cold
 *   freezes creatures solid. Shift ends it early.</li>
 *   <li>Always: anything frozen solid shatters when hit, for heavy damage.
 *   Your punches chill. Water freezes under your feet (sneak to stop it).
 *   You cannot freeze, but fire hurts you more.</li>
 * </ul>
 * All ice and snow is temporary and melts back to what was there before.
 */
public final class CryokinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("cryo_frost_bolt", 1)
                    .cost(COMMON.cryoBoltEnergyCost::get, COMMON.cryoBoltCooldownTicks::get)
                    .activate(context -> {
                        CryoService.frostBolt(context.player(), context.abilityLevel());
                        return true;
                    })
                    .build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("cryo_flash_freeze", 2)
                    .cost(COMMON.cryoFreezeEnergyCost::get, COMMON.cryoFreezeCooldownTicks::get)
                    .activate(context -> CryoService.flashFreeze(
                            context.player(), context.abilityLevel()))
                    .build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("cryo_ice_wall", 3)
                    .cost(COMMON.cryoWallEnergyCost::get, COMMON.cryoWallCooldownTicks::get)
                    .activate(context -> CryoService.iceWall(
                            context.player(), context.abilityLevel()))
                    .build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("cryo_glacial_spikes", 4)
                    .cost(COMMON.cryoSpikesEnergyCost::get, COMMON.cryoSpikesCooldownTicks::get)
                    .activate(context -> {
                        CryoService.glacialSpikes(context.player(), context.abilityLevel());
                        return true;
                    })
                    .build(),
            AbilitySlot.QUINARY, SimpleAction.builder("cryo_blizzard", 5)
                    .cost(COMMON.cryoBlizzardEnergyCost::get, COMMON.cryoBlizzardCooldownTicks::get)
                    .canUse(context -> !CryoService.hasBlizzard(context.player()))
                    .activate(context -> {
                        CryoService.blizzard(context.player(), context.abilityLevel());
                        return true;
                    })
                    .deactivate(context -> {
                        if (!CryoService.hasBlizzard(context.player())) {
                            return false;
                        }
                        CryoService.endBlizzard(context.player());
                        return true;
                    })
                    .build()
    );

    @Override
    public AbilityDefinition definition() {
        return DEFINITION;
    }

    @Override
    public Map<AbilitySlot, AbilityAction> actions() {
        return ACTIONS;
    }

    @Override
    public boolean canUse(AbilityUseContext context) {
        return false;
    }

    @Override
    public AbilityActivationResult activate(AbilityUseContext context) {
        return AbilityActivationResult.REJECTED;
    }

    @Override
    public void tick(AbilityUseContext context) {
        CryoService.rimeWalk(context.player());
    }

    @Override
    public void onRevoked(AbilityUseContext context) {
        CryoService.stopAll(context.player());
    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(
                context.player(), context.abilityId());
    }

    @Override
    public void onOverexerted(AbilityUseContext context) {
        CryoService.hypothermia(context.player());
    }
}
