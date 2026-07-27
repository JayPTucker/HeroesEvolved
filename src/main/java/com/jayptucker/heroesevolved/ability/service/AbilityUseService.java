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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;

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
        if (!abilityAction.definition().isUnlockedAt(progress.level())) {
            return AbilityUseResult.ACTION_LOCKED;
        }

        ResourceLocation actionId = abilityAction.definition().id();

        if (CooldownService.isOnCooldown(player, actionId)) {
            return AbilityUseResult.ON_COOLDOWN;
        }

        AbilityUseContext context = new AbilityUseContext(
                player,
                powerId,
                progress.level()
        );

        if (!abilityAction.canUse(context)) {
            return AbilityUseResult.CANNOT_USE;
        }

        int energyCost = abilityAction.energyCost(progress.level());

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

        int cooldownTicks = abilityAction.cooldownTicks(progress.level());

        // Zero means this action has no cooldown. Avoid storing a zero-tick
        // entry, because a one-tick client/server timing difference can make
        // the HUD briefly display it as a one-second cooldown.
        if (cooldownTicks > 0) {
            CooldownService.startCooldown(
                    player,
                    actionId,
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
