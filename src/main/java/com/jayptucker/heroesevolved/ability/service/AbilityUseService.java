package com.jayptucker.heroesevolved.ability.service;

import com.jayptucker.heroesevolved.effects.AbilityEffects;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.energy.OverexertionService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;

import java.util.Map;
import java.util.Optional;

public final class AbilityUseService {
    private AbilityUseService() {
    }

    /** Forwards a key release to the assigned power's action in that slot. */
    public static void releaseAssignedAction(ServerPlayer player, AbilitySlot slot) {
        PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getValue().isUnlocked())
                .ifPresent(entry -> {
                    Ability power = AbilityRegistry.ABILITIES.get(entry.getKey());
                    if (power == null) {
                        return;
                    }
                    Optional.ofNullable(com.jayptucker.heroesevolved.skills.SkillTreeService.actions(player, entry.getKey()).get(slot)).ifPresent(action -> action.release(
                            new AbilityUseContext(
                                    player,
                                    entry.getKey(),
                                    PlayerProgressionService.getLevel(player),
                                    false
                            )
                    ));
                });
    }

    public static AbilityUseResult activateAssignedAction(
            ServerPlayer player,
            AbilitySlot slot,
            boolean allowOverexertion
    ) {
        if (EclipseService.arePowersSuppressed(player)) return AbilityUseResult.POWER_SUPPRESSED;
        if (!PowerRules.canAct(player)
                || com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(player)
                || com.jayptucker.heroesevolved.intro.PowerIntroService.isActive(player)
                || com.jayptucker.heroesevolved.time.intro.TimeIntroService.isVisiting(player)
                || com.jayptucker.heroesevolved.time.intro.TimeIntroService.isCity(player.level())) {
            PowerFeedback.explain(player, Component.literal(
                    com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(player)
                            ? "You're frozen solid and can't move a muscle."
                            : com.jayptucker.heroesevolved.intro.PowerIntroService.isActive(player)
                            ? "Your power is still awakening..."
                            : "You can't use your power right now."));
            return AbilityUseResult.CANNOT_USE;
        }
        // Eclipse suppression applies before a power can consume energy,
        // start a cooldown, or change any server-side state.
        if (EclipseService.arePowersSuppressed(player)) {
            return AbilityUseResult.POWER_SUPPRESSED;
        }

        Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignment =
                PlayerAbilityService.getData(player).assignedPower();

        if (assignment.isEmpty()) {
            return AbilityUseResult.NO_ASSIGNED_POWER;
        }

        ResourceLocation powerId = assignment.get().getKey();
        AbilityProgress progress = assignment.get().getValue();

        if (!progress.isUnlocked()) {
            return AbilityUseResult.NOT_UNLOCKED;
        }

        Ability power = AbilityRegistry.ABILITIES.get(powerId);

        if (power == null) {
            return AbilityUseResult.UNKNOWN_ABILITY;
        }

        Optional<AbilityAction> action = Optional.ofNullable(com.jayptucker.heroesevolved.skills.SkillTreeService.actions(player, powerId).get(slot));

        if (action.isEmpty()) {
            return AbilityUseResult.ACTION_NOT_ASSIGNED;
        }

        AbilityAction abilityAction = action.get();

        // Individual actions unlock at different levels of the same power.
        int powerLevel = PlayerProgressionService.getLevel(player);

        Component actionName = Component.translatable(abilityAction.definition().displayNameKey());

        if (!abilityAction.definition().isUnlockedAt(powerLevel)) {
            PowerFeedback.explain(player, actionName.copy().append(Component.literal(
                    " unlocks at Level " + abilityAction.definition().unlockLevel()
                            + " (you're Level " + powerLevel + ").")));
            return AbilityUseResult.ACTION_LOCKED;
        }

        AbilityUseContext context = new AbilityUseContext(
                player,
                powerId,
                powerLevel,
                allowOverexertion
        );

        // A held modifier has a second purpose for persistent abilities:
        // end the effect immediately. This branch is intentionally before
        // cooldown and energy checks, because ending an effect is always free.
        if (context.modifierHeld() && abilityAction.deactivate(context)) {
            PlayerPowerSyncService.sync(player);
            return AbilityUseResult.SUCCESS;
        }

        ResourceLocation cooldownId = abilityAction.cooldownId(context);

        if (CooldownService.isOnCooldown(player, cooldownId)) {
            long remaining = CooldownService.getRemainingTicks(player, cooldownId);
            PowerFeedback.explain(player, actionName.copy().append(Component.literal(
                    String.format(" is recovering: ready in %.1fs.", remaining / 20.0D))));
            return AbilityUseResult.ON_COOLDOWN;
        }

        if (!abilityAction.canUse(context)) {
            // Many actions explain their own refusal; this is the fallback.
            PowerFeedback.explain(player, actionName.copy().append(Component.literal(
                    " can't be used right now.")));
            return AbilityUseResult.CANNOT_USE;
        }

        int energyCost = com.jayptucker.heroesevolved.skills.SkillTreeService.activationCost(player, powerId, com.jayptucker.heroesevolved.ability.PowerBalance.energy(abilityAction.definition().id(), abilityAction.energyCost(context)));

        boolean requiresOverexertion =
                PlayerEnergyService.getEnergy(player) < energyCost;

        // Holding Shift when pressing a slot is the player's explicit consent
        // to use overexertion if they lack the required energy.
        if (requiresOverexertion && !allowOverexertion) {
            PowerFeedback.explain(player, actionName.copy().append(Component.literal(
                    " needs " + energyCost + " stamina (you have "
                            + PlayerEnergyService.getEnergy(player)
                            + "). Hold Shift to overexert.")));
            return AbilityUseResult.INSUFFICIENT_ENERGY;
        }

        if (abilityAction.activate(context)
                != AbilityActivationResult.SUCCESS) {
            return AbilityUseResult.ACTIVATION_REJECTED;
        }

        AbilityEffects.onAction(
                player, powerId, abilityAction.definition().id().getPath());
        if (!abilityAction.definition().id().getPath().equals("quake_seamfinder") || !com.jayptucker.heroesevolved.skills.SkillTreeService.has(player, "quake_surgical_silence")) com.jayptucker.heroesevolved.story.sylar.SylarStoryService.notice(player);
        com.jayptucker.heroesevolved.effects.ArmPoseActions.onAction(
                player, abilityAction.definition().id().getPath());

        int energySpent;
        if (requiresOverexertion) {
            energySpent = OverexertionService.apply(player, energyCost);
            power.onOverexerted(context);
        } else {
            energySpent = PlayerEnergyService.consumeUpTo(player, energyCost);
        }

        // Mastery is earned in proportion to energy spent, so stronger
        // actions earn more and nothing can be spammed for free progress.
        MasteryService.awardEnergySpent(player, energySpent);

        int cooldownTicks = com.jayptucker.heroesevolved.skills.SkillTreeService.cooldown(player, powerId, com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(abilityAction.definition().id(), abilityAction.cooldownTicks(context)));

        // Zero means this action has no cooldown. Avoid storing a zero-tick
        // entry, because a one-tick client/server timing difference can make
        // the HUD briefly display it as a one-second cooldown.
        if (cooldownTicks > 0) {
            CooldownService.startCooldown(
                    player,
                    cooldownId,
                    cooldownTicks
            );
        }

        // The client needs the new cooldown end-time to gray out this slot.
        PlayerPowerSyncService.sync(player);

        com.jayptucker.heroesevolved.expansion.FacilityService.onPowerUse(player, abilityAction.definition().id().getPath());
        return requiresOverexertion
                ? AbilityUseResult.OVEREXERTED_SUCCESS
                : AbilityUseResult.SUCCESS;
    }
}
