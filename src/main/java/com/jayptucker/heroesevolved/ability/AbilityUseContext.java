package com.jayptucker.heroesevolved.ability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public record AbilityUseContext(
    ServerPlayer player, 
    ResourceLocation abilityId, 
    int abilityLevel
) {
    public AbilityUseContext {
        Objects.requireNonNull(player, "Player cannot be null.");
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        if (abilityLevel < 1) {
            throw new IllegalArgumentException("Ability level must be at least one.");
        }
    }

    public long gameTime() {
        return player.serverLevel().getGameTime();
    }
}