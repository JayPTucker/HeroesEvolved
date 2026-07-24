package com.jayptucker.heroesevolved.ability.data;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
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


    public PlayerAbilityData assignDormant(ResourceLocation abilityId) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        if (hasAbility(abilityId)) {
            return this;
        }

        return withAbility(abilityId, AbilityProgress.dormant());
    }

        public PlayerAbilityData unlock(ResourceLocation abilityId) {
        AbilityProgress progress = requireAbility(abilityId);
        return withAbility(abilityId, progress.unlock());
    }

    public PlayerAbilityData gainMastery(ResourceLocation abilityId, int amount) {
        AbilityProgress progress = requireAbility(abilityId);
        return withAbility(abilityId, progress.gainMastery(amount));
    }

    public PlayerAbilityData setLevel(ResourceLocation abilityId, int level) {
        AbilityProgress progress = requireAbility(abilityId);
        return withAbility(abilityId, progress.withLevel(level));
    }

    private AbilityProgress requireAbility(ResourceLocation abilityId) {
        return ability(abilityId).orElseThrow(() ->
            new IllegalArgumentException("Player does not have ability: " + abilityId)
        );
    }

    private PlayerAbilityData withAbility(ResourceLocation abilityId, AbilityProgress progress) {
        Map<ResourceLocation, AbilityProgress> updatedAbilities = new HashMap<>(abilities);
        updatedAbilities.put(abilityId, progress);
        return new PlayerAbilityData(updatedAbilities);
    }
}
