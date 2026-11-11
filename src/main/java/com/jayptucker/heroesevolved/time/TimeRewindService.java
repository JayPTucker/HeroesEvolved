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
        RewindTravel.tick(server);
        TimeRewindPlayers.tick(server, gameTime);
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
            // Only drop what Rewind kept: the shared journals may be a speedster's (see SpeedOverrun).
            if (HISTORY.containsKey(player.getUUID()) || DEATHS.containsKey(player.getUUID())) forget(player);
            return;
        }
        // A dead body records nothing, but its history is kept: the death can still be rewound.
        if (!player.isAlive()) return;
        Deque<Sample> samples = HISTORY.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        long blockRevision = TimeRewindBlocks.track(player);
        TimeRewindBlocks.anchor(player, gameTime);
        samples.addLast(new Sample(gameTime, player.level().dimension(), player.position(),
                player.getYRot(), player.getXRot(), player.getHealth(), player.isOnFire(), blockRevision,
                TimeRewindEntities.capture(player), TimeRewindContainers.capture(player)));
        while (!samples.isEmpty() && samples.getFirst().gameTime < gameTime - REWIND_TICKS - 20) {
            samples.removeFirst();
        }
    }

    public static boolean canRewind(ServerPlayer player) {
        Sample target = target(player);
        return target != null && target.entities.complete() && target.containers.complete()
                && TimeRewindBlocks.plan(player, target.blockRevision, target.dimension) != null;
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
        // Only a time user's own history ends with an unrewindable death; the shared journals may be a speedster's.
        if (death == null) { if (HISTORY.containsKey(player.getUUID())) forget(player); return; }
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
        if (target == null) {
            player.displayClientMessage(Component.literal("Record 15 seconds before rewinding."), true);
            return false;
        }
        boolean undoingDeath = acrossDeath(player, target);
        // History crosses worlds: fifteen seconds ago may have been somewhere else.
        ServerLevel home = player.getServer().getLevel(target.dimension);
        if (home == null || !target.entities.complete() || !target.containers.complete()) {
            player.displayClientMessage(Component.literal("There were too many entities to record a complete rewind. Wait for a complete history."), true);
            return false;
        }
        TimeRewindBlocks.Plan blocks = TimeRewindBlocks.plan(player, target.blockRevision, target.dimension);
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

        TimeStopService.stop(player);
        TimeSlowService.stop(player);
        SpaceTimeService.forget(player);
        // The world snaps back at once; everyone it reaches is carried back through their own past (RewindTravel)
        // and lands in it as they were. The one rewinding lands exactly where they stood.
        UUID caster = player.getUUID();
        Sample landingAt = target;
        TimeRewindEntities.Landing landing = RewindTravel.landing(target.gameTime, RewindTravel.MOMENT_TICKS, current -> {
            if (!current.getUUID().equals(caster)) return;
            ServerLevel level = current.getServer().getLevel(landingAt.dimension);
            if (level != null) current.teleportTo(level, landingAt.position.x, landingAt.position.y, landingAt.position.z, landingAt.yaw, landingAt.pitch);
            current.resetFallDistance();
            if (landingAt.health > current.getHealth()) current.setHealth(Math.min(current.getMaxHealth(), landingAt.health));
            if (!landingAt.onFire) current.clearFire();
            // Coming back from the dead: a hardcore respawn would otherwise leave them spectating.
            if (undoingDeath && current.getServer().isHardcore() && current.isSpectator()) current.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            current.displayClientMessage(Component.literal(undoingDeath
                    ? "Time folds back 15 seconds. Your death never happened." : "Time folds back 15 seconds.").withStyle(ChatFormatting.GOLD), true);
        });
        TimeRewindBlocks.restore(player, blocks);
        TimeRewindContainers.restore(player, home, target.containers);
        java.util.Set<UUID> restored = TimeRewindEntities.restore(player, home, target.entities, target.gameTime, landing);
        // Anyone else who died in reach since then lives again (the rewind's own frame may not have held them).
        TimeRewindPlayers.undoDeaths(player, target.gameTime, target.dimension, target.position, restored, landing);
        if (undoingDeath) {
            DEATHS.remove(player.getUUID());
            com.jayptucker.heroesevolved.progression.EvolutionTrials.advance(player, ModAbilities.TIME_MANIPULATION_ID);
        }

        // The rewound seconds no longer happened; you cannot rewind into them.
        if (samples != null) samples.clear();
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
        RewindTravel.clear();
        TimeRewindPlayers.clear();
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
        // Fifteen seconds ago may have been in another world: the rewind takes you back across.
        return findTarget(samples, now, null, false);
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
