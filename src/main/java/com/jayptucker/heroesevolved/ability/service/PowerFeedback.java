package com.jayptucker.heroesevolved.ability.service;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.WeakHashMap;
public final class PowerFeedback {
    private static final WeakHashMap<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private PowerFeedback() {}
    public static void reject(ServerPlayer player, String reason) {
        long now = player.level().getGameTime();
        if (now - LAST.getOrDefault(player, Long.MIN_VALUE / 2) < 10) return;
        LAST.put(player, now); player.displayClientMessage(Component.literal(reason), true);
    }
}
