package com.jayptucker.heroesevolved.cooldown;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record PlayerCooldownData(
        Map<ResourceLocation, Long> expirationTimes
) {
    public static final Codec<PlayerCooldownData> CODEC =
        RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.unboundedMap(
                        ResourceLocation.CODEC,
                        Codec.LONG
                    )
                    .fieldOf("expiration_times")
                    .forGetter(
                        PlayerCooldownData::expirationTimes
                    )
            ).apply(instance, PlayerCooldownData::new)
        );

    public PlayerCooldownData {
        expirationTimes = Map.copyOf(expirationTimes);
    }

    public static PlayerCooldownData empty() {
        return new PlayerCooldownData(Map.of());
    }

    public long remainingTicks(
        ResourceLocation abilityId,
        long currentGameTime
    ) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        long expirationTime = expirationTimes.getOrDefault(
            abilityId,
            currentGameTime
        );

        return Math.max(0L, expirationTime - currentGameTime);
    }

    public PlayerCooldownData startCooldown(
            ResourceLocation abilityId,
            long expirationTime
    ) {
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        Map<ResourceLocation, Long> updatedCooldowns =
            new HashMap<>(expirationTimes);

        updatedCooldowns.put(abilityId, expirationTime);

        return new PlayerCooldownData(updatedCooldowns);
    }

    public PlayerCooldownData removeExpired(long currentGameTime) {
        Map<ResourceLocation, Long> updatedCooldowns =
            new HashMap<>(expirationTimes);

        updatedCooldowns.entrySet().removeIf(
            entry -> entry.getValue() <= currentGameTime
        );

        return new PlayerCooldownData(updatedCooldowns);
    }
}