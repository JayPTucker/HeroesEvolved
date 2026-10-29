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

        sendLoadout(player);
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

    private static final java.util.Map<java.util.UUID, LoadoutPayload> SENT_LOADOUTS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The wheel, the key bindings and Power Level, sent only when something in them changed. */
    private static void sendLoadout(ServerPlayer player) {
        var assignment = PlayerAbilityService.getData(player).assignedPower();
        LoadoutPayload payload = assignment.filter(e -> e.getValue().isUnlocked()).map(e -> loadout(player, e.getKey()))
                .orElseGet(() -> new LoadoutPayload(1, 0, 1, false, "", 0, 1, 0, List.of(), List.of()));
        if (payload.equals(SENT_LOADOUTS.get(player.getUUID()))) return;
        SENT_LOADOUTS.put(player.getUUID(), payload);
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void forget(ServerPlayer player) { SENT_LOADOUTS.remove(player.getUUID()); }

    private static LoadoutPayload loadout(ServerPlayer player, ResourceLocation power) {
        int level = PlayerProgressionService.getPowerLevel(player), tier = PlayerProgressionService.getLevel(player);
        long mastery = PlayerProgressionService.getData(player).mastery();
        boolean evolved = PlayerProgressionService.evolved(player);
        long here = com.jayptucker.heroesevolved.progression.PowerLevels.masteryFor(level);
        long next = level >= com.jayptucker.heroesevolved.progression.PowerLevels.MAX ? here
                : com.jayptucker.heroesevolved.progression.PowerLevels.masteryFor(level + 1);
        var trial = com.jayptucker.heroesevolved.progression.EvolutionTrials.trial(power.getPath());
        AbilityUseContext preview = previewContext(player, false);
        List<LoadoutPayload.Entry> entries = new java.util.ArrayList<>();
        for (var entry : com.jayptucker.heroesevolved.skills.Loadout.available(player, power)) {
            AbilityAction action = entry.action();
            ResourceLocation id = action.definition().id();
            int unlockTier = entry.node() != null ? entry.node().level() : action.definition().unlockLevel();
            int energy = com.jayptucker.heroesevolved.ability.service.AbilityRanks.energy(player, power, action,
                    com.jayptucker.heroesevolved.skills.SkillTreeService.activationCost(player, power,
                            com.jayptucker.heroesevolved.ability.PowerBalance.energy(id, action.energyCost(preview))));
            int cooldown = com.jayptucker.heroesevolved.ability.service.AbilityRanks.cooldown(player, power, action,
                    com.jayptucker.heroesevolved.skills.SkillTreeService.cooldown(player, power,
                            com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(id, action.cooldownTicks(preview))));
            int uses = com.jayptucker.heroesevolved.ability.service.AbilityRanks.uses(player, power, action);
            entries.add(new LoadoutPayload.Entry(entry.key(), id.getPath(), action.definition().displayNameKey(), tier >= unlockTier,
                    com.jayptucker.heroesevolved.progression.PowerLevels.levelForTier(unlockTier), energy, cooldown,
                    CooldownService.getExpirationGameTime(player, action.cooldownId(preview)),
                    com.jayptucker.heroesevolved.ability.service.AbilityRanks.rank(uses), uses));
        }
        var build = com.jayptucker.heroesevolved.skills.SkillTreeService.build(player, power);
        List<String> keys = new java.util.ArrayList<>();
        for (int key = 1; key <= com.jayptucker.heroesevolved.skills.Loadout.KEYS; key++)
            keys.add(com.jayptucker.heroesevolved.skills.Loadout.binding(build, key));
        return new LoadoutPayload(level, Math.max(0, Math.min(mastery, next) - here), Math.max(0, next - here), evolved, trial.description(),
                Math.min(trial.goal(), com.jayptucker.heroesevolved.progression.EvolutionTrials.progress(player, power.getPath())), trial.goal(),
                com.jayptucker.heroesevolved.skills.SkillTreeService.storyPoints(player), List.copyOf(entries), List.copyOf(keys));
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
