package com.jayptucker.heroesevolved.ability.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Powers that were taken out of the mod, and where each one went. The one place every loader asks about a saved power
 * id: a player's own power ({@code events/PowerOwnershipEvents}), a gifted mob's gift, Sylar's watch list and a mimic's
 * memories.
 *
 * <ul>
 *   <li><b>Precognition</b> (removed November 2026) has no successor: its owner gets a random unclaimed power.</li>
 *   <li><b>Technopathy</b> (folded into Electrokinesis, 25 November 2026): its machine control, Kill Switch, interception
 *   and winch now live in Electrokinesis's tree, so a technopath becomes an electrokinetic when that power is free.</li>
 * </ul>
 */
public final class RetiredPowers {
    public static final ResourceLocation PRECOGNITION = id("precognition"), TECHNOPATHY = id("technopathy");

    /** Each retired power and the power that took it over (absent: none did). */
    private static final Map<ResourceLocation, Optional<ResourceLocation>> RETIRED = Map.of(
            PRECOGNITION, Optional.empty(),
            TECHNOPATHY, Optional.of(ModAbilities.ELECTROKINESIS_ID));

    /** A power id read from a save, with a retired power that has a successor turned into its successor. */
    public static final Codec<ResourceLocation> CODEC = ResourceLocation.CODEC.xmap(RetiredPowers::current, id -> id);

    private RetiredPowers() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    public static boolean retired(ResourceLocation power) { return power != null && RETIRED.containsKey(power); }

    /** The power that took over a retired one, if any. Empty for a power that was never retired. */
    public static Optional<ResourceLocation> successor(ResourceLocation power) {
        return power == null ? Optional.empty() : RETIRED.getOrDefault(power, Optional.empty());
    }

    /** {@code power}, or its successor when it was retired and has one. A retired power with none comes back unchanged. */
    public static ResourceLocation current(ResourceLocation power) { return successor(power).orElse(power); }

    /** The chat line a player sees when their saved power was retired and replaced by {@code replacement}. */
    public static String messageKey(ResourceLocation retired, ResourceLocation replacement, boolean awake) {
        boolean successor = successor(retired).filter(replacement::equals).isPresent();
        String which = retired.equals(TECHNOPATHY) ? "technopathy" : "precognition";
        return "message.heroesevolved.retired." + which + (successor ? ".successor" : "") + (awake ? ".awake" : ".dormant");
    }
}
