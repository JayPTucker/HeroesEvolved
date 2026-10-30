package com.jayptucker.heroesevolved.story.sylar;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sylar's voice, paced. Everything he says, and every line of narration about him, goes through a
 * short per-player queue that delivers at most one line every eight seconds, so a fight or a sighting
 * never becomes a wall of text. Warnings the player must act on right now (the hold, the pin, the
 * escape) skip the queue.
 */
public final class SylarVoice {
    public static final int GAP_TICKS = 160, QUEUE = 3;
    private record Line(Component text, boolean speech) {}
    private static final Map<UUID, Deque<Line>> QUEUES = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private SylarVoice() {}

    /** Something he says, in his own voice. */
    public static void say(ServerPlayer player, String key, Object... args) {
        queue(player, new Line(Component.literal("Sylar: ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.translatable(key, args).withStyle(ChatFormatting.RED)), true));
    }

    /** What the player notices. */
    public static void narrate(ServerPlayer player, String key, Object... args) {
        queue(player, new Line(Component.translatable(key, args).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false));
    }

    /** A warning that cannot wait: on the action bar at once, and never queued. */
    public static void warn(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
    }

    private static void queue(ServerPlayer player, Line line) {
        Deque<Line> queue = QUEUES.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        // When the queue is full, narration gives way to what he says.
        while (queue.size() >= QUEUE) {
            if (!queue.removeIf(l -> !l.speech)) queue.removeFirst();
        }
        queue.addLast(line);
        flush(player, player.getServer().overworld().getGameTime());
    }

    private static void flush(ServerPlayer player, long now) {
        Deque<Line> queue = QUEUES.get(player.getUUID());
        if (queue == null || queue.isEmpty()) return;
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < GAP_TICKS) return;
        player.sendSystemMessage(queue.removeFirst().text);
        LAST.put(player.getUUID(), now);
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (var entry : java.util.List.copyOf(QUEUES.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) { QUEUES.remove(entry.getKey()); continue; }
            flush(player, now);
        }
    }

    /** Lines still waiting to be said to this player. */
    public static int pending(ServerPlayer player) {
        Deque<Line> queue = QUEUES.get(player.getUUID());
        return queue == null ? 0 : queue.size();
    }

    public static void forget(UUID player) { QUEUES.remove(player); LAST.remove(player); }

    public static void clear() { QUEUES.clear(); LAST.clear(); }
}
