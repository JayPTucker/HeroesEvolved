package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.cryo.CryoKitService;
import com.jayptucker.heroesevolved.cryo.CryoRideService;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/** A connected ice-sculpting, glacier-riding and deliberate-shatter kit. Saved action IDs remain stable. */
public final class CryokinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0,0,5,AbilityActivationType.INSTANT);
    private static final Map<AbilitySlot,AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, new AbilityAction() {
                private final AbilityActionDefinition definition = new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath("heroesevolved","cryo_frost_bolt"),"action.heroesevolved.cryo_frost_bolt",1,0,0);
                @Override public AbilityActionDefinition definition(){return definition;}
                @Override public int energyCost(int level){return COMMON.cryoBoltEnergyCost.get();}
                @Override public int cooldownTicks(int level){return COMMON.cryoBoltCooldownTicks.get();}
                @Override public boolean canUse(AbilityUseContext c){return !CryoKitService.isStreaming(c.player());}
                @Override public AbilityActivationResult activate(AbilityUseContext c){return CryoKitService.startStream(c.player(),c.abilityLevel())?AbilityActivationResult.SUCCESS:AbilityActivationResult.REJECTED;}
                @Override public void release(AbilityUseContext c){CryoKitService.stopStream(c.player());}
                @Override public boolean deactivate(AbilityUseContext c){return CryoKitService.stopStream(c.player());}
            },
            AbilitySlot.SECONDARY, SimpleAction.builder("cryo_flash_freeze",2)
                    .cost(COMMON.cryoWallEnergyCost::get,COMMON.cryoWallCooldownTicks::get)
                    .activate(c->CryoKitService.sculpt(c.player(),c.abilityLevel(),c.modifierHeld()))
                    .followUp(c->CryoKitService.reshape(c.player(),c.abilityLevel(),c.modifierHeld())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("cryo_ice_wall",3)
                    .cost(()->25,()->100).canUse(c->CryoRideService.canStart(c.player()))
                    .activate(c->CryoRideService.toggle(c.player(),c.abilityLevel()))
                    .deactivateOnPress(c->true).deactivate(c->{if(!CryoRideService.isRiding(c.player()))return false;CryoRideService.stop(c.player(),true);return true;}).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("cryo_glacial_spikes",4)
                    .cost(COMMON.cryoFreezeEnergyCost::get,COMMON.cryoFreezeCooldownTicks::get)
                    .activate(c->CryoKitService.prison(c.player(),c.abilityLevel()))
                    .deactivateOnPress(c->CryoKitService.hasPrison(c.player()))
                    .deactivate(c->CryoKitService.shatterPrison(c.player())).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("cryo_blizzard",5)
                    .cost(COMMON.cryoBlizzardEnergyCost::get,COMMON.cryoBlizzardCooldownTicks::get)
                    .canUse(c->!CryoKitService.hasWhiteout(c.player()))
                    .activate(c->CryoKitService.whiteout(c.player(),c.abilityLevel()))
                    .deactivate(c->CryoKitService.endWhiteout(c.player(),true)).build());
    @Override public AbilityDefinition definition(){return DEFINITION;}
    @Override public Map<AbilitySlot,AbilityAction> actions(){return ACTIONS;}
    @Override public boolean canUse(AbilityUseContext context){return false;}
    @Override public AbilityActivationResult activate(AbilityUseContext context){return AbilityActivationResult.REJECTED;}
    @Override public void tick(AbilityUseContext context){CryoService.rimeWalk(context.player());}
    @Override public void onRevoked(AbilityUseContext context){CryoService.stopAll(context.player());}
    @Override public void onAwaken(AbilityUseContext context){com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(),context.abilityId());}
    @Override public void onOverexerted(AbilityUseContext context){CryoService.hypothermia(context.player());}
}
