package com.jayptucker.heroesevolved.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlayerProgressionData(long mastery) {
    public static final Codec<PlayerProgressionData> CODEC =
        RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.LONG.fieldOf("mastery")
                    .forGetter(PlayerProgressionData::mastery)
            ).apply(instance, PlayerProgressionData::new)
        );
    
    public PlayerProgressionData {
        if (mastery < 0) {
            throw new IllegalArgumentException("Mastery cannot be negative.");
        }
    }

    public static PlayerProgressionData empty() {
        return new PlayerProgressionData(0);
    }

    public PlayerProgressionData addMastery(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Mastery gain must be positive.");
        }

        // mastery + amount can wrap negative before Math.min sees it, which
        // would throw from the constructor. Saturate instead.
        long updatedMastery = Long.MAX_VALUE - mastery < amount
                ? Long.MAX_VALUE
                : mastery + amount;
        return new PlayerProgressionData(updatedMastery);
    }
}
