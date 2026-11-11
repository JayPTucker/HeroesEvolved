package com.jayptucker.heroesevolved.time;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every player's recent states (once a second), the path they took (every other tick, across worlds), and
 * their recent deaths with what each one dropped, so a rewind can undo a death and send each player back
 * through their own past (see {@link RewindTravel}). Kept only while someone who can rewind is recording (see
 * {@link TimeRewindBlocks}), and only as long as the longest rewind reaches back.
 */
public final class TimeRewindPlayers {
    private static final int SAMPLE_TICKS = 20, STEP_TICKS = 2, KEEP_TICKS = TimeRewindBlocks.LONGEST_RETENTION_TICKS + 40;

    private record Snapshot(long time, ResourceKey<Level> dimension, CompoundTag tag) {}

    /** Where a player was at a moment: the world, the spot, which way they looked. */
    public record Point(long time, ResourceKey<Level> dimension, Vec3 at, float yaw, float pitch) {}

    /** A death: whose, when, in which world and where, and what it dropped (items and experience). */
    private static final class Death {
        final UUID player; final long time; final ResourceKey<Level> dimension; final Vec3 at;
        final Set<UUID> drops = new HashSet<>();
        Death(UUID player, long time, ResourceKey<Level> dimension, Vec3 at) {
            this.player = player; this.time = time; this.dimension = dimension; this.at = at;
        }
    }

    private static final Map<UUID, Deque<Snapshot>> HISTORY = new HashMap<>();
    private static final Map<UUID, Deque<Point>> PATHS = new HashMap<>();
    private static final List<Death> DEATHS = new ArrayList<>();

    private TimeRewindPlayers() {}

    /** Once a second while anyone is recording: every living player's state. */
    public static void tick(MinecraftServer server, long now) {
        boolean recording = TimeRewindBlocks.recording();
        if (recording && now % SAMPLE_TICKS == 0) for (ServerPlayer player : server.getPlayerList().getPlayers()) record(player, now);
        if (recording && now % STEP_TICKS == 0) for (ServerPlayer player : server.getPlayerList().getPlayers()) step(player, now);
        HISTORY.values().forEach(samples -> { while (!samples.isEmpty() && samples.getFirst().time < now - KEEP_TICKS) samples.removeFirst(); });
        HISTORY.values().removeIf(Deque::isEmpty);
        PATHS.values().forEach(path -> { while (!path.isEmpty() && path.getFirst().time < now - KEEP_TICKS) path.removeFirst(); });
        PATHS.values().removeIf(Deque::isEmpty);
        DEATHS.removeIf(death -> death.time < now - KEEP_TICKS);
    }

    /** Records this player's state as of {@code now} (living players only: a body is not a state to return to). */
    public static void record(ServerPlayer player, long now) {
        if (!player.isAlive()) return;
        Deque<Snapshot> samples = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (!samples.isEmpty() && samples.getLast().time >= now) samples.removeLast();
        samples.addLast(new Snapshot(now, player.level().dimension(), TimeRewindEntities.tag(player)));
    }

