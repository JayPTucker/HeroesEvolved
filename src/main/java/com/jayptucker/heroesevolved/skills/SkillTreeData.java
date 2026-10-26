package com.jayptucker.heroesevolved.skills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/** Immutable, per-power purchases. Saved with the player, including across death. */
public record SkillTreeData(Map<String, Build> powers) {
    public record Build(int highestLevel, Set<String> learned, Map<String, String> slots) {
        public static final Codec<Build> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 100).fieldOf("highest_level").forGetter(Build::highestLevel),
                Codec.STRING.listOf().xmap(Set::copyOf, ArrayList::new).fieldOf("learned").forGetter(Build::learned),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("slots").forGetter(Build::slots)
        ).apply(i, Build::new));
        public Build { learned = Set.copyOf(learned); slots = Map.copyOf(slots); }
        public static Build empty() { return new Build(1, Set.of(), Map.of()); }
        public int earned() { return 3 + 2 * (highestLevel - 1); }
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
