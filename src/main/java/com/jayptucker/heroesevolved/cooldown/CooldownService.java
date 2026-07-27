package com.jayptucker.heroesevolved.cooldown;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class CooldownService {
    private CooldownService() {
    }

    public static boolean isOnCooldown(
        ServerPlayer player,
        ResourceLocation abilityId
    ) {
        return getRemainingTicks(player, abilityId) > 0;
    }

    public static long getRemainingTicks(
        ServerPlayer player,
        ResourceLocation abilityId
    ) {
        Objects.requireNonNull(player, "Player cannot be null.");
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        long gameTime = player.serverLevel().getGameTime();

        return player.getData(
            ModDataAttachments.PLAYER_COOLDOWNS.get()
        ).remainingTicks(abilityId, gameTime);
    }

    public static void startCooldown(
            ServerPlayer player,
            ResourceLocation abilityId,
            int durationTicks
    ) {
        Objects.requireNonNull(player, "Player cannot be null.");
        Objects.requireNonNull(abilityId, "Ability ID cannot be null.");

        if (durationTicks < 0) {
            throw new IllegalArgumentException(
                "Cooldown duration cannot be negative."
            );
        }

        if (durationTicks == 0) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        long expirationTime = gameTime + durationTicks;

        PlayerCooldownData cooldownData = player.getData(
            ModDataAttachments.PLAYER_COOLDOWNS.get()
        );

        player.setData(
            ModDataAttachments.PLAYER_COOLDOWNS.get(),
            cooldownData
                .removeExpired(gameTime)
                .startCooldown(abilityId, expirationTime)
        );
    }
}