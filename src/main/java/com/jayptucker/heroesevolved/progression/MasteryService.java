package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Awards Mastery, which drives the single shared power level.
 *
 * <p>Balance rests on energy. Power use earns Mastery in proportion to the
 * energy it actually spent, so a 120-energy Earthquake earns eight times a
 * 15-energy Shockwave, and spamming cheap actions earns no faster than the
 * player's energy regenerates. Combat rewards scale with the toughness of
 * the hostile mob defeated, with diminishing returns for farming one type.
 * A small trickle rewards active, non-AFK play.</p>
 *
 * <p>The short-lived trackers intentionally reset after a server restart;
 * only the player's earned Mastery is persistent.</p>
 */
public final class MasteryService {
    private static final long ACTIVE_WINDOW_TICKS = 20L * 30L;
    private static final double MINIMUM_ACTIVE_MOVEMENT_SQUARED = 4.0D;

    private static final Map<UUID, ActiveTimeTracker> ACTIVE_TIME_TRACKERS =
            new HashMap<>();
    private static final Map<UUID, Map<EntityType<?>, Deque<Long>>>
            MOB_KILL_TIMES = new HashMap<>();
    // Fractional Mastery carried between small energy spends, so a power
    // that drains 1 energy at a time still earns its fair share.
    private static final Map<UUID, Double> ENERGY_MASTERY_REMAINDER =
            new HashMap<>();

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

    /**
     * Records that a power was used, which counts as activity for the
     * active-play trickle. Mastery itself comes from the energy spent; see
     * {@link #awardEnergySpent}.
     */
    public static void markPowerUse(
            ServerPlayer player,
            ResourceLocation abilityId
    ) {
        if (hasAwakenedPower(player)) {
            markActivity(player, player.serverLevel().getGameTime());
        }
    }

    /** The main Mastery source: energy genuinely spent on a power. */
    public static void awardEnergySpent(ServerPlayer player, int energySpent) {
        // Test-mode unlimited energy spends nothing, so it earns nothing.
        if (energySpent <= 0 || !hasAwakenedPower(player)
                || PlayerEnergyService.hasUnlimitedEnergy(player)) {
            return;
        }

        double earned = energySpent
                * HeroesEvolvedConfig.COMMON.masteryPerEnergySpent.get()
                + ENERGY_MASTERY_REMAINDER.getOrDefault(player.getUUID(), 0.0D);
        long whole = (long) Math.floor(earned);
        ENERGY_MASTERY_REMAINDER.put(player.getUUID(), earned - whole);

        markActivity(player, player.serverLevel().getGameTime());
        if (whole > 0) {
            awardMastery(player, whole);
        }
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

        // Tougher foes are worth more: a zombie (20 health) earns the base
        // rate, an enderman twice that, a Warden twenty-five times.
        double toughness = Math.max(1.0D, monster.getMaxHealth() / 10.0D);
        int fullReward = (int) Math.round(toughness * HeroesEvolvedConfig.COMMON
                .mobKillMasteryPerTenHealth.get());
        int fullRewardLimit = HeroesEvolvedConfig.COMMON
                .mobKillFullRewardLimit
                .get();
        int awardedMastery = killTimes.size() < fullRewardLimit
                ? fullReward
                : Math.max(1, fullReward / 2);

        killTimes.addLast(gameTime);
        markActivity(player, gameTime);
        awardMastery(player, awardedMastery);
    }

    /** Operator grant from the admin menu; obeys level-up rules. */
    public static void grantMastery(ServerPlayer player, long amount) {
        if (amount > 0) {
            awardMastery(player, amount);
        }
    }

    public static void forgetPlayer(UUID playerId) {
        ACTIVE_TIME_TRACKERS.remove(playerId);
        MOB_KILL_TIMES.remove(playerId);
        ENERGY_MASTERY_REMAINDER.remove(playerId);
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
            celebrateLevelUp(player, levelAfter);
        }

        PlayerPowerSyncService.sync(player);
    }

    /**
     * A level-up is a milestone: a title, a full energy surge, and a list
     * of everything the new level unlocked.
     */
    public static void celebrateLevelUp(ServerPlayer player, int newLevel) {
        PlayerEnergyService.restore(
                player, PlayerEnergyService.getMaximumEnergy(player)
        );

        List<AbilityAction> unlocked = actionsUnlockedAt(player, newLevel);
        Component subtitle = unlocked.isEmpty()
                ? Component.literal("Your power grows stronger")
                : Component.literal("Unlocked: ").append(Component.translatable(
                        unlocked.getFirst().definition().displayNameKey()));

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("Level " + newLevel)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                subtitle.copy().withStyle(ChatFormatting.GOLD)
        ));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 0.8F, 1.0F);

        player.sendSystemMessage(Component.translatable(
                "message.heroesevolved.level_up"
        ));
        for (AbilityAction action : unlocked) {
            player.sendSystemMessage(Component.literal(" + ")
                    .append(Component.translatable(
                            action.definition().displayNameKey()))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static List<AbilityAction> actionsUnlockedAt(
            ServerPlayer player,
            int level
    ) {
        return PlayerAbilityService.getData(player).assignedPower()
                .map(entry -> AbilityRegistry.ABILITIES.get(entry.getKey()))
                .map(Ability::actions)
                .map(actions -> actions.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .filter(action ->
                                action.definition().unlockLevel() == level)
                        .toList())
                .orElse(List.of());
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
