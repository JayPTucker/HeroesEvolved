package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Awards Mastery for meaningful power use, non-AFK play, and hostile-mob
 * kills. The short-lived trackers intentionally reset after a server restart;
 * only the player's earned Mastery is persistent.
 */
public final class MasteryService {
    private static final long ACTIVE_WINDOW_TICKS = 20L * 30L;
    private static final double MINIMUM_ACTIVE_MOVEMENT_SQUARED = 4.0D;

    private static final Map<UUID, ActiveTimeTracker> ACTIVE_TIME_TRACKERS =
            new HashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Long>>
            POWER_USE_TIMES = new HashMap<>();
    private static final Map<UUID, Map<EntityType<?>, Deque<Long>>>
            MOB_KILL_TIMES = new HashMap<>();

    private MasteryService() {
    }

    public static void tickActiveTime(ServerPlayer player) {
        if (!hasAwakenedPower(player)) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        ActiveTimeTracker tracker = ACTIVE_TIME_TRACKERS.computeIfAbsent(
                player.getUUID(),
                ignored -> new ActiveTimeTracker(
                        player.position(),
                        gameTime,
                        gameTime
                )
        );

        if (player.position().distanceToSqr(tracker.lastPosition())
                >= MINIMUM_ACTIVE_MOVEMENT_SQUARED) {
            tracker = tracker.withActivity(player.position(), gameTime);
        }

        int intervalTicks = HeroesEvolvedConfig.COMMON
                .activeMasteryIntervalTicks
                .get();

        if (gameTime - tracker.lastAwardGameTime() >= intervalTicks
                && gameTime - tracker.lastActivityGameTime()
                <= ACTIVE_WINDOW_TICKS) {
            awardMastery(
                    player,
                    HeroesEvolvedConfig.COMMON.activeMasteryAmount.get()
            );
            tracker = tracker.withAward(gameTime);
        }

        ACTIVE_TIME_TRACKERS.put(player.getUUID(), tracker);
    }

    public static boolean awardPowerUse(
            ServerPlayer player,
            ResourceLocation abilityId
    ) {
        if (!hasAwakenedPower(player)) {
            return false;
        }

        long gameTime = player.serverLevel().getGameTime();
        Map<ResourceLocation, Long> useTimes = POWER_USE_TIMES.computeIfAbsent(
                player.getUUID(),
                ignored -> new HashMap<>()
        );
        long lastUseTime = useTimes.getOrDefault(abilityId, Long.MIN_VALUE);
        int cooldownTicks = HeroesEvolvedConfig.COMMON
                .powerUseMasteryCooldownTicks
                .get();

        // The first use has no previous timestamp. Checking the sentinel
        // explicitly avoids overflowing when the world time is still small.
        if (lastUseTime != Long.MIN_VALUE
                && gameTime - lastUseTime < cooldownTicks) {
            return false;
        }

        useTimes.put(abilityId, gameTime);
        markActivity(player, gameTime);
        awardMastery(
                player,
                HeroesEvolvedConfig.COMMON.powerUseMasteryAmount.get()
        );
        return true;
    }

    public static void awardHostileMobKill(
            ServerPlayer player,
            Monster monster
    ) {
        if (!hasAwakenedPower(player)) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        Map<EntityType<?>, Deque<Long>> killsByType = MOB_KILL_TIMES
                .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        Deque<Long> killTimes = killsByType.computeIfAbsent(
                monster.getType(),
                ignored -> new ArrayDeque<>()
        );
        long windowStart = gameTime - HeroesEvolvedConfig.COMMON
                .mobKillWindowTicks
                .get();

        while (!killTimes.isEmpty() && killTimes.peekFirst() <= windowStart) {
            killTimes.removeFirst();
        }

        int maximumRewardedKills = HeroesEvolvedConfig.COMMON
                .mobKillMaximumRewardedKills
                .get();

        if (killTimes.size() >= maximumRewardedKills) {
            return;
        }

        int fullRewardLimit = HeroesEvolvedConfig.COMMON
                .mobKillFullRewardLimit
                .get();
        int fullReward = HeroesEvolvedConfig.COMMON.mobKillMasteryAmount.get();
        int awardedMastery = killTimes.size() < fullRewardLimit
                ? fullReward
                : Math.max(1, fullReward / 2);

        killTimes.addLast(gameTime);
        markActivity(player, gameTime);
        awardMastery(player, awardedMastery);
    }

    public static void forgetPlayer(UUID playerId) {
        ACTIVE_TIME_TRACKERS.remove(playerId);
        POWER_USE_TIMES.remove(playerId);
        MOB_KILL_TIMES.remove(playerId);
    }

    private static boolean hasAwakenedPower(ServerPlayer player) {
        Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignment =
                PlayerAbilityService.getData(player).assignedPower();
        return assignment.map(entry -> entry.getValue().isUnlocked())
                .orElse(false);
    }

    private static void markActivity(ServerPlayer player, long gameTime) {
        ActiveTimeTracker tracker = ACTIVE_TIME_TRACKERS.computeIfAbsent(
                player.getUUID(),
                ignored -> new ActiveTimeTracker(
                        player.position(),
                        gameTime,
                        gameTime
                )
        );
        ACTIVE_TIME_TRACKERS.put(
                player.getUUID(),
                tracker.withActivity(player.position(), gameTime)
        );
    }

    private static void awardMastery(ServerPlayer player, long amount) {
        int levelBefore = PlayerProgressionService.getLevel(player);
        PlayerProgressionService.awardMastery(player, amount);
        int levelAfter = PlayerProgressionService.getLevel(player);

        if (levelAfter > levelBefore) {
            int energyGained = (levelAfter - levelBefore)
                    * HeroesEvolvedConfig.COMMON.energyPerLevel.get();
            PlayerEnergyService.restore(player, energyGained);
            player.sendSystemMessage(
                    Component.translatable("message.heroesevolved.level_up")
            );
        }

        PlayerPowerSyncService.sync(player);
    }

    private record ActiveTimeTracker(
            Vec3 lastPosition,
            long lastActivityGameTime,
            long lastAwardGameTime
    ) {
        private ActiveTimeTracker withActivity(Vec3 position, long gameTime) {
            return new ActiveTimeTracker(
                    position,
                    gameTime,
                    lastAwardGameTime
            );
        }

        private ActiveTimeTracker withAward(long gameTime) {
            return new ActiveTimeTracker(
                    lastPosition,
                    lastActivityGameTime,
                    gameTime
            );
        }
    }
}
