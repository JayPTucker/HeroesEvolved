package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.telepathy.TelepathyService;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Telepathy, inspired by Matt Parkman.
 *
 * <ul>
 *   <li>Level 1, Read Mind: hear a creature's thoughts; what it hunts glows.</li>
 *   <li>Level 2, Suggestion: a hostile mob forgets you; a peaceful one
 *   follows you.</li>
 *   <li>Level 3, Mind Link: linked players see each other through walls
 *   and feel it when one is hurt.</li>
 *   <li>Level 4, Puppet: turn a hostile mob on its own kind. Shift ends it.</li>
 *   <li>Level 5, Nightmare: trap a mind in terror; it flees and suffers.</li>
 *   <li>Passive: hear hostile thoughts when something unseen hunts you.
 *   Overexertion: too many voices at once.</li>
 * </ul>
 */
public final class TelepathyAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("telepathy_read_mind", 1)
                    .cost(COMMON.telepathyReadEnergyCost::get,
                            COMMON.telepathyReadCooldownTicks::get)
                    .activate(context -> TelepathyService.readMind(
                            context.player(), context.abilityLevel()))
                    .build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("telepathy_suggestion", 2)
                    .cost(COMMON.telepathySuggestionEnergyCost::get,
                            COMMON.telepathySuggestionCooldownTicks::get)
                    .activate(context -> TelepathyService.suggestion(
                            context.player(), context.abilityLevel()))
                    .build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("telepathy_mind_link", 3)
                    .cost(COMMON.telepathyLinkEnergyCost::get,
                            COMMON.telepathyLinkCooldownTicks::get)
                    .canUse(context -> !TelepathyService.hasLink(context.player()))
                    .activate(context -> TelepathyService.mindLink(
                            context.player(), context.abilityLevel()))
                    .build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("telepathy_puppet", 4)
                    .cost(COMMON.telepathyPuppetEnergyCost::get,
                            COMMON.telepathyPuppetCooldownTicks::get)
                    .canUse(context -> !TelepathyService.hasPuppet(context.player()))
                    .activate(context -> TelepathyService.puppet(
                            context.player(), context.abilityLevel()))
                    .deactivate(context -> {
                        if (!TelepathyService.hasPuppet(context.player())) {
                            return false;
                        }
                        TelepathyService.releasePuppet(context.player());
                        return true;
                    })
                    .build(),
            AbilitySlot.QUINARY, SimpleAction.builder("telepathy_nightmare", 5)
                    .cost(COMMON.telepathyNightmareEnergyCost::get,
                            COMMON.telepathyNightmareCooldownTicks::get)
                    .activate(context -> TelepathyService.nightmare(
                            context.player(), context.abilityLevel()))
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
    public void onRevoked(AbilityUseContext context) {
        TelepathyService.stopAll(context.player());
    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(
                context.player(), context.abilityId());
    }

    @Override
    public void onOverexerted(AbilityUseContext context) {
        TelepathyService.overload(context.player());
    }
}
