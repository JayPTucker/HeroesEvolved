package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rewind: undo the last fifteen seconds of your own timeline. You snap back to
 * where you stood, nearby changed blocks are restored, lost health returns,
 * and flames that caught you since are put out.
 *
 * <p>It can undo your own death. Your history survives dying, and for fifteen seconds after a
 * death you can rewind past it: from the death screen, or with the Rewind key once you've
 * respawned. You come back where you stood fifteen seconds before you pressed it, with the
 * inventory and experience you had then, and what you dropped when you died is gone again.</p>
 */
public final class TimeRewindService {
    private static final int SAMPLE_INTERVAL_TICKS = 5;
    public static final int REWIND_SECONDS = 15;
    private static final int REWIND_TICKS = 20 * REWIND_SECONDS;
    private static final Map<UUID, Deque<Sample>> HISTORY = new HashMap<>();
    /** A recent death that can still be rewound: when, where, and whether the death screen asked for it. */
    private static final class Death {
        final long time; final ResourceKey<Level> dimension; boolean requested;
        Death(long time, ResourceKey<Level> dimension) { this.time = time; this.dimension = dimension; }
    }
    private static final Map<UUID, Death> DEATHS = new HashMap<>();
    private static final java.util.Set<UUID> PENDING_DEATH_REWINDS = new java.util.HashSet<>();
    public static final ResourceLocation REWIND_ID = ResourceLocation.fromNamespaceAndPath("heroesevolved", "time_rewind");
    /** True while a death rewind carries the player across dimensions; the dimension change must not erase their history. */
    private static boolean crossingForRewind;

    private TimeRewindService() {
    }

    /** Records recent states for every awakened Time Manipulation user. */
    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        // The death screen's Rewind runs on the tick after the respawn it asked for.
        for (UUID id : List.copyOf(PENDING_DEATH_REWINDS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            PENDING_DEATH_REWINDS.remove(id);
            if (player != null && player.isAlive() && rewind(player)) payForDeathRewind(player);
        }
        DEATHS.values().removeIf(death -> gameTime - death.time > REWIND_TICKS);
        if (gameTime % SAMPLE_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            record(player, gameTime);
            showMarker(player);
        }
        TimeRewindBlocks.prune(gameTime);
        TimeRewindEntities.prune(gameTime);
    }

    static void record(ServerPlayer player, long gameTime) {
        if (!hasAwakenedTimeManipulation(player)) {
            forget(player);
            return;
        }
        // A dead body records nothing, but its history is kept: the death can still be rewound.
        if (!player.isAlive()) return;
        Deque<Sample> samples = HISTORY.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        long blockRevision = TimeRewindBlocks.track(player);
        samples.addLast(new Sample(gameTime, player.level().dimension(), player.position(),
                player.getYRot(), player.getXRot(), player.getHealth(), player.isOnFire(), blockRevision,
                TimeRewindEntities.capture(player), TimeRewindContainers.capture(player)));
        while (!samples.isEmpty() && samples.getFirst().gameTime < gameTime - REWIND_TICKS - 20) {
            samples.removeFirst();
        }
    }

    public static boolean canRewind(ServerPlayer player) {
        Sample target = target(player);
        if (target != null && acrossDeath(player, target) && !target.dimension.equals(player.level().dimension()))
            return target.entities.complete() && target.containers.complete();
        return target != null && target.entities.complete() && target.containers.complete()
                && TimeRewindBlocks.plan(player, target.blockRevision) != null;
    }

    // ------------------------------------------------------------------
    // Rewinding a death
    // ------------------------------------------------------------------

    /**
     * The ghost only you can see: where Rewind would put you if you pressed it now. Shown while Rewind
     * is unlocked and ready and the spot is more than a couple of blocks away.
     */
    private static void showMarker(ServerPlayer player) {
        if (!player.isAlive() || com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(player) < 4
                || com.jayptucker.heroesevolved.cooldown.CooldownService.isOnCooldown(player, REWIND_ID)) return;
        Sample target = findTarget(HISTORY.get(player.getUUID()), player.getServer().overworld().getGameTime(), player.level().dimension(), false);
        if (target == null || target.position.distanceToSqr(player.position()) < 4) return;
        com.jayptucker.heroesevolved.network.TimeGhostPayload.marker(player, target.position, target.yaw);
    }

