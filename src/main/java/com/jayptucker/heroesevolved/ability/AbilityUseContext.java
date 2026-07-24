package com.jayptucker.heroesevolved.ability;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public record AbilityUseContext(ServerPlayer player) {
    public AbilityUseContext {
        Objects.requireNonNull(player, "Player cannot be null.");
    }

    public long gameTime() {
        return player.serverLevel().getGameTime();
    }
}