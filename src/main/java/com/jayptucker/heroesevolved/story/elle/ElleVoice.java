package com.jayptucker.heroesevolved.story.elle;

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
 * Elle's voice and the omens' narration, paced like Sylar's: at most one line every six seconds per player, so a fight
 * never becomes a wall of text. Warnings the player must act on now go to the action bar at once.
 */
public final class ElleVoice {
    public static final int GAP_TICKS = 120, QUEUE = 3;
    private static final Map<UUID, Deque<Component>> QUEUES = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private ElleVoice() {}

    /** Something Elle says. */
    public static void say(ServerPlayer player, String key, Object... args) {
        queue(player, Component.literal("Elle: ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.translatable(key, args).withStyle(ChatFormatting.WHITE)));
    }

    /** What the player notices. */
    public static void narrate(ServerPlayer player, String key, Object... args) {
        queue(player, Component.translatable(key, args).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** A warning that cannot wait. */
    public static void warn(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
    }

    private static void queue(ServerPlayer player, Component line) {
        Deque<Component> queue = QUEUES.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        while (queue.size() >= QUEUE) queue.removeFirst();
        queue.addLast(line);
        flush(player, player.getServer().overworld().getGameTime());
    }

    private static void flush(ServerPlayer player, long now) {
        Deque<Component> queue = QUEUES.get(player.getUUID());
        if (queue == null || queue.isEmpty()) return;
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < GAP_TICKS && now >= last) return;
        player.sendSystemMessage(queue.removeFirst());
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

    public static void forget(UUID player) { QUEUES.remove(player); LAST.remove(player); }
    public static void clear() { QUEUES.clear(); LAST.clear(); }
}