    /** Before skill-tree and balance adjustments: four minutes at Level 4, two and a half from Level 5. */
    public static int cooldownTicks(int level) {
        var config = com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;
        return 20 * (level >= 5 ? config.timeRewindCooldownLevel5Seconds.get() : config.timeRewindCooldownLevel4Seconds.get());
    }

    /**
     * The death screen's rewind costs what the key does, the full stamina cost and the full cooldown.
     * A body just back from the dead rarely has the stamina, so it overexerts rather than failing.
     */
    private static void payForDeathRewind(ServerPlayer player) {
        int level = com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(player);
        int cost = com.jayptucker.heroesevolved.skills.SkillTreeService.activationCost(player, ModAbilities.TIME_MANIPULATION_ID,
                com.jayptucker.heroesevolved.ability.PowerBalance.energy(REWIND_ID,
                        com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.timeRewindEnergyCost.get()));
        int spent = com.jayptucker.heroesevolved.energy.PlayerEnergyService.getEnergy(player) >= cost
                ? com.jayptucker.heroesevolved.energy.PlayerEnergyService.consumeUpTo(player, cost)
                : com.jayptucker.heroesevolved.energy.OverexertionService.apply(player, cost);
        MasteryService.awardEnergySpent(player, spent);
        int cooldown = com.jayptucker.heroesevolved.skills.SkillTreeService.cooldown(player, ModAbilities.TIME_MANIPULATION_ID,
                com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(REWIND_ID, cooldownTicks(level)));
        com.jayptucker.heroesevolved.cooldown.CooldownService.startCooldown(player, REWIND_ID, cooldown);
        com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(player);
    }

