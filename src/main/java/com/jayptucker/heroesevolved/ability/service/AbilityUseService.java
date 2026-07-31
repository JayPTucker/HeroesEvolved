package com.jayptucker.heroesevolved.ability.service;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;

import java.util.Map;
import java.util.Optional;

public final class AbilityUseService {
    private AbilityUseService() {
    }

    public static AbilityUseResult activateAssignedAction(
            ServerPlayer player,
            AbilitySlot slot,
            boolean allowOverexertion
    ) {
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

        Optional<AbilityAction> action = power.action(slot);

        if (action.isEmpty()) {
            return AbilityUseResult.ACTION_NOT_ASSIGNED;
        }

        AbilityAction abilityAction = action.get();

        // Individual actions unlock at different levels of the same power.
        int powerLevel = PlayerProgressionService.getLevel(player);

        if (!abilityAction.definition().isUnlockedAt(powerLevel)) {
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
            return AbilityUseResult.ON_COOLDOWN;
        }

        if (!abilityAction.canUse(context)) {
            return AbilityUseResult.CANNOT_USE;
        }

        int energyCost = abilityAction.energyCost(context);

        boolean requiresOverexertion =
                PlayerEnergyService.getEnergy(player) < energyCost;

        // Holding Shift when pressing a slot is the player's explicit consent
        // to use overexertion if they lack the required energy.
        if (requiresOverexertion && !allowOverexertion) {
            return AbilityUseResult.INSUFFICIENT_ENERGY;
        }

        if (abilityAction.activate(context)
                != AbilityActivationResult.SUCCESS) {
            return AbilityUseResult.ACTIVATION_REJECTED;
        }

        if (requiresOverexertion) {
            OverexertionService.apply(player, energyCost);
        } else {
            PlayerEnergyService.tryConsume(player, energyCost);
        }

        int cooldownTicks = abilityAction.cooldownTicks(context);

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

        return requiresOverexertion
                ? AbilityUseResult.OVEREXERTED_SUCCESS
                : AbilityUseResult.SUCCESS;
    }
}
