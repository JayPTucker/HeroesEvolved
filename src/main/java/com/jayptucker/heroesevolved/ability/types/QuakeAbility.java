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
import com.jayptucker.heroesevolved.quake.QuakeService;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Vibration manipulation, inspired by Daisy "Quake" Johnson.
 *
 * <ul>
 *   <li>Level 1, Shockwave: a palm blast that hurls targets, reflects
 *   projectiles and shatters glass. Aimed at nearby ground, it launches
 *   Quake into the air.</li>
 *   <li>Level 2, Seismic Slam: a ground punch whose ring of force throws
 *   everything outward. Used in midair, Quake plunges first.</li>
 *   <li>Level 3, Tremor Sense: feel heartbeats; nearby creatures glow
 *   through walls for Quake alone.</li>
 *   <li>Level 4, Quake Blast: hold the key to pour a two-handed beam of
 *   vibration, draining energy every second. Aimed below, it hovers.</li>
 *   <li>Level 5, Earthquake: a sustained quake ending in an aftershock.
 *   Shift ends it early.</li>
 *   <li>Passive: vibration-cushioned landings. Overexertion fractures
 *   Quake's arms.</li>
 * </ul>
 */
public final class QuakeAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, new ShockwaveAction(),
            AbilitySlot.SECONDARY, new SeismicSlamAction(),
            AbilitySlot.TERTIARY, new TremorSenseAction(),
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
                    && !QuakeService.isBlasting(context.player());
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
            return !context.player().isPassenger();
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            QuakeService.shockwave(context.player(), context.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }
    }

    private static final class SeismicSlamAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                action("quake_seismic_slam", 2);

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeSlamEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeSlamCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger();
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            QuakeService.slam(context.player(), context.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }
    }

    private static final class TremorSenseAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                action("quake_tremor_sense", 3);

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeTremorSenseEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.quakeTremorSenseCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !QuakeService.hasTremorSense(context.player());
        }

        @Override
        public boolean deactivate(AbilityUseContext context) {
            if (!QuakeService.hasTremorSense(context.player())) {
                return false;
            }
            QuakeService.stopTremorSense(context.player());
            return true;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            QuakeService.startTremorSense(context.player(), context.abilityLevel());
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
            return !QuakeService.hasActiveEarthquake(context.player());
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
