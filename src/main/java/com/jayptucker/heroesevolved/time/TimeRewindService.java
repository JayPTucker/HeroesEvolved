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
 */
public final class TimeRewindService {
    private static final int SAMPLE_INTERVAL_TICKS = 5;
    public static final int REWIND_SECONDS = 15;
    private static final int REWIND_TICKS = 20 * REWIND_SECONDS;
    private static final Map<UUID, Deque<Sample>> HISTORY = new HashMap<>();

    private TimeRewindService() {
    }

    /** Records recent states for every awakened Time Manipulation user. */
    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        if (gameTime % SAMPLE_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            record(player, gameTime);
        }
        TimeRewindBlocks.prune(gameTime);
    }

    static void record(ServerPlayer player, long gameTime) {
        if (!player.isAlive() || !hasAwakenedTimeManipulation(player)) {
            forget(player);
            return;
        }
        Deque<Sample> samples = HISTORY.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        long blockRevision = TimeRewindBlocks.track(player);
        samples.addLast(new Sample(gameTime, player.level().dimension(), player.position(),
                player.getYRot(), player.getXRot(), player.getHealth(), player.isOnFire(), blockRevision));
        while (!samples.isEmpty() && samples.getFirst().gameTime < gameTime - REWIND_TICKS - 20) {
            samples.removeFirst();
        }
    }

    public static boolean canRewind(ServerPlayer player) {
        Sample target = target(player);
        return target != null && TimeRewindBlocks.plan(player, target.blockRevision) != null;
    }

    public static boolean rewind(ServerPlayer player) {
        Sample target = target(player);
        if (target == null) {
            player.displayClientMessage(Component.literal(
                    "Record 15 seconds in this dimension before rewinding."), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
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

        TimeRewindBlocks.restore(player, blocks);
        player.teleportTo(level, target.position.x, target.position.y,
                target.position.z, target.yaw, target.pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.resetFallDistance();
        if (target.health > player.getHealth()) {
            player.setHealth(Math.min(player.getMaxHealth(), target.health));
        }
        if (!target.onFire) {
            player.clearFire();
        }

        Vec3 to = player.position();
        AbilityEffects.send(level, Kind.TEMPORAL_FIELD, to, to.add(0.0D, 1.0D, 0.0D), 2.5D);
        level.playSound(null, to.x, to.y, to.z, ModSounds.TIME_UNSLOW.get(),
                SoundSource.PLAYERS, 0.9F, 0.8F);
        ScreenTintPayload.send(player, 0x60C0A060, 10, ScreenTintPayload.Style.FLASH);
        player.displayClientMessage(Component.literal(
                "Time folds back 15 seconds.").withStyle(ChatFormatting.GOLD), true);

        // The rewound seconds no longer happened; you cannot rewind into them.
        samples.clear();
        TimeRewindBlocks.forget(player.getUUID());
        MasteryService.markPowerUse(player, ModAbilities.TIME_MANIPULATION_ID);
        return true;
    }

    public static void forget(ServerPlayer player) {
        HISTORY.remove(player.getUUID());
        TimeRewindBlocks.forget(player.getUUID());
    }

    public static void clear() {
        HISTORY.clear();
        TimeRewindBlocks.clear();
    }

    /** Use the same clock for recording and selecting history, in every dimension. */
    private static Sample target(ServerPlayer player) {
        return findTarget(HISTORY.get(player.getUUID()),
                player.serverLevel().getServer().overworld().getGameTime(),
                player.level().dimension());
    }

    /** The last recorded state at or before the full fifteen-second cutoff. */
    private static Sample findTarget(Deque<Sample> samples, long gameTime, ResourceKey<Level> dimension) {
        if (samples == null || samples.isEmpty()
                || samples.getLast().gameTime < gameTime - SAMPLE_INTERVAL_TICKS) {
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
                        && sample.dimension.equals(dimension) ? sample : null;
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
            long blockRevision
    ) {
    }
}
