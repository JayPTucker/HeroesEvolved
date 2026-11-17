package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Immutable server-tick snapshot. Voice callbacks never inspect entities or the mutable time-field maps. */
public final class TemporalVoiceState {
    public record Listener(boolean affected, boolean perceives) {}
    private static volatile Map<UUID, Listener> players = Map.of();

    private TemporalVoiceState() {}

    public static void publish(MinecraftServer server) {
        Map<UUID, Listener> next = new HashMap<>();
        for (var player : server.getPlayerList().getPlayers())
            next.put(player.getUUID(), new Listener(TimeStopService.reaches(player) || TimeSlowService.reaches(player)
                    || AdvancedTechniques.perceptionReaches(player) || SpaceTimeService.isSuspended(player),
                    player.isAlive() && !player.isSpectator() && TimeVeil.movesInTime(player)));
        players = Map.copyOf(next);
    }

    /** Everyone caught in the changed time is silent, except voices between two temporal perceivers. */
    public static boolean blocks(UUID speaker, UUID listener) {
        Listener from = players.get(speaker), to = players.get(listener);
        return (from != null && from.affected || to != null && to.affected)
                // Radio receivers and server audio channels can have a non-player sender. Frozen microphones are
                // cancelled before radio input; a temporal listener can still hear the remaining radio output.
                && !((from == null || from.perceives) && to != null && to.perceives);
    }

    /** Cancel before Simple Radio or another microphone plugin can retransmit a frozen speaker. */
    public static boolean frozen(UUID speaker) {
        Listener from = players.get(speaker);
        return from != null && from.affected && !from.perceives;
    }

    public static void clear() { players = Map.of(); }
}
