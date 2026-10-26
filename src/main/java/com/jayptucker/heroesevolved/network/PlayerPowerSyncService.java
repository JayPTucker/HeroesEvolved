package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.progression.ProgressionCalculator;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.ability.types.TimeManipulationAbility;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class PlayerPowerSyncService {
    private PlayerPowerSyncService() {
    }

    public static void sync(ServerPlayer player) {
        long mastery = PlayerProgressionService.getData(player).mastery();
        int level = PlayerProgressionService.getLevel(player);
        int maximumLevel = HeroesEvolvedConfig.COMMON.maximumLevel.get();
        long masteryRequiredForCurrentLevel =
                ProgressionCalculator.masteryRequiredForLevel(level);
        long masteryRequiredForNextLevel = level >= maximumLevel
                ? mastery
                : ProgressionCalculator.masteryRequiredForLevel(level + 1);
        Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignment =
                PlayerAbilityService.getData(player).assignedPower();

        List<AbilitySnapshot> abilities = assignment
                .map(entry -> List.of(new AbilitySnapshot(
                        entry.getKey(),
                        entry.getValue().isUnlocked(),
                        level,
                        mastery,
                        masteryRequiredForCurrentLevel,
                        masteryRequiredForNextLevel,
                        maximumLevel
                )))
                .orElseGet(List::of);

        List<AbilityActionSnapshot> actions = assignment
                .map(entry -> createActionSnapshots(
                        player,
                        entry.getKey()
                ))
                .orElseGet(List::of);

        PacketDistributor.sendToPlayer(
                player,
                new PlayerPowerSyncPayload(
                        PlayerEnergyService.getEnergy(player),
                        PlayerEnergyService.getMaximumEnergy(player),
                        PlayerEnergyService.regenerationPerSecond(player),
                        abilities,
                        actions
                )
        );
    }

    private static List<AbilityActionSnapshot> createActionSnapshots(
            ServerPlayer player,
            ResourceLocation powerId
    ) {
        Ability power = AbilityRegistry.ABILITIES.get(powerId);

        if (power == null) {
            return List.of();
        }

        return com.jayptucker.heroesevolved.skills.SkillTreeService.actions(player, powerId)
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> toSnapshots(
                        player,
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    private static Stream<AbilityActionSnapshot> toSnapshots(
            ServerPlayer player,
            com.jayptucker.heroesevolved.ability.AbilitySlot slot,
            AbilityAction action
    ) {
        ResourceLocation actionId = action.definition().id();
        int level = PlayerProgressionService.getLevel(player);
        int unlockLevel = action.definition().unlockLevel();
        boolean unlocked = action.definition().isUnlockedAt(level);

        if (actionId.equals(ResourceLocation.fromNamespaceAndPath(
                com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID,
                "temporal_snapshot"
        ))) {
            AbilityUseContext create = previewContext(player, true);
            AbilityUseContext travel = previewContext(player, false);
            return Stream.of(
                    new AbilityActionSnapshot(
                            slot,
                            "hud.heroesevolved.temporal_snapshot_create",
                            unlocked,
                            CooldownService.getExpirationGameTime(player,
                                    TimeManipulationAbility
                                            .TEMPORAL_SNAPSHOT_CREATE_COOLDOWN),
                            unlockLevel,
                            com.jayptucker.heroesevolved.skills.SkillTreeService.activationCost(player, create.abilityId(), com.jayptucker.heroesevolved.ability.PowerBalance.energy(actionId, action.energyCost(create))),
                            com.jayptucker.heroesevolved.skills.SkillTreeService.cooldown(player, create.abilityId(), com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(actionId, action.cooldownTicks(create)))
                    ),
                    new AbilityActionSnapshot(
                            slot,
                            "hud.heroesevolved.temporal_snapshot_travel",
                            unlocked,
                            CooldownService.getExpirationGameTime(player,
                                    TimeManipulationAbility
                                            .TEMPORAL_SNAPSHOT_TRAVEL_COOLDOWN),
                            unlockLevel,
                            com.jayptucker.heroesevolved.skills.SkillTreeService.activationCost(player, travel.abilityId(), com.jayptucker.heroesevolved.ability.PowerBalance.energy(actionId, action.energyCost(travel))),
                            com.jayptucker.heroesevolved.skills.SkillTreeService.cooldown(player, travel.abilityId(), com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(actionId, action.cooldownTicks(travel)))
                    )
            );
        }

        AbilityUseContext preview = previewContext(player, false);
        return Stream.of(new AbilityActionSnapshot(
                slot,
                action.definition().displayNameKey(),
                unlocked,
                CooldownService.getExpirationGameTime(player, actionId),
                unlockLevel,
                com.jayptucker.heroesevolved.skills.SkillTreeService.activationCost(player, preview.abilityId(), com.jayptucker.heroesevolved.ability.PowerBalance.energy(actionId, action.energyCost(preview))),
                com.jayptucker.heroesevolved.skills.SkillTreeService.cooldown(player, preview.abilityId(), com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(actionId, action.cooldownTicks(preview)))
        ));
    }

    /**
     * Costs and cooldowns can depend on the player's level and state, so
     * the skills menu shows the values that would apply right now.
     */
    private static AbilityUseContext previewContext(
            ServerPlayer player,
            boolean modifierHeld
    ) {
        return new AbilityUseContext(
                player,
                PlayerAbilityService.getData(player).assignedPower()
                        .map(Map.Entry::getKey)
                        .orElseThrow(),
                PlayerProgressionService.getLevel(player),
                modifierHeld
        );
    }
}
