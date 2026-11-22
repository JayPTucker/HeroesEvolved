package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

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
 * little, with hits and spending sharing a six-Mastery allowance per active minute.
 * Exploration and active play are independent sources; repeated local kills stop paying.
 * The reward ledger persists, so a farm cannot reset it by reconnecting.</p>
 *
 * <p>Only timestamps and fractional payments reset on logout. No unused offline time is rewarded.</p>
 */
public final class MasteryService {
    private static final long ACTIVE_WINDOW_TICKS = 20L * 30L;
    private static final double MINIMUM_ACTIVE_MOVEMENT_SQUARED = 4.0D;
    /**
     * A player at the keyboard turns their view. Water loops, minecart tracks and autoclickers move a body or
     * break blocks at a fixed look, so movement and building only count as play when the view has turned too.
     */
    private static final float MINIMUM_LOOK_DEGREES = 2.0F;
    /** A hit or defeat within this long of using a power counts as done with it. */
    public static final long POWER_WINDOW_TICKS = 60L;
    private static final long HIT_INTERVAL_TICKS = 10L;
    private static final int KILL_POWER_BONUS_PERCENT = 50;
    public static final int EXPLORATION_MASTERY = 30, EXPLORATION_SIZE = 128, ENCOUNTER_SIZE = 256;
    private static final String KILL_REWARDED = "heroesevolved_mastery_rewarded";
    private static final String HIT_REWARDED = "heroesevolved_mastery_hit_damage";

    private static final Map<UUID, Long> LAST_POWER_USE = new HashMap<>();
    private static final Map<UUID, Long> LAST_HIT_AWARD = new HashMap<>();

    private static final Map<UUID, ActiveTimeTracker> ACTIVE_TIME_TRACKERS =
            new HashMap<>();
    // Fractional Mastery carried between small energy spends, so a power
    // that drains 1 energy at a time still earns its fair share.
    private static final Map<UUID, Double> ENERGY_MASTERY_REMAINDER =
            new HashMap<>();

    private MasteryService() {
    }

    public static void tickActiveTime(ServerPlayer player) {
        if (!hasAwakenedPower(player) || !player.isAlive() || player.isSpectator()) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        ActiveTimeTracker tracker = ACTIVE_TIME_TRACKERS.computeIfAbsent(
                player.getUUID(),
                ignored -> new ActiveTimeTracker(
                        player.position(),
                        player.getYRot(),
                        player.getXRot(),
                        gameTime - ACTIVE_WINDOW_TICKS - 1,
                        gameTime
                )
        );

        boolean moved = player.position().distanceToSqr(tracker.lastPosition())
                >= MINIMUM_ACTIVE_MOVEMENT_SQUARED && tracker.looked(player);
        if (moved) {
            tracker = tracker.withActivity(player, gameTime);
        }

        int intervalTicks = HeroesEvolvedConfig.COMMON
                .activeMasteryIntervalTicks
                .get();

        // Count only observed active ticks. A reconnect or a long AFK gap cannot cash in wall-clock time.
        long elapsed = Math.clamp(gameTime - tracker.lastAwardGameTime(), 0, 20);
        if (gameTime - tracker.lastActivityGameTime() <= ACTIVE_WINDOW_TICKS && elapsed > 0) {
            PlayerProgressionData data = PlayerProgressionService.getData(player);
            boolean interval = data.activeTicks() / intervalTicks != (data.activeTicks() + elapsed) / intervalTicks;
            player.setData(ModDataAttachments.PLAYER_PROGRESSION, data.active(elapsed, interval));
            if (interval) awardMastery(player, HeroesEvolvedConfig.COMMON.activeMasteryAmount.get());
        }
        tracker = tracker.withAward(gameTime);

        // One new region per active minute, never a reward for repeatedly crossing a border or teleporting back.
        if (moved) explore(player);

        ACTIVE_TIME_TRACKERS.put(player.getUUID(), tracker);
    }

    /**
     * Records that a power was used. Casting into empty air does not keep the active-play clock running.
     * Practice Mastery comes from the energy spent; see
     * {@link #awardEnergySpent}.
     */
    public static void markPowerUse(
            ServerPlayer player,
            ResourceLocation abilityId
    ) {
        if (hasAwakenedPower(player)) {
            long now = player.serverLevel().getGameTime();
            LAST_POWER_USE.put(player.getUUID(), now);
        }
    }

