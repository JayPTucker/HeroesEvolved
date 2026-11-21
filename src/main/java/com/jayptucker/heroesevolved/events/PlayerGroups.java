package com.jayptucker.heroesevolved.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Players who are together, for world events that roll by place rather than by player: anyone within {@code radius} of
 * someone in a group is in it (so a line of friends is one group). A server of players spread over the map gets one roll
 * per group, each as often as a player on their own would; friends standing together share theirs, so playing together
 * doesn't multiply what falls on them. Spectators are left out.
 */
public final class PlayerGroups {
    private PlayerGroups() {}

    public static List<List<ServerPlayer>> of(ServerLevel level, double radius) { return of(level.players(), radius); }

    public static List<List<ServerPlayer>> of(List<ServerPlayer> players, double radius) {
        List<ServerPlayer> left = new ArrayList<>();
        for (ServerPlayer p : players) if (!p.isSpectator() && p.isAlive()) left.add(p);
        List<List<ServerPlayer>> groups = new ArrayList<>();
        double r2 = radius * radius;
        while (!left.isEmpty()) {
            List<ServerPlayer> group = new ArrayList<>();
            Deque<ServerPlayer> open = new ArrayDeque<>();
            open.add(left.removeFirst());
            while (!open.isEmpty()) {
                ServerPlayer p = open.removeFirst();
                group.add(p);
                for (ServerPlayer q : List.copyOf(left))
                    if (q.level() == p.level() && q.distanceToSqr(p) <= r2) {
                        left.remove(q);
                        open.add(q);
                    }
            }
            groups.add(group);
        }
        return groups;
    }

    /** Whether {@code a} and {@code b} are together (within {@code radius} of one another, or linked through others). */
    public static boolean together(ServerLevel level, ServerPlayer a, ServerPlayer b, double radius) {
        for (List<ServerPlayer> g : of(level, radius)) if (g.contains(a)) return g.contains(b);
        return false;
    }
}
