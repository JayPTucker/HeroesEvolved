package com.jayptucker.heroesevolved.energy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Powers held for as long as you can pay for them. A drain takes {@code perSecond} stamina a little every
 * server tick; when the bar can't cover the next tick the drain ends and calls {@code onEmpty}, which ends the
 * power. Still World and Between Seconds use it: they last until the key is pressed again or the stamina runs
 * out.
 *
 * <p>A drain that {@code holdsRecovery} stops stamina recovering while it runs (the world is stopped, and so
 * is your second wind); others let recovery work against them.</p>
 *
 * <p>Drains tick on the server tick and keep their {@link ServerPlayer}, so they run for game-test players
 * too, which the level never ticks.</p>
 */
public final class StaminaDrain {
    public record Drain(ServerPlayer player, String id, double perSecond, boolean holdsRecovery, Runnable onEmpty) {}

    private static final Map<UUID, Map<String, Drain>> DRAINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> MASTERY = new ConcurrentHashMap<>();

    private StaminaDrain() {}

    /** Starts (or replaces) a drain. {@code perSecond} is scaled by the server's stamina cost scale. */
    public static void start(ServerPlayer player, String id, double perSecond, boolean holdsRecovery, Runnable onEmpty) {
        double scaled = perSecond * com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.staminaCostScale.get();
        DRAINS.computeIfAbsent(player.getUUID(), key -> new ConcurrentHashMap<>())
                .put(id, new Drain(player, id, Math.max(0, scaled), holdsRecovery, onEmpty));
    }

    /** Ends a drain without calling its {@code onEmpty}. */
    public static void stop(ServerPlayer player, String id) {
        Map<String, Drain> drains = DRAINS.get(player.getUUID());
        if (drains == null) return;
        if (drains.remove(id) != null) PlayerEnergyService.drainEnded(player);
        if (drains.isEmpty()) DRAINS.remove(player.getUUID());
    }

    public static boolean active(ServerPlayer player, String id) {
        Map<String, Drain> drains = DRAINS.get(player.getUUID());
        return drains != null && drains.containsKey(id);
    }

    public static boolean active(ServerPlayer player) {
        Map<String, Drain> drains = DRAINS.get(player.getUUID());
        return drains != null && !drains.isEmpty();
    }

    /** Stamina per second every held power of this player is taking. */
    public static double rate(ServerPlayer player) {
        Map<String, Drain> drains = DRAINS.get(player.getUUID());
        return drains == null ? 0 : drains.values().stream().mapToDouble(Drain::perSecond).sum();
    }

    public static boolean holdsRecovery(ServerPlayer player) {
        Map<String, Drain> drains = DRAINS.get(player.getUUID());
        return drains != null && drains.values().stream().anyMatch(Drain::holdsRecovery);
    }

    public static void forget(ServerPlayer player) {
        DRAINS.remove(player.getUUID());
        MASTERY.remove(player.getUUID());
    }

    public static void clear() {
        DRAINS.clear();
        MASTERY.clear();
    }

    public static void tick(MinecraftServer server) {
        List<Drain> empty = new ArrayList<>();
        for (var entry : List.copyOf(DRAINS.entrySet())) {
            for (Drain drain : List.copyOf(entry.getValue().values())) {
                ServerPlayer player = drain.player();
                if (player.isRemoved() || !player.isAlive()) {
                    stop(player, drain.id());
                    empty.add(drain);
                    continue;
                }
                if (!PlayerEnergyService.drain(player, drain.perSecond() / 20.0D)) {
                    stop(player, drain.id());
                    empty.add(drain);
                    continue;
                }
                // Mastery is earned on stamina spent, a whole point at a time.
                double owed = MASTERY.merge(player.getUUID(), drain.perSecond() / 20.0D, Double::sum);
                if (owed >= 1) {
                    MASTERY.put(player.getUUID(), owed - (int) owed);
                    com.jayptucker.heroesevolved.progression.MasteryService.awardEnergySpent(player, (int) owed);
                }
            }
        }
        // Ending a power can start or stop other drains, so the callbacks run after the walk.
        for (Drain drain : empty) {
            try { drain.onEmpty().run(); } catch (RuntimeException ignored) {}
        }
    }
}