    /** True if the player used their power in the last three seconds. */
    public static boolean usedPowerRecently(ServerPlayer player) {
        Long last = LAST_POWER_USE.get(player.getUUID());
        return last != null && player.serverLevel().getGameTime() - last <= POWER_WINDOW_TICKS;
    }

    /**
     * Fresh damage to an eligible enemy while the power was in use: one Mastery per two damage,
     * at most twice a second and sharing the six-point practice allowance. PvP gives no Mastery.
     */
    public static void awardPowerHit(ServerPlayer player, LivingEntity target, float damage) {
        if (damage <= 0 || !hasAwakenedPower(player) || !usedPowerRecently(player) || !rewardable(target)
                || localKills(player, target) >= HeroesEvolvedConfig.COMMON.mobKillMaximumRewardedKills.get()) return;
        long now = player.serverLevel().getGameTime();
        Long last = LAST_HIT_AWARD.get(player.getUUID());
        if (last != null && now - last < HIT_INTERVAL_TICKS) return;
        LAST_HIT_AWARD.put(player.getUUID(), now);
        // Healing the same captive cannot generate fresh practice. The budget is shared by all attackers.
        float already = target.getPersistentData().getFloat(HIT_REWARDED);
        float fresh = Math.max(0, Math.min(damage, target.getMaxHealth() - already));
        target.getPersistentData().putFloat(HIT_REWARDED, already + fresh);
        if (fresh > 0) {
            markActivity(player, now);
            awardPractice(player, Math.clamp(Math.round(fresh / 2.0F), 1, 6));
        }
    }

    /** Saving someone with your power: healing a dying ally, catching a falling one. */
    public static void awardRescue(ServerPlayer player, long amount) {
        if (amount <= 0 || !hasAwakenedPower(player)) return;
        markPowerUse(player, null);
        markActivity(player, player.serverLevel().getGameTime());
        awardMastery(player, amount);
    }

    /** Bounded practice: energy genuinely spent on a power, sharing the hit allowance. */
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

