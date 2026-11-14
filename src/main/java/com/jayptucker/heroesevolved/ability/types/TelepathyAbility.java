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
 *   <li>Level 1, Read Mind: hold to hear thoughts and establish a Mind Thread.</li>
 *   <li>Level 2, Suggestion: a hostile mob forgets you; a peaceful one
 *   follows you.</li>
 *   <li>Level 3, Mind Link: one willing player or a party; linked minds hear each other's voices
 *   from anywhere, see each other through walls and feel it when one is hurt. Shift + Mind Link
 *   turns the inner voice on: only linked minds hear you.</li>
 *   <li>Level 4, Puppet: possess a deeply read body; players fight back through a mental contest.</li>
 *   <li>Level 5, Nightmare: connected minds perceive private apparitions and flee in terror.</li>
 *   <li>Passive: hear hostile thoughts when something unseen hunts you.
 *   Overexertion: too many voices at once.</li>
 * </ul>
 */
public final class TelepathyAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, new AbilityAction() {
                private final com.jayptucker.heroesevolved.ability.AbilityActionDefinition definition = new com.jayptucker.heroesevolved.ability.AbilityActionDefinition(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "telepathy_read_mind"), "action.heroesevolved.telepathy_read_mind", 1, 0, 0);
                public com.jayptucker.heroesevolved.ability.AbilityActionDefinition definition() { return definition; }
                public int energyCost(int level) { return COMMON.telepathyReadEnergyCost.get(); }
                public int cooldownTicks(int level) { return 20; }
                public boolean canUse(AbilityUseContext c) { return true; }
                // Shift + Read Mind at tier 4, on a player read all the way: Mind's Eye. Free (it costs Strain).
                public boolean followUp(AbilityUseContext c) { return c.modifierHeld() && com.jayptucker.heroesevolved.telepathy.TelepathySkills.mindsEye(c.player(), c.abilityLevel()); }
                public AbilityActivationResult activate(AbilityUseContext c) { return com.jayptucker.heroesevolved.telepathy.TelepathyFocus.start(c.player(),c.abilityLevel()) ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED; }
                public void release(AbilityUseContext c) { com.jayptucker.heroesevolved.telepathy.TelepathyFocus.stop(c.player()); }
                public boolean deactivate(AbilityUseContext c) { if(!com.jayptucker.heroesevolved.telepathy.TelepathyFocus.reading(c.player()))return false; release(c); return true; }
            },
            AbilitySlot.SECONDARY, SimpleAction.builder("telepathy_suggestion", 2)
                    .cost(COMMON.telepathySuggestionEnergyCost::get,
                            COMMON.telepathySuggestionCooldownTicks::get)
                    .activate(context -> TelepathyService.suggestion(
                            context.player(), context.abilityLevel()))
                    .build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("telepathy_mind_link", 3)
                    .cost(COMMON.telepathyLinkEnergyCost::get,
                            () -> 20)
                    .canUse(context -> true)
                    .activate(context -> TelepathyService.mindLink(
                            context.player(), context.abilityLevel()))
                    // Shift + Mind Link, once there is a mind to speak into: the inner voice, on or off, for free.
                    .followUp(context -> context.modifierHeld()
                            && com.jayptucker.heroesevolved.telepathy.TelepathySkills.toggleInnerVoice(context.player()))
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
        com.jayptucker.heroesevolved.telepathy.TelepathySkills.leave(context.player());
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