    /** A player died. If they could rewind right now, the death stays rewindable for fifteen seconds. */
    public static void died(ServerPlayer player) {
        if (!hasAwakenedTimeManipulation(player) || HISTORY.get(player.getUUID()) == null
                || com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(player) < 4
                || com.jayptucker.heroesevolved.cooldown.CooldownService.isOnCooldown(player, REWIND_ID)
                || com.jayptucker.heroesevolved.time.intro.TimeIntroService.isVisiting(player)
                || com.jayptucker.heroesevolved.time.intro.TimeIntroService.isCity(player.level())) return;
        long now = player.getServer().overworld().getGameTime();
        DEATHS.put(player.getUUID(), new Death(now, player.level().dimension()));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.jayptucker.heroesevolved.network.DeathRewindPayload(REWIND_TICKS));
    }

    /** The death screen's Rewind button: rewind as soon as the player has respawned. */
    public static void requestDeathRewind(ServerPlayer player) {
        Death death = DEATHS.get(player.getUUID());
        if (death != null) death.requested = true;
    }

    /** A new life. Keep the old history while its death can still be rewound; otherwise start fresh. */
    public static void respawned(ServerPlayer player) {
        Death death = DEATHS.get(player.getUUID());
        if (death == null) { forget(player); return; }
        if (death.requested) PENDING_DEATH_REWINDS.add(player.getUUID());
    }

    public static boolean canRewindDeath(ServerPlayer player) {
        return DEATHS.containsKey(player.getUUID());
    }

    /** Is this rewind target from before a death that can still be undone? */
    private static boolean acrossDeath(ServerPlayer player, Sample target) {
        Death death = DEATHS.get(player.getUUID());
        return death != null && target.gameTime <= death.time;
    }

    public static boolean rewind(ServerPlayer player) {
        Sample target = target(player);
        // Undoing a death can cross dimensions: go back to the world you died in first.
        if (target != null && acrossDeath(player, target) && !target.dimension.equals(player.level().dimension())) {
            ServerLevel destination = player.getServer().getLevel(target.dimension);
            if (destination == null) target = null;
            else {
                crossingForRewind = true;
                try {
                    player.teleportTo(destination, target.position.x, target.position.y, target.position.z, target.yaw, target.pitch);
                } finally { crossingForRewind = false; }
            }
        }
        boolean undoingDeath = target != null && acrossDeath(player, target);
        if (target == null) {
            player.displayClientMessage(Component.literal(
                    "Record 15 seconds in this dimension before rewinding."), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (!target.entities.complete() || !target.containers.complete()) {
            player.displayClientMessage(Component.literal("There were too many entities to record a complete rewind. Wait for a complete history."), true);
            return false;
        }
        TimeRewindBlocks.Plan blocks = TimeRewindBlocks.plan(player, target.blockRevision);
        if (blocks == null) {
            player.displayClientMessage(Component.literal(
                    "The recent timeline is still being recorded. Wait 15 seconds."), true);
            return false;
        }
        AABB destinationBox = player.getBoundingBox().move(target.position.subtract(player.position()));
        if (!TimeRewindBlocks.canOccupy(player, destinationBox, blocks)) {
            player.displayClientMessage(Component.literal(
                    "Your position from 15 seconds ago is now obstructed."), true);
            return false;
        }
        Deque<Sample> samples = HISTORY.get(player.getUUID());
        Vec3 from = player.position();
        long gameTime = level.getServer().overworld().getGameTime();

        // Trace the path being unwound, newest moments first.
        List<Sample> path = new ArrayList<>(samples);
        Vec3 previous = from;
        for (int index = path.size() - 1; index >= 0; index--) {
            Sample sample = path.get(index);
            if (sample.gameTime < target.gameTime) {
                break;
            }
            // History may cross dimensions; never draw another world's positions here.
            if (!sample.dimension.equals(level.dimension())) {
                previous = null;
                continue;
            }
            if (previous != null && previous.distanceToSqr(sample.position) > 0.01D) {
                AbilityEffects.send(level, Kind.TEMPORAL_REWIND, previous, sample.position,
                        (gameTime - sample.gameTime) * 20.0D / REWIND_TICKS);
            }
            previous = sample.position;
        }
        AbilityEffects.send(level, Kind.TEMPORAL_BURST, from, from.add(0.0D, 1.0D, 0.0D), 2.0D);
        // Ghosts of you along the way, appearing newest first: you watch yourself run backwards.
        List<Sample> walked = new ArrayList<>();
        for (int index = path.size() - 1; index >= 0; index--) {
            Sample sample = path.get(index);
            if (sample.gameTime < target.gameTime) break;
            if (sample.dimension.equals(level.dimension())) walked.add(sample);
        }
        int echoes = Math.min(6, walked.size());
        for (int i = 0; i < echoes; i++) {
            Sample sample = walked.get(i * walked.size() / Math.max(1, echoes));
            if (sample.position.distanceToSqr(from) < 1.0D) continue;
            com.jayptucker.heroesevolved.network.TimeGhostPayload.echo(player, sample.position, sample.yaw, 18, i * 2);
        }

        TimeStopService.stop(player);
        TimeSlowService.stop(player);
        SpaceTimeService.forget(player);
        TimeRewindBlocks.restore(player, blocks);
        TimeRewindContainers.restore(player, target.containers);
        TimeRewindEntities.restore(player, target.entities, target.gameTime);
        player.teleportTo(level, target.position.x, target.position.y,
                target.position.z, target.yaw, target.pitch);
        player.resetFallDistance();
        if (target.health > player.getHealth()) {
            player.setHealth(Math.min(player.getMaxHealth(), target.health));
        }
        if (!target.onFire) {
            player.clearFire();
        }

        Vec3 to = player.position();
        AbilityEffects.send(level, Kind.TIME_REWIND_ARRIVE, to, to.add(0.0D, 1.0D, 0.0D), 2.5D);
        level.playSound(null, to.x, to.y, to.z, ModSounds.TIME_UNSLOW.get(),
                SoundSource.PLAYERS, 0.9F, 0.8F);
        ScreenTintPayload.send(player, 0x60C0A060, 10, ScreenTintPayload.Style.FLASH);
        player.displayClientMessage(Component.literal(undoingDeath
                ? "Time folds back 15 seconds. Your death never happened." : "Time folds back 15 seconds.").withStyle(ChatFormatting.GOLD), true);
        if (undoingDeath) {
            DEATHS.remove(player.getUUID());
            com.jayptucker.heroesevolved.progression.EvolutionTrials.advance(player, ModAbilities.TIME_MANIPULATION_ID);
            // Coming back from the dead: a hardcore respawn would otherwise leave them spectating.
            if (player.getServer().isHardcore() && player.isSpectator()) player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }

        // The rewound seconds no longer happened; you cannot rewind into them.
        samples.clear();
        TimeRewindBlocks.forget(player.getUUID());
        TimeRewindEntities.forget(player.getUUID());
        MasteryService.markPowerUse(player, ModAbilities.TIME_MANIPULATION_ID);
        TemporalStoryService.reach(player, TemporalStoryService.REWRITE);
        return true;
    }

    public static void forget(ServerPlayer player) {
        if (crossingForRewind) return;
        DEATHS.remove(player.getUUID());
        PENDING_DEATH_REWINDS.remove(player.getUUID());
        HISTORY.remove(player.getUUID());
        TimeRewindBlocks.forget(player.getUUID());
        TimeRewindEntities.forget(player.getUUID());
    }

    public static void clear() {
        HISTORY.clear();
        DEATHS.clear();
        PENDING_DEATH_REWINDS.clear();
        TimeRewindBlocks.clear();
        TimeRewindEntities.clear();
    }

    /** Use the same clock for recording and selecting history, in every dimension. */
    private static Sample target(ServerPlayer player) {
        long now = player.serverLevel().getServer().overworld().getGameTime();
        Deque<Sample> samples = HISTORY.get(player.getUUID());
        Death death = DEATHS.get(player.getUUID());
        if (death != null) {
            // After a death the history may have stopped (still dead) or moved on (respawned elsewhere):
            // the target is still fifteen seconds ago, in whatever world that was.
            Sample before = findTarget(samples, now, null, true);
            if (before != null && before.gameTime <= death.time) return before;
        }
        return findTarget(samples, now, player.level().dimension(), false);
    }

    /** The last recorded state at or before the full fifteen-second cutoff. */
    private static Sample findTarget(Deque<Sample> samples, long gameTime, ResourceKey<Level> dimension, boolean allowStale) {
        if (samples == null || samples.isEmpty()
                || !allowStale && samples.getLast().gameTime < gameTime - SAMPLE_INTERVAL_TICKS) {
            return null;
        }

        long cutoff = gameTime - REWIND_TICKS;
        var newestFirst = samples.descendingIterator();
        while (newestFirst.hasNext()) {
            Sample sample = newestFirst.next();
            if (sample.gameTime <= cutoff) {
                // Never substitute a shorter rewind when history is incomplete,
                // or reuse a stale state from another dimension or session.
                return sample.gameTime >= cutoff - SAMPLE_INTERVAL_TICKS
                        && (dimension == null || sample.dimension.equals(dimension)) ? sample : null;
            }
        }
        return null;
    }

    private static boolean hasAwakenedTimeManipulation(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey()
                        .equals(ModAbilities.TIME_MANIPULATION_ID)
                        && entry.getValue().isUnlocked())
                .isPresent();
    }

    private record Sample(
            long gameTime,
            ResourceKey<Level> dimension,
            Vec3 position,
            float yaw,
            float pitch,
            float health,
            boolean onFire,
            long blockRevision,
            TimeRewindEntities.Frame entities,
            TimeRewindContainers.Frame containers
    ) {
    }
}