        if (whole > 0) {
            awardPractice(player, whole);
        }
    }

    private static void awardPractice(ServerPlayer player, long amount) {
        PlayerProgressionData data = PlayerProgressionService.getData(player);
        int earned = (int) Math.min(amount, data.practiceLeft());
        if (earned <= 0) return;
        player.setData(ModDataAttachments.PLAYER_PROGRESSION, data.practice(earned));
        awardMastery(player, earned);
    }

    private static String region(LivingEntity entity, int size) {
        return entity.level().dimension().location() + "|" + Math.floorDiv(entity.getBlockX(), size)
                + "|" + Math.floorDiv(entity.getBlockZ(), size);
    }

    /** Claims the current region only when active movement and a fresh exploration allowance meet. */
    private static void explore(ServerPlayer player) {
        PlayerProgressionData data = PlayerProgressionService.getData(player);
        String place = region(player, EXPLORATION_SIZE);
        if (!data.explorationReady() || data.explored().contains(place) || data.explored().size() >= PlayerProgressionData.MAX_PLACES) return;
        player.setData(ModDataAttachments.PLAYER_PROGRESSION, data.explore(place));
        awardMastery(player, EXPLORATION_MASTERY);
    }

    private static String encounter(LivingEntity target) {
        return region(target, ENCOUNTER_SIZE) + "|" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
    }

    private static int localKills(ServerPlayer player, LivingEntity target) {
        return PlayerProgressionService.getData(player).encounters().getOrDefault(encounter(target), 0);
    }

    private static boolean boss(LivingEntity target) {
        Gift gift = Gift.read(target);
        return target.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)
                || gift != null && gift.evil() && gift.rarity().boss();
    }

    /** One eligibility rule for hits, kills and the gifted reward callback. Spawn provenance is saved by NeoForge. */
    public static boolean rewardable(LivingEntity target) {
        if (target instanceof ServerPlayer || target.getPersistentData().getBoolean(GiftedMobs.FOLLOWER)
                || target.getPersistentData().contains("heroesevolved_rift_beast")) return false;
        Gift gift = Gift.read(target);
        if (!(target instanceof Enemy) && !(gift != null && gift.evil()) && !boss(target)) return false;
        if (target instanceof Mob mob && mob.getSpawnType() != null) {
            return switch (mob.getSpawnType().name()) {
                case "SPAWNER", "TRIAL_SPAWNER", "SPAWN_EGG", "DISPENSER", "COMMAND", "BREEDING", "REINFORCEMENT" -> false;
                case "MOB_SUMMONED" -> boss(target);
                case "CONVERSION" -> gift != null && gift.evil() && gift.origin() == Gift.Origin.RENEGADE;
                default -> true;
            };
        }
        return true;
    }

    public static void awardHostileMobKill(
            ServerPlayer player,
            LivingEntity monster
    ) {
        if (!hasAwakenedPower(player) || !rewardable(monster) || monster.getPersistentData().getBoolean(KILL_REWARDED)) {
            return;
        }
        PlayerProgressionData data = PlayerProgressionService.getData(player);
        String place = encounter(monster);
        int kills = data.encounters().getOrDefault(place, 0);
        if (kills >= HeroesEvolvedConfig.COMMON.mobKillMaximumRewardedKills.get()
                || !data.encounters().containsKey(place) && data.encounters().size() >= PlayerProgressionData.MAX_PLACES) return;
        // The mob's flag also prevents the death event and gifted loot callback from paying twice.
        monster.getPersistentData().putBoolean(KILL_REWARDED, true);
        player.setData(ModDataAttachments.PLAYER_PROGRESSION, data.encounter(place));

        // Tougher foes are worth more: a zombie (20 health) earns the base
        // rate, an enderman twice that, a Warden twenty-five times.
        double toughness = Math.max(1.0D, monster.getMaxHealth() / 10.0D);
        int fullReward = (int) Math.round(toughness * HeroesEvolvedConfig.COMMON
                .mobKillMasteryPerTenHealth.get());
        int fullRewardLimit = HeroesEvolvedConfig.COMMON
                .mobKillFullRewardLimit
                .get();
        Gift gift = Gift.read(monster);
        if (gift != null && gift.evil()) fullReward += 10 << gift.rarity().ordinal();
        if (boss(monster)) fullReward = Math.max(120, fullReward * 4);
        int awardedMastery = kills < fullRewardLimit
                ? fullReward
                : fullReward / 2;
        // Defeating it with your power is worth half again, and counts
        // toward the Evolved trial.
        if (usedPowerRecently(player)) {
            awardedMastery += awardedMastery * KILL_POWER_BONUS_PERCENT / 100;
            EvolutionTrials.defeated(player);
        }

        markActivity(player, player.serverLevel().getGameTime());
        if (awardedMastery > 0) awardMastery(player, awardedMastery);
    }

    /** Operator grant from the admin menu; obeys level-up rules. */
    public static void grantMastery(ServerPlayer player, long amount) {
        if (amount > 0) {
            awardMastery(player, amount);
        }
    }

    public static void forgetPlayer(UUID playerId) {
        ACTIVE_TIME_TRACKERS.remove(playerId);
        ENERGY_MASTERY_REMAINDER.remove(playerId);
        LAST_POWER_USE.remove(playerId);
        LAST_HIT_AWARD.remove(playerId);
    }

    /**
     * Real building/interaction counts as playtime, without granting practice or combat rewards. An autoclicker
     * at a fixed look (a cobblestone generator, a lever) is not play, so the view must have turned since last time.
     */
    public static void activity(ServerPlayer player) {
        if (!hasAwakenedPower(player)) return;
        ActiveTimeTracker tracker = ACTIVE_TIME_TRACKERS.get(player.getUUID());
        if (tracker == null || tracker.looked(player)) markActivity(player, player.serverLevel().getGameTime());
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
                        player.getYRot(),
                        player.getXRot(),
                        gameTime,
                        gameTime
                )
        );
        ACTIVE_TIME_TRACKERS.put(
                player.getUUID(),
                tracker.withActivity(player, gameTime)
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
            float lastYaw,
            float lastPitch,
            long lastActivityGameTime,
            long lastAwardGameTime
    ) {
        private ActiveTimeTracker withActivity(ServerPlayer player, long gameTime) {
            return new ActiveTimeTracker(
                    player.position(),
                    player.getYRot(),
                    player.getXRot(),
                    gameTime,
                    lastAwardGameTime
            );
        }

        private ActiveTimeTracker withAward(long gameTime) {
            return new ActiveTimeTracker(
                    lastPosition,
                    lastYaw,
                    lastPitch,
                    lastActivityGameTime,
                    gameTime
            );
        }

        /** Has the view turned since the last counted activity? */
        private boolean looked(ServerPlayer player) {
            return Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - lastYaw))
                    + Math.abs(player.getXRot() - lastPitch) >= MINIMUM_LOOK_DEGREES;
        }
    }
}
