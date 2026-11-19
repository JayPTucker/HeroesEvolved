package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Sublevel 4-R, the Helix Ward: the one sleeper the Company kept apart from the others. On the Last Day a group of new
 * players is sealed in together; if the ward has never had its sleeper, one of the group is drawn by lot, dosed, and
 * sealed in its private pod instead of the hall. A player who lives the day alone is never chosen, and once someone has
 * slept there nobody else ever does. They wake there 103 years later behind a jammed blast door, carrying a random power
 * that sleeps until danger wakes it.
 */
public final class HelixWard {
    private HelixWard() {}

    /**
     * Draws the ward's sleeper from {@code group} (the new players living the Last Day together), or null: there must be
     * at least two of them, and the ward must still be waiting for its one sleeper.
     */
    public static @Nullable UUID draw(BunkerData data, List<UUID> group, RandomSource random) {
        if (group.size() < 2 || !data.labOpen()) return null;
        return group.get(random.nextInt(group.size()));
    }

    /**
     * The dose Weir gave at 6:11: a random power the world hasn't given anyone yet, any of them, Space-Time included,
     * dormant until danger wakes it as any other. Returns false if they already carry one or none are left.
     */
    public static boolean dose(ServerPlayer player) {
        if (PlayerAbilityService.getData(player).hasAssignedPower()) return false;
        List<ResourceLocation> open = AbilityRegistry.ABILITIES.keySet().stream()
                .filter(ModAbilities::rollable)
                .filter(power -> PlayerAbilityService.availableTo(player, power))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        if (open.isEmpty()) return false;
        return PlayerAbilityService.assignDormantAbility(player, open.get(player.getRandom().nextInt(open.size())));
    }

    /** Whether {@code player} is the ward's sleeper in this world. */
    public static boolean sleeper(MinecraftServer server, ServerPlayer player) {
        return player.getUUID().equals(BunkerData.get(server).lab);
    }
}
