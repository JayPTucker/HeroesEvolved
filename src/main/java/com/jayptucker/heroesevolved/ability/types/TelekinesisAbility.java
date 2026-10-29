package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.telekinesis.TelekinesisService;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/** A physical control kit: gather, hold beside you, redirect, then use the collision. */
public final class TelekinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("telekinesis_push", 1)
                    .cost(COMMON.telekinesisPushEnergyCost::get, COMMON.telekinesisPushCooldownTicks::get)
                    .activate(c -> {
                        if (c.modifierHeld() && TelekinesisService.isGripping(c.player()))
                            return TelekinesisService.slamGripped(c.player());
                        TelekinesisService.push(c.player(), c.abilityLevel());
                        return true;
                    }).build(),
            AbilitySlot.SECONDARY, new GripAction(),
            AbilitySlot.TERTIARY, new BulletAction(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("telekinesis_disarm", 4)
                    .cost(COMMON.telekinesisDisarmEnergyCost::get, COMMON.telekinesisDisarmCooldownTicks::get)
                    .activate(c -> { TelekinesisService.disarm(c.player(), c.abilityLevel()); return true; }).build(),
            AbilitySlot.QUINARY, new StormAction()
    );

    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }
    @Override public void tick(AbilityUseContext context) {
        if (context.gameTime() % 5 == 0) TelekinesisService.drawNearbyItems(context.player());
    }
    @Override public void onRevoked(AbilityUseContext context) { TelekinesisService.stopAll(context.player()); }
    @Override public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }
    @Override public void onOverexerted(AbilityUseContext context) { TelekinesisService.migraine(context.player()); }

    private static AbilityActionDefinition definition(String id, int level) {
        return new AbilityActionDefinition(ResourceLocation.fromNamespaceAndPath("heroesevolved", id),
                "action.heroesevolved." + id, level, 0, 0);
    }

    /** H seizes, H again throws, Shift + H places or gently drops without another cost. */
    private static final class GripAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION = TelekinesisAbility.definition("telekinesis_grip", 2);
        @Override public AbilityActionDefinition definition() { return DEFINITION; }
        @Override public int energyCost(int level) { return COMMON.telekinesisGripEnergyCost.get(); }
        @Override public int cooldownTicks(int level) { return COMMON.telekinesisGripCooldownTicks.get(); }
        @Override public int energyCost(AbilityUseContext c) {
            return TelekinesisService.isGripping(c.player()) ? COMMON.telekinesisGripEnergyCost.get() / 3
                    : COMMON.telekinesisGripEnergyCost.get();
        }
        @Override public int cooldownTicks(AbilityUseContext c) {
            return TelekinesisService.isGripping(c.player()) ? 10 : COMMON.telekinesisGripCooldownTicks.get();
        }
        @Override public boolean canUse(AbilityUseContext c) {
            return TelekinesisService.isGripping(c.player()) || !c.player().isPassenger()
                    && !TelekinesisService.hasActiveLift(c.player()) && TelekinesisService.findGripTarget(c.player()) != null;
        }
        @Override public AbilityActivationResult activate(AbilityUseContext c) {
            boolean used = TelekinesisService.isGripping(c.player()) ? TelekinesisService.throwGripped(c.player())
                    : TelekinesisService.grip(c.player(), c.abilityLevel());
            return used ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED;
        }
        @Override public boolean deactivate(AbilityUseContext c) { return TelekinesisService.placeGripped(c.player()); }
    }

    /** The recovery begins when the guard ends, so a second press always releases the formation. */
    private static final class BulletAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION = TelekinesisAbility.definition("telekinesis_bullet_stop", 3);
        @Override public AbilityActionDefinition definition() { return DEFINITION; }
        @Override public int energyCost(int level) { return COMMON.telekinesisBulletStopEnergyCost.get(); }
        @Override public int cooldownTicks(int level) { return COMMON.telekinesisBulletStopCooldownTicks.get(); }
        @Override public int energyCost(AbilityUseContext c) {
            return TelekinesisService.hasBulletStop(c.player()) ? 0 : energyCost(c.abilityLevel());
        }
        @Override public int cooldownTicks(AbilityUseContext c) {
            return TelekinesisService.hasBulletStop(c.player()) ? 0 : cooldownTicks(c.abilityLevel());
        }
        @Override public boolean canUse(AbilityUseContext c) { return !c.player().isPassenger(); }
        @Override public AbilityActivationResult activate(AbilityUseContext c) {
            if (TelekinesisService.hasBulletStop(c.player())) TelekinesisService.releaseBulletStop(c.player());
            else TelekinesisService.bulletStop(c.player(), c.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }
        @Override public boolean deactivate(AbilityUseContext c) { return TelekinesisService.cancelBulletStop(c.player()); }
    }

    private static final class StormAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION = TelekinesisAbility.definition("telekinesis_storm", 5);
        @Override public AbilityActionDefinition definition() { return DEFINITION; }
        @Override public int energyCost(int level) { return COMMON.telekinesisStormEnergyCost.get(); }
        @Override public int cooldownTicks(int level) { return COMMON.telekinesisStormCooldownTicks.get(); }
        @Override public int energyCost(AbilityUseContext c) {
            return TelekinesisService.hasActiveLift(c.player()) ? 0 : energyCost(c.abilityLevel());
        }
        @Override public int cooldownTicks(AbilityUseContext c) {
            return TelekinesisService.hasActiveLift(c.player()) ? 0 : cooldownTicks(c.abilityLevel());
        }
        @Override public boolean canUse(AbilityUseContext c) { return !c.player().isPassenger(); }
        @Override public AbilityActivationResult activate(AbilityUseContext c) {
            if (TelekinesisService.hasActiveLift(c.player()))
                return TelekinesisService.slamStorm(c.player()) ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED;
            TelekinesisService.telekineticStorm(c.player(), c.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }
        @Override public boolean deactivate(AbilityUseContext c) { return TelekinesisService.cancelStorm(c.player()); }
    }
}
