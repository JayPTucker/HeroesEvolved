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
import net.minecraft.world.entity.LivingEntity;
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
 * <p>Mastery comes from using a power for something. Hitting an enemy with
 * your power, defeating one within three seconds of using it, and saving an
 * ally earn the most; defeats scale with the foe's toughness, with
 * diminishing returns for farming one type. Stamina spent still earns a
 * little (half a point per point by default), so practice counts but never
 * outpaces energy regeneration. Story notes and a small trickle for active,
 * non-AFK play round it out.</p>
 *
 * <p>The short-lived trackers intentionally reset after a server restart;
 * only the player's earned Mastery is persistent.</p>
 */
public final class MasteryService {
    private static final long ACTIVE_WINDOW_TICKS = 20L * 30L;
    private static final double MINIMUM_ACTIVE_MOVEMENT_SQUARED = 4.0D;
    /** A hit or defeat within this long of using a power counts as done with it. */
    public static final long POWER_WINDOW_TICKS = 60L;
    private static final long HIT_INTERVAL_TICKS = 10L;
    private static final int KILL_POWER_BONUS_PERCENT = 50;

    private static final Map<UUID, Long> LAST_POWER_USE = new HashMap<>();
    private static final Map<UUID, Long> LAST_HIT_AWARD = new HashMap<>();

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
            long now = player.serverLevel().getGameTime();
            LAST_POWER_USE.put(player.getUUID(), now);
            markActivity(player, now);
        }
    }

    /** True if the player used their power in the last three seconds. */
    public static boolean usedPowerRecently(ServerPlayer player) {
        Long last = LAST_POWER_USE.get(player.getUUID());
        return last != null && player.serverLevel().getGameTime() - last <= POWER_WINDOW_TICKS;
    }

    /**
     * A hit on an enemy (a hostile mob, or another player) landed while the
     * power was in use: one Mastery per two damage, up to 6, at most twice a second.
     */
    public static void awardPowerHit(ServerPlayer player, float damage) {
        if (damage <= 0 || !hasAwakenedPower(player) || !usedPowerRecently(player)) return;
        long now = player.serverLevel().getGameTime();
        Long last = LAST_HIT_AWARD.get(player.getUUID());
        if (last != null && now - last < HIT_INTERVAL_TICKS) return;
        LAST_HIT_AWARD.put(player.getUUID(), now);
        awardMastery(player, Math.clamp(Math.round(damage / 2.0F), 1, 6));
    }

    /** Saving someone with your power: healing a dying ally, catching a falling one. */
    public static void awardRescue(ServerPlayer player, long amount) {
        if (amount <= 0 || !hasAwakenedPower(player)) return;
        markPowerUse(player, null);
        awardMastery(player, amount);
    }

    /** The main Mastery source: energy genuinely spent on a power. */
    public static void awardEnergySpent(ServerPlayer player, int energySpent) {
        // Test-mode unlimited energy spends nothing, so it earns nothing.
        if (energySpent <= 0 || !hasAwakenedPower(player)
                || PlayerEnergyService.hasUnlimitedEnergy(player)) {
            return;
        }

        LAST_POWER_USE.put(player.getUUID(), player.serverLevel().getGameTime());
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
            LivingEntity monster
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
        // Defeating it with your power is worth half again, and counts
        // toward the Evolved trial.
        if (usedPowerRecently(player)) {
            awardedMastery += awardedMastery * KILL_POWER_BONUS_PERCENT / 100;
            EvolutionTrials.defeated(player);
        }

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
        LAST_POWER_USE.remove(playerId);
        LAST_HIT_AWARD.remove(playerId);
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
        int levelBefore = PlayerProgressionService.getPowerLevel(player);
        PlayerProgressionService.awardMastery(player, amount);
        int levelAfter = PlayerProgressionService.getPowerLevel(player);

        if (levelAfter > levelBefore) {
            celebrateLevelUp(player, levelBefore, levelAfter);
        }
        EvolutionTrials.check(player);

        PlayerPowerSyncService.sync(player);
    }

    /**
     * A level-up is a milestone: a title with the stage, a full energy
     * surge, the skill points it gave, and everything the new level unlocked.
     */
    public static void celebrateLevelUp(ServerPlayer player, int fromLevel, int toLevel) {
        PlayerEnergyService.restore(
                player, PlayerEnergyService.getMaximumEnergy(player)
        );

        List<AbilityAction> unlocked = new java.util.ArrayList<>();
        for (int tier = PowerLevels.tierFor(fromLevel) + 1; tier <= PowerLevels.tierFor(toLevel); tier++) {
            unlocked.addAll(actionsUnlockedAt(player, tier));
        }
        int points = PowerLevels.points(toLevel) - PowerLevels.points(fromLevel);
        PowerLevels.Stage stage = PowerLevels.stage(toLevel);
        boolean newStage = stage != PowerLevels.stage(fromLevel);

        Component subtitle;
        if (!unlocked.isEmpty()) {
            subtitle = Component.literal("Unlocked: ").append(Component.translatable(
                    unlocked.getFirst().definition().displayNameKey()));
        } else if (points > 0) {
            subtitle = Component.literal("+" + points + " skill point" + (points == 1 ? "" : "s"));
        } else {
            subtitle = Component.literal(stage.title);
        }

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("Level " + toLevel)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                subtitle.copy().withStyle(ChatFormatting.GOLD)
        ));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, newStage ? 1.0F : 0.6F, newStage ? 0.9F : 1.2F);

        player.sendSystemMessage(Component.translatable(
                "message.heroesevolved.level_up"
        ));
        player.sendSystemMessage(Component.literal("Level " + toLevel + " \u00b7 " + stage.title
                        + (newStage ? " (new stage)" : ""))
                .withStyle(ChatFormatting.RED));
        if (points > 0) {
            player.sendSystemMessage(Component.literal(" + " + points + " skill point"
                            + (points == 1 ? "" : "s") + " to spend in your skill tree")
                    .withStyle(ChatFormatting.AQUA));
        }
        for (AbilityAction action : unlocked) {
            player.sendSystemMessage(Component.literal(" + ")
                    .append(Component.translatable(
                            action.definition().displayNameKey()))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (toLevel == PowerLevels.EVOLVED - 1) {
            PlayerAbilityService.getData(player).assignedPower().ifPresent(entry -> player.sendSystemMessage(
                    Component.literal("One level left. To Evolve: "
                                    + EvolutionTrials.trial(entry.getKey().getPath()).description() + ".")
                            .withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }

    private static List<AbilityAction> actionsUnlockedAt(
            ServerPlayer player,
            int tier
    ) {
        return PlayerAbilityService.getData(player).assignedPower()
                .map(entry -> AbilityRegistry.ABILITIES.get(entry.getKey()))
                .map(Ability::actions)
                .map(actions -> actions.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .filter(action ->
                                action.definition().unlockLevel() == tier)
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
