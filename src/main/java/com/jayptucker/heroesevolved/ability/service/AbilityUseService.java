package com.jayptucker.heroesevolved.ability.service;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.energy.OverexertionService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class AbilityUseService {
    private AbilityUseService() {
    }

    public static AbilityUseResult activate(
        ServerPlayer player,
        ResourceLocation abilityId,
        boolean allowOverexertion
    ) {
        Ability ability = AbilityRegistry.ABILITIES.get(abilityId);

        // Never trust an ability ID without checking it exists in our registry.
        if (ability == null) {
            return AbilityUseResult.UNKNOWN_ABILITY;
        }

        Optional<AbilityProgress> progress =
            PlayerAbilityService.getAbility(player, abilityId);

        // Players cannot use powers they have not unlocked.
        if (progress.isEmpty() || !progress.get().isUnlocked()) {
            return AbilityUseResult.NOT_UNLOCKED;
        }

        if (CooldownService.isOnCooldown(player, abilityId)) {
            return AbilityUseResult.ON_COOLDOWN;
        }

        AbilityProgress abilityProgress = progress.get();

        AbilityUseContext context = new AbilityUseContext(
            player,
            abilityId,
            abilityProgress.level()
        );

        // Individual abilities can reject use for their own reasons:
        // invalid target, player is flying, no missing health, and so on.
        if (!ability.canUse(context)) {
            return AbilityUseResult.CANNOT_USE;
        }

        int energyCost = ability.energyCost(abilityProgress.level());

        boolean requiresOverexertion =
            PlayerEnergyService.getEnergy(player) < energyCost;

        // The player must deliberately allow overexertion.
        // Abilities never harm a player automatically for lacking energy.
        if (requiresOverexertion && !allowOverexertion) {
            return AbilityUseResult.INSUFFICIENT_ENERGY;
        }

        // The ability itself performs its effect here.
        // We only charge energy if it reports a successful activation.
        if (ability.activate(context) != AbilityActivationResult.SUCCESS) {
            return AbilityUseResult.ACTIVATION_REJECTED;
        }

        if (requiresOverexertion) {
            OverexertionService.apply(player, energyCost);
        } else {
            PlayerEnergyService.tryConsume(player, energyCost);
        }

        // Cooldown starts after a successful use, never after a failed attempt.
        CooldownService.startCooldown(
            player,
            abilityId,
            ability.cooldownTicks(abilityProgress.level())
        );

        return requiresOverexertion
            ? AbilityUseResult.OVEREXERTED_SUCCESS
            : AbilityUseResult.SUCCESS;
    }
}