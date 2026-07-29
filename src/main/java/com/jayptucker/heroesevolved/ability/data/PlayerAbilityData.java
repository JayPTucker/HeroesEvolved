package com.jayptucker.heroesevolved.ability.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlayerAbilityData(Map<ResourceLocation, AbilityProgress> abilities) {

    public static final Codec<PlayerAbilityData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, AbilityProgress.CODEC)
                .fieldOf("abilities")
                .forGetter(PlayerAbilityData::abilities)
        ).apply(instance, PlayerAbilityData::new)
    );

    public PlayerAbilityData {
        abilities = Map.copyOf(abilities);

        if (abilities.size() > 1) {
            throw new IllegalArgumentException(
                "A player can only have one assigned power."
            );
        }
    }


    public static PlayerAbilityData empty() {
        return new PlayerAbilityData(Map.of());
    }


    public Optional<AbilityProgress> ability(ResourceLocation abilityId) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");
        return Optional.ofNullable(abilities.get(abilityId));
    }


    public boolean hasAbility(ResourceLocation abilityId) {
        return ability(abilityId).isPresent();
    }

    public boolean hasAssignedPower() {
        return !abilities.isEmpty();
    }

    public Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignedPower() {
        return abilities.entrySet().stream().findFirst();
    }


    public PlayerAbilityData assignDormant(ResourceLocation abilityId) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        if (hasAssignedPower()) {
            if (hasAbility(abilityId)) {
                return this;
            }

            throw new IllegalStateException(
                "A player already has an assigned power."
            );
        }

        return new PlayerAbilityData(Map.of(abilityId, AbilityProgress.dormant()));
    }

        public PlayerAbilityData unlock(ResourceLocation abilityId) {
        AbilityProgress progress = requireAbility(abilityId);
        return withProgress(abilityId, progress.unlock());
    }

    public PlayerAbilityData gainMastery(ResourceLocation abilityId, int amount) {
        AbilityProgress progress = requireAbility(abilityId);
        return withProgress(abilityId, progress.gainMastery(amount));
    }

    public PlayerAbilityData setLevel(ResourceLocation abilityId, int level) {
        AbilityProgress progress = requireAbility(abilityId);
        return withProgress(abilityId, progress.withLevel(level));
    }

    public PlayerAbilityData replaceWithUnlockedPower(ResourceLocation abilityId) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        return new PlayerAbilityData(Map.of(
            abilityId,
            AbilityProgress.dormant().unlock()
        ));
    }

    public PlayerAbilityData clearAssignedPower() {
        return empty();
    }

    private AbilityProgress requireAbility(ResourceLocation abilityId) {
        return ability(abilityId).orElseThrow(() ->
            new IllegalArgumentException("Player does not have ability: " + abilityId)
        );
    }

    private PlayerAbilityData withProgress(
            ResourceLocation abilityId,
            AbilityProgress progress
    ) {
        return new PlayerAbilityData(Map.of(abilityId, progress));
    }
}
