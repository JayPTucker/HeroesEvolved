package com.jayptucker.heroesevolved.skills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/** Immutable, per-power purchases. Saved with the player, including across death. */
public record SkillTreeData(Map<String, Build> powers) {
    /**
     * {@code highestLevel} is the highest Power Level reached. Saves from before Power Levels kept a tier
     * (1 to 5) under "highest_level"; it is read as the level that tier now starts at, so old Level 5 is 15.
     */
    public record Build(int highestLevel, Set<String> learned, Map<String, String> slots, int training) {
        public static final Codec<Build> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 100).optionalFieldOf("highest_level").forGetter(b -> Optional.empty()),
                Codec.intRange(1, 100).optionalFieldOf("highest_power_level").forGetter(b -> Optional.of(b.highestLevel())),
                Codec.STRING.listOf().xmap(Set::copyOf, ArrayList::new).fieldOf("learned").forGetter(Build::learned),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("slots").forGetter(Build::slots),
                Codec.intRange(0, 6).optionalFieldOf("training", 0).forGetter(Build::training)
        ).apply(i, (tier, level, learned, slots, training) -> new Build(
                level.orElseGet(() -> com.jayptucker.heroesevolved.progression.PowerLevels.levelForTier(tier.orElse(1))),
                learned, slots, training)));
        public Build { learned = Set.copyOf(learned); slots = Map.copyOf(slots); }
        public Build(int level, Set<String> learned, Map<String, String> slots) { this(level, learned, slots, 0); }
        public static Build empty() { return new Build(1, Set.of(), Map.of()); }
        /** Points from the highest Power Level reached and from training; story points are added on top. */
        public int earned() { return com.jayptucker.heroesevolved.progression.PowerLevels.points(highestLevel) + training; }
    }
    public static final Codec<SkillTreeData> CODEC = Codec.unboundedMap(Codec.STRING, Build.CODEC)
            .xmap(SkillTreeData::new, SkillTreeData::powers);
    public SkillTreeData { powers = Map.copyOf(powers); }
    public static SkillTreeData empty() { return new SkillTreeData(Map.of()); }
    public Build build(String power) { return powers.getOrDefault(power, Build.empty()); }
    public SkillTreeData with(String power, Build build) {
        var next = new HashMap<>(powers); next.put(power, build); return new SkillTreeData(next);
    }
}
