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
 * Rapid cellular regeneration, inspired by Claire Bennet: the one who keeps getting back up.
 *
 * <ul>
 *   <li>Passive: damage you take becomes Regrowth, stored healing your body burns to close wounds
 *   fast. Falls hurt half as much. From Level 2 toxins are purged. Heavy blades and explosions take
 *   limbs, which grow back from the bone out. And you cannot be killed: a lethal blow puts you down
 *   on the ground (90 seconds at Level 1, 12 at Level 5) until your body repairs itself.</li>
 *   <li>Level 1, Rapid Mending: spend all Regrowth on an instant heal; overflow becomes Absorption.</li>
 *   <li>Level 2, Blood Gift: heal someone up to twelve blocks away with your blood (it burns the
 *   undead); the cut feeds Regrowth. Holding an empty syringe bottles it for the Formula.</li>
 *   <li>Level 3, Indestructible: no falls or knockback, dulled pain, reckless punches, and
 *   bullets pushed back out.</li>
 *   <li>Level 4, Blood Bond: friends nearby heal with you and you take a share of their wounds.</li>
 *   <li>Level 5, Second Life: your blood in a friend saves them from their next death.</li>
 * </ul>
 */
public final class RegenerationAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(
        0,
        0,
        5,
        AbilityActivationType.PASSIVE
    );

    /** Level 4, Blood Bond: friends nearby heal with you and you take a share of their wounds; the undead burn. */
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
        if (context.gameTime() % 10 == 0) RegenerationService.syncHud(context.player(), context.abilityLevel());
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

        // Stored Regrowth makes the wound close faster.
        context.player().heal(
            RegenerationService.passiveHeal(context.player(), context.abilityLevel())
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
     * The fifth key is Second Life: give a friend your blood so their next death does not take them.
     * Resurrection itself stays passive (the HUD shows its timer); with no friend in sight, the key
     * reports whether it is ready.
     */
    private static final class ResurrectionAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        RegenerationService.SECOND_LIFE_ID,
                        "action.heroesevolved.regeneration_second_life",
                        5,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return 40;
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return RegenerationService.SECOND_LIFE_COOLDOWN_TICKS;
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            ServerPlayer player = context.player();
            if (RegenerationService.findSecondLifeTarget(player) != null) return true;
            int down = com.jayptucker.heroesevolved.regeneration.BodyService.recoveryTicks(context.abilityLevel(),
                    RegenerationService.regrowth(player)) / 20;
            player.displayClientMessage(Component.literal("Death will not hold you: if you go down, you're up in "
                    + down + "s. Aim at a friend to give them a Second Life."), true);
            return false;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            return RegenerationService.secondLife(context.player())
                    ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED;
        }
    }
}
