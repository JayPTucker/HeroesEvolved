package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActionDefinition;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.combat.PlayerCombatData;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.regeneration.RegenerationService;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Rapid cellular regeneration, inspired by Claire Bennet.
 *
 * <ul>
 *   <li>Passive: heals over time, more at higher levels. From Level 2 it
 *   also purges poison and other toxins.</li>
 *   <li>Level 1, Rapid Mending: an instant burst of healing.</li>
 *   <li>Level 2, Blood Gift: heal the creature in your sights with your
 *   own blood (it burns the undead).</li>
 *   <li>Level 3, Indestructible: no fall damage or knockback, and dulled
 *   pain, for a short time.</li>
 *   <li>Level 5, Resurrection: passive; return from death once per
 *   cooldown.</li>
 * </ul>
 */
public final class RegenerationAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(
        0,
        0,
        5,
        AbilityActivationType.PASSIVE
    );

    /** Level 4: everyone nearby heals with you; the undead burn. */
    private static final AbilityAction HEALING_AURA =
        com.jayptucker.heroesevolved.ability.SimpleAction
            .builder("regeneration_healing_aura", 4)
            .cost(HeroesEvolvedConfig.COMMON.regenerationAuraEnergyCost::get,
                HeroesEvolvedConfig.COMMON.regenerationAuraCooldownTicks::get)
            .canUse(context -> !RegenerationService.hasHealingAura(context.player()))
            .activate(context -> {
                RegenerationService.healingAura(context.player(),
                    context.abilityLevel());
                return true;
            })
            .build();

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
        AbilitySlot.PRIMARY, new RapidMendingAction(),
        AbilitySlot.SECONDARY, new BloodGiftAction(),
        AbilitySlot.TERTIARY, new IndestructibleAction(),
        AbilitySlot.QUATERNARY, HEALING_AURA,
        AbilitySlot.QUINARY, new ResurrectionAction()
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
        if (context.player().getHealth() >= context.player().getMaxHealth()) {
            return false;
        }

        PlayerCombatData combatData =
            context.player().getData(ModDataAttachments.PLAYER_COMBAT.get());

        if (combatData.wasDamagedWithin(
            context.gameTime(),
            HeroesEvolvedConfig.COMMON.regenerationDamageDelayTicks.get()
        )) {
            return false;
        }

        return PlayerEnergyService.getEnergy(context.player())
            >= HeroesEvolvedConfig.COMMON.regenerationEnergyCost.get();
    }

    @Override
    public AbilityActivationResult activate(AbilityUseContext context) {
        return AbilityActivationResult.REJECTED;

    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(context.player(), context.abilityId());
    }

    @Override
    public void onRevoked(AbilityUseContext context) {
        RegenerationService.forget(context.player());
    }

    @Override
    public void tick(AbilityUseContext context) {
        int intervalTicks = HeroesEvolvedConfig.COMMON.regenerationIntervalTicks.get();

        if (context.gameTime() % intervalTicks != 0) {
            return;
        }

        // Toxins are flushed even mid-fight; only healing waits for a lull.
        RegenerationService.purgeToxinsPassively(
            context.player(), context.abilityLevel()
        );

        // A forced blood draw leaves the body drained: it cannot regrow for a while.
        if (!canUse(context) || context.player().hasEffect(com.jayptucker.heroesevolved.registry.ModItems.DRAINED)) {
            return;
        }

        int energyCost = HeroesEvolvedConfig.COMMON.regenerationEnergyCost.get();

        if (!PlayerEnergyService.tryConsume(context.player(), energyCost)) {
            return;
        }

        context.player().heal(
            RegenerationService.passiveHealAmount(context.abilityLevel())
        );
        spawnHealingParticles(context.player());
        MasteryService.awardEnergySpent(context.player(), energyCost);
    }

    private static void spawnHealingParticles(ServerPlayer player) {
        AbilityEffects.at(player,
                Kind.REGENERATION, 0.8D);
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

    private static final class RapidMendingAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                action("regeneration_rapid_mending", 1);

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.regenerationMendingEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.regenerationMendingCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return true;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            RegenerationService.rapidMending(context.player(), context.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }
    }

    private static final class BloodGiftAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                action("regeneration_blood_gift", 2);

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.regenerationBloodGiftEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.regenerationBloodGiftCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return true;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            // No target means no cost and no cooldown.
            return RegenerationService.bloodGift(
                    context.player(), context.abilityLevel())
                    ? AbilityActivationResult.SUCCESS
                    : AbilityActivationResult.REJECTED;
        }
    }

    private static final class IndestructibleAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                action("regeneration_indestructible", 3);

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.regenerationIndestructibleEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.regenerationIndestructibleCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !RegenerationService.isIndestructible(context.player());
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            RegenerationService.indestructible(context.player(), context.abilityLevel());
            return AbilityActivationResult.SUCCESS;
        }
    }

    /**
     * Resurrection is passive. Its slot exists so the HUD can show whether
     * it is ready; pressing the key only reports its state.
     */
    private static final class ResurrectionAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        RegenerationService.RESURRECTION_ID,
                        "action.heroesevolved.regeneration_resurrection",
                        5,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        /** Shown in the skills menu as the time between resurrections. */
        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON
                    .regenerationResurrectionCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            ServerPlayer player = context.player();
            long remaining = CooldownService.getRemainingTicks(
                    player, RegenerationService.RESURRECTION_ID
            );
            player.displayClientMessage(Component.literal(remaining > 0
                    ? "Your body is still recovering from death ("
                            + (remaining / 20 + 1) + "s)."
                    : "Resurrection is ready. Death will not hold you."), true);
            return false;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            return AbilityActivationResult.REJECTED;
        }
    }
}