    /** Where this player is at {@code now}, for the path a rewind sends them back along. Travelling back is not recorded. */
    public static void step(ServerPlayer player, long now) {
        if (!player.isAlive() || RewindTravel.travelling(player)) return;
        Deque<Point> path = PATHS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (!path.isEmpty() && path.getLast().time >= now) path.removeLast();
        path.addLast(new Point(now, player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
    }

    /** The path this player took since {@code cutoff}, newest first, ending at the last point at or before it. */
    public static List<Point> path(UUID player, long cutoff) {
        List<Point> result = new ArrayList<>();
        Deque<Point> path = PATHS.get(player);
        if (path == null) return result;
        for (Iterator<Point> it = path.descendingIterator(); it.hasNext(); ) {
            Point point = it.next();
            result.add(point);
            if (point.time <= cutoff) break;
        }
        return result;
    }

    /** A player died: remember when and where, so the death can be undone. */
    public static void died(ServerPlayer player, long now) {
        if (!TimeRewindBlocks.recording()) return;
        DEATHS.add(new Death(player.getUUID(), now, player.level().dimension(), player.position()));
    }

    /** What a dying player's body drops. */
    public static void dropped(ServerPlayer player, Collection<ItemEntity> drops) {
        Death death = latest(player.getUUID());
        if (death != null) drops.forEach(item -> death.drops.add(item.getUUID()));
    }

    /** Experience a death spills is spawned on its own, a moment later, where the body fell. */
    static void born(Entity entity, long now) {
        if (!(entity instanceof ExperienceOrb) && !(entity instanceof ItemEntity) || DEATHS.isEmpty()) return;
        for (int i = DEATHS.size() - 1; i >= 0; i--) {
            Death death = DEATHS.get(i);
            if (now - death.time > 2) break;
            if (death.dimension.equals(entity.level().dimension()) && death.at.distanceToSqr(entity.position()) < 4) {
                death.drops.add(entity.getUUID());
                return;
            }
        }
    }

    private static Death latest(UUID player) {
        for (int i = DEATHS.size() - 1; i >= 0; i--) if (DEATHS.get(i).player.equals(player)) return DEATHS.get(i);
        return null;
    }

    /** The state recorded at or just before {@code time} (within two samples), or null. */
    private static Snapshot at(UUID player, long time) {
        Deque<Snapshot> samples = HISTORY.get(player);
        if (samples == null) return null;
        for (Iterator<Snapshot> it = samples.descendingIterator(); it.hasNext(); ) {
            Snapshot snapshot = it.next();
            if (snapshot.time <= time) return snapshot.time >= time - SAMPLE_TICKS * 2 ? snapshot : null;
        }
        return null;
    }

    /**
     * Undoes every player death since {@code cutoff} in the caster's world that the rewind reaches: the caster's
     * own, and any within the recording radius of {@code center} (where the rewind returns to), of the caster
     * now, or of anywhere the caster has been recently. Each player comes back as they were at {@code cutoff}
     * (players the rewind already put back keep that), alive, and what their death dropped is taken back. Offline
     * players are put back when they next log in. Returns how many deaths were undone.
     */
    public static int undoDeaths(ServerPlayer caster, long cutoff, ResourceKey<Level> home, Vec3 center, Set<UUID> alreadyRestored) {
        return undoDeaths(caster, cutoff, home, center, alreadyRestored, TimeRewindEntities.AT_ONCE);
    }

    /** As {@link #undoDeaths(ServerPlayer, long, ResourceKey, Vec3, Set)}, putting players back through {@code landing}. */
    public static int undoDeaths(ServerPlayer caster, long cutoff, ResourceKey<Level> home, Vec3 center, Set<UUID> alreadyRestored,
                                 TimeRewindEntities.Landing landing) {
        ServerLevel level = caster.serverLevel();
        MinecraftServer server = level.getServer();
        double r2 = TimeRewindBlocks.RECORDING_RADIUS * TimeRewindBlocks.RECORDING_RADIUS;
        Set<UUID> revived = new HashSet<>();
        int undone = 0;
        for (Iterator<Death> it = DEATHS.iterator(); it.hasNext(); ) {
            Death death = it.next();
            if (death.time <= cutoff) continue;
            boolean reached = death.player.equals(caster.getUUID())
                    || death.dimension.equals(home) && death.at.distanceToSqr(center) <= r2
                    || death.dimension.equals(caster.level().dimension()) && death.at.distanceToSqr(caster.position()) <= r2
                    || TimeRewindBlocks.covers(caster.getUUID(), death.dimension, death.at);
            if (!reached) continue;
            Snapshot before = at(death.player, cutoff);
            if (!alreadyRestored.contains(death.player) && before == null) continue;
            it.remove();
            undone++;
            for (UUID drop : death.drops) TimeRewindEntities.unmake(level, drop);
            if (!revived.add(death.player) || alreadyRestored.contains(death.player)) {
                revive(server, death.player);
                continue;
            }
            ServerLevel world = server.getLevel(before.dimension);
            if (world == null) continue;
            // Wherever they are now (any world), or offline.
            ServerPlayer player = TimeRewindEntities.find(level, death.player) instanceof ServerPlayer found ? found : null;
            if (player == null) {
                PendingPlayerRewinds.get(server).put(death.player, before.dimension.location(), before.tag);
                continue;
            }
            if (!player.isAlive()) player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            revive(server, death.player);
            landing.land(player, world, before.tag);
        }
        return undone;
    }

    /** A hardcore death leaves its player watching as a spectator; once the death is undone they play again. */
    private static void revive(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null && server.isHardcore() && player.isSpectator()) player.setGameMode(GameType.SURVIVAL);
    }

    public static void forget(UUID player) {
        HISTORY.remove(player);
        PATHS.remove(player);
    }

    public static void clear() {
        HISTORY.clear();
        PATHS.clear();
        DEATHS.clear();
    }
}
