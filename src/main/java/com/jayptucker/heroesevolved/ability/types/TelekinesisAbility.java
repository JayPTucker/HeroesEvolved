package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActionDefinition;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.telekinesis.TelekinesisService;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Telekinesis, inspired by Sylar.
 *
 * <ul>
 *   <li>Level 1, Telekinetic Push: a cone of force; things hit walls hard.</li>
 *   <li>Level 2, Grip: hold a creature in the air, choking. Press again
 *   to hurl it.</li>
 *   <li>Level 3, Bullet Stop: freeze incoming projectiles, then fling
 *   them where you look.</li>
 *   <li>Level 4, Disarm: tear the weapon from a mob's hands (players are
 *   yanked toward you instead) and draw in loose items nearby.</li>
 *   <li>Level 5, Telekinetic Storm: lift everyone around you, then slam
 *   them down.</li>
 *   <li>Passive: loose items drift toward you. Overexertion brings a
 *   blinding migraine.</li>
 * </ul>
 */
public final class TelekinesisAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("telekinesis_push", 1)
                    .cost(COMMON.telekinesisPushEnergyCost::get,
                            COMMON.telekinesisPushCooldownTicks::get)
                    .activate(context -> {
                        TelekinesisService.push(context.player(),
                                context.abilityLevel());
                        return true;
                    })
                    .build(),
            AbilitySlot.SECONDARY, new GripAction(),
            AbilitySlot.TERTIARY, SimpleAction.builder("telekinesis_bullet_stop", 3)
                    .cost(COMMON.telekinesisBulletStopEnergyCost::get,
                            COMMON.telekinesisBulletStopCooldownTicks::get)
                    .canUse(context -> !TelekinesisService.hasBulletStop(context.player()))
                    .activate(context -> {
                        TelekinesisService.bulletStop(context.player(),
                                context.abilityLevel());
                        return true;
                    })
                    .deactivate(context -> {
                        if (!TelekinesisService.hasBulletStop(context.player())) {
                            return false;
                        }
                        TelekinesisService.releaseBulletStop(context.player());
                        return true;
                    })
                    .build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("telekinesis_disarm", 4)
                    .cost(COMMON.telekinesisDisarmEnergyCost::get,
                            COMMON.telekinesisDisarmCooldownTicks::get)
                    .activate(context -> {
                        TelekinesisService.disarm(context.player(),
                                context.abilityLevel());
                        return true;
                    })
                    .build(),
            AbilitySlot.QUINARY, SimpleAction.builder("telekinesis_storm", 5)
                    .cost(COMMON.telekinesisStormEnergyCost::get,
                            COMMON.telekinesisStormCooldownTicks::get)
                    .canUse(context -> !TelekinesisService.hasActiveLift(context.player()))
                    .activate(context -> {
                        TelekinesisService.telekineticStorm(context.player(),
                                context.abilityLevel());
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
        if (context.gameTime() % 5 == 0) {
            TelekinesisService.drawNearbyItems(context.player());
        }
    }

    @Override
    public void onRevoked(AbilityUseContext context) {
        TelekinesisService.stopAll(context.player());
    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }

    @Override
    public void onOverexerted(AbilityUseContext context) {
        TelekinesisService.migraine(context.player());
    }

    /**
     * First press seizes, second press throws. Disarm rides along: gripping
     * a mob that holds a weapon tears it away the moment you seize it.
     */
    private static final class GripAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID, "telekinesis_grip"),
                        "action.heroesevolved.telekinesis_grip",
                        2,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(AbilityUseContext context) {
            int cost = COMMON.telekinesisGripEnergyCost.get();
            return TelekinesisService.isGripping(context.player()) ? cost / 3 : cost;
        }

        @Override
        public int cooldownTicks(AbilityUseContext context) {
            // Read after activation: a fresh grip only blocks a double-press,
            // a throw starts the full cooldown.
            return TelekinesisService.isGripping(context.player())
                    ? 10
                    : COMMON.telekinesisGripCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return TelekinesisService.isGripping(context.player())
                    || TelekinesisService.findGripTarget(context.player()) != null;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            if (TelekinesisService.isGripping(context.player())) {
                return TelekinesisService.throwGripped(context.player())
                        ? AbilityActivationResult.SUCCESS
                        : AbilityActivationResult.REJECTED;
            }

            return TelekinesisService.grip(context.player(), context.abilityLevel())
                    ? AbilityActivationResult.SUCCESS
                    : AbilityActivationResult.REJECTED;
        }
    }
}
