package com.jayptucker.heroesevolved.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Mastery and its anti-farming ledger; all fields survive death, logout and server restarts. */
public record PlayerProgressionData(long mastery, long activeTicks, int practiceLeft, boolean explorationReady,
                                    Set<String> explored, Map<String, Integer> encounters) {
    public static final int PRACTICE_PER_MINUTE = 6, MAX_PLACES = 8192;
    public static final Codec<PlayerProgressionData> CODEC =
        RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.LONG.fieldOf("mastery").forGetter(PlayerProgressionData::mastery),
                Codec.LONG.optionalFieldOf("active_ticks", 0L).forGetter(PlayerProgressionData::activeTicks),
                Codec.INT.optionalFieldOf("practice_left", PRACTICE_PER_MINUTE).forGetter(PlayerProgressionData::practiceLeft),
                Codec.BOOL.optionalFieldOf("exploration_ready", true).forGetter(PlayerProgressionData::explorationReady),
                Codec.STRING.listOf().xmap(Set::copyOf, ArrayList::new).optionalFieldOf("explored", Set.of()).forGetter(PlayerProgressionData::explored),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("encounters", Map.of()).forGetter(PlayerProgressionData::encounters)
            ).apply(instance, PlayerProgressionData::new)
        );
    
    public PlayerProgressionData {
        if (mastery < 0) {
            throw new IllegalArgumentException("Mastery cannot be negative.");
        }
        activeTicks = Math.max(0, activeTicks);
        practiceLeft = Math.clamp(practiceLeft, 0, PRACTICE_PER_MINUTE);
        explored = Set.copyOf(explored);
        encounters = Map.copyOf(encounters);
    }

    /** Old saves and callers keep their Mastery and receive an empty ledger. */
    public PlayerProgressionData(long mastery) { this(mastery, 0, PRACTICE_PER_MINUTE, true, Set.of(), Map.of()); }

    public PlayerProgressionData withMastery(long value) {
        return new PlayerProgressionData(value, activeTicks, practiceLeft, explorationReady, explored, encounters);
    }

    public PlayerProgressionData active(long ticks, boolean interval) {
        return new PlayerProgressionData(mastery, activeTicks + ticks, interval ? PRACTICE_PER_MINUTE : practiceLeft,
                interval || explorationReady, explored, encounters);
    }

    public PlayerProgressionData practice(int amount) {
        return new PlayerProgressionData(mastery, activeTicks, practiceLeft - amount, explorationReady, explored, encounters);
    }

    public PlayerProgressionData explore(String place) {
        Set<String> next = new HashSet<>(explored); next.add(place);
        return new PlayerProgressionData(mastery, activeTicks, practiceLeft, false, next, encounters);
    }

    public PlayerProgressionData encounter(String place) {
        Map<String, Integer> next = new HashMap<>(encounters); next.merge(place, 1, Integer::sum);
        return new PlayerProgressionData(mastery, activeTicks, practiceLeft, explorationReady, explored, next);
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
        return withMastery(updatedMastery);
    }
}
