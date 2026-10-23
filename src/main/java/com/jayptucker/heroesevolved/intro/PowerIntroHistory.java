package com.jayptucker.heroesevolved.intro;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.Set;

/** Each power has its own first-acquisition marker, retained across death and reassignment. */
public record PowerIntroHistory(Set<ResourceLocation> seen) {
    public static final Codec<PowerIntroHistory> CODEC = ResourceLocation.CODEC.listOf().xmap(
            values -> new PowerIntroHistory(Set.copyOf(values)),
            history -> history.seen.stream().sorted().toList());
    public PowerIntroHistory { seen = Set.copyOf(seen); }
    public static PowerIntroHistory empty() { return new PowerIntroHistory(Set.of()); }
    public PowerIntroHistory with(ResourceLocation power) {
        Set<ResourceLocation> updated = new HashSet<>(seen);
        updated.add(power);
        return new PowerIntroHistory(updated);
    }
}
