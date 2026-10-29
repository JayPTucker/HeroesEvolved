package com.jayptucker.heroesevolved.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a player has done with their powers, kept across death: story notes found (each one a skill
 * point), how often each action was used (its rank), progress on each power's Evolved trial, and the
 * powers that have Evolved. Action and trial keys are prefixed with the power's path.
 */
public record PowerJourney(Set<String> discoveries, Map<String, Integer> uses, Map<String, Integer> trials, Set<String> evolved) {
    public static final int MAX_STORY_POINTS = 8;
    public static final Codec<PowerJourney> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().xmap(Set::copyOf, ArrayList::new).optionalFieldOf("discoveries", Set.of()).forGetter(PowerJourney::discoveries),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("uses", Map.of()).forGetter(PowerJourney::uses),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("trials", Map.of()).forGetter(PowerJourney::trials),
            Codec.STRING.listOf().xmap(Set::copyOf, ArrayList::new).optionalFieldOf("evolved", Set.of()).forGetter(PowerJourney::evolved)
    ).apply(i, PowerJourney::new));

    public PowerJourney {
        discoveries = Set.copyOf(discoveries); uses = Map.copyOf(uses); trials = Map.copyOf(trials); evolved = Set.copyOf(evolved);
    }

    public static PowerJourney empty() { return new PowerJourney(Set.of(), Map.of(), Map.of(), Set.of()); }

    public int storyPoints() { return Math.min(MAX_STORY_POINTS, discoveries.size()); }

    public PowerJourney discover(String id) {
        Set<String> next = new HashSet<>(discoveries); next.add(id);
        return new PowerJourney(next, uses, trials, evolved);
    }

    public int uses(String power, String action) { return uses.getOrDefault(power + "|" + action, 0); }

    public PowerJourney used(String power, String action) {
        Map<String, Integer> next = new HashMap<>(uses); next.merge(power + "|" + action, 1, Integer::sum);
        return new PowerJourney(discoveries, next, trials, evolved);
    }

    public int trial(String power) { return trials.getOrDefault(power, 0); }

    public PowerJourney advance(String power, int amount) {
        Map<String, Integer> next = new HashMap<>(trials); next.merge(power, amount, Integer::sum);
        return new PowerJourney(discoveries, uses, next, evolved);
    }

    public boolean isEvolved(String power) { return evolved.contains(power); }

    public PowerJourney evolve(String power) {
        Set<String> next = new HashSet<>(evolved); next.add(power);
        return new PowerJourney(discoveries, uses, trials, next);
    }
}
