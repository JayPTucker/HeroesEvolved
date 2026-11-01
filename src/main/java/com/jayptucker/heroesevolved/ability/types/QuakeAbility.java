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
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.quake.QuakeKit;
import com.jayptucker.heroesevolved.quake.QuakeService;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Quake, after Daisy Johnson (Agents of S.H.I.E.L.D.): vibration, and what it costs her body.
 *
 * <ul>
 *   <li>Level 1, Shockwave: a palm blast that hurls targets, turns arrows back and shatters glass. In
 *   midair (or aimed down) it launches her the other way.</li>
 *   <li>Level 2, Resonate: shake one enemy apart from the inside for three seconds: it staggers and
 *   can't aim, its armour rattles apart and its bones ache.</li>
 *   <li>Level 3, Fault Line: punch the ground and a crack races out ahead of her, erupting under every
 *   enemy on its way. Aimed straight down or from the air, it is the Seismic Slam ring.</li>
 *   <li>Level 4, Quake Blast: hold to pour a two-handed beam. Jump and it carries her: Daisy's flight.</li>
 *   <li>Level 5, Earthquake: a sustained quake ending in an aftershock.</li>
 *   <li>Always: Strain builds with every quake and makes her hit harder, until at 100 the bones in her
 *   arms crack. Crouch still to feel heartbeats through walls. Vibration cushions her landings.</li>
 * </ul>
 */
public final class QuakeAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, new ShockwaveAction(),
            AbilitySlot.SECONDARY, com.jayptucker.heroesevolved.ability.SimpleAction.builder("quake_resonate", 2).cost(() -> 18, () -> 160)
                    .canUse(c -> !QuakeKit.fractured(c.player()))
                    .activate(c -> QuakeKit.resonate(c.player(), c.abilityLevel())).build(),
            AbilitySlot.TERTIARY, com.jayptucker.heroesevolved.ability.SimpleAction.builder("quake_fault_line", 3)
                    .cost(() -> HeroesEvolvedConfig.COMMON.quakeSlamEnergyCost.get(), () -> HeroesEvolvedConfig.COMMON.quakeSlamCooldownTicks.get())
                    .canUse(c -> !c.player().isPassenger() && !QuakeKit.fractured(c.player()))
                    .activate(c -> QuakeKit.faultLine(c.player(), c.abilityLevel())).build(),
            AbilitySlot.QUATERNARY, new QuakeBlastAction(),
            AbilitySlot.QUINARY, new EarthquakeAction()
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
        QuakeService.stopAll(context.player());
        QuakeKit.forget(context.player());
    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }

    @Override
    public void onOverexerted(AbilityUseContext context) {
        QuakeService.fractureArms(context.player());
    }

    /**
     * Held: pressing starts the beam, letting go ends it. The beam drains
     * energy while held, so its own activation cost is small, and its
     * cooldown begins only when it stops.
     */
    private static final class QuakeBlastAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        QuakeService.QUAKE_BLAST_ID,
                        "action.heroesevolved.quake_blast",
                        4,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeBlastEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            // Shown in the skills menu; applied when the beam ends.
            return HeroesEvolvedConfig.COMMON.quakeBlastCooldownTicks.get();
        }

        @Override
        public int cooldownTicks(AbilityUseContext context) {
            // Not started on activation: QuakeService starts it on release.
            return 0;
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger()
                    && !QuakeService.isBlasting(context.player())
                    && !QuakeKit.fractured(context.player());
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            QuakeService.startQuakeBlast(context.player(), context.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }

        @Override
        public void release(AbilityUseContext context) {
            QuakeService.stopQuakeBlast(context.player(), false);
        }

        @Override
        public boolean deactivate(AbilityUseContext context) {
            if (!QuakeService.isBlasting(context.player())) {
                return false;
            }
            QuakeService.stopQuakeBlast(context.player(), false);
            return true;
        }
    }

    private static AbilityActionDefinition action(String path, int unlockLevel) {
        return new AbilityActionDefinition(
                ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path),
                "action." + HeroesEvolved.MOD_ID + "." + path,
                unlockLevel,
                0,
                0
        );
    }

    private static final class ShockwaveAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                action("quake_shockwave", 1);

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeShockwaveEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeShockwaveCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger() && !QuakeKit.fractured(context.player());
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            QuakeService.shockwave(context.player(), context.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }
    }

    private static final class EarthquakeAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                action("quake_earthquake", 5);

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeEarthquakeEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeEarthquakeCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !QuakeService.hasActiveEarthquake(context.player()) && !QuakeKit.fractured(context.player());
        }

        @Override
        public boolean deactivate(AbilityUseContext context) {
            if (!QuakeService.hasActiveEarthquake(context.player())) {
                return false;
            }
            QuakeService.stopEarthquake(context.player());
            return true;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            QuakeService.startEarthquake(context.player(), context.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }
    }
}
